/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.realm;

import static de.arbeitsagentur.opdt.keycloak.common.ExpirationUtils.isExpired;
import static de.arbeitsagentur.opdt.keycloak.common.MapProviderObjectType.REALM_AFTER_REMOVE;
import static de.arbeitsagentur.opdt.keycloak.common.MapProviderObjectType.REALM_BEFORE_REMOVE;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;

import de.arbeitsagentur.opdt.keycloak.cassandra.CompositeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.utils.KeycloakModelUtils;

@JBossLog
public class CassandraRealmsProvider extends TransactionalProvider<Realm, CassandraRealmAdapter>
        implements RealmProvider {
    private final RealmRepository realmRepository;

    public CassandraRealmsProvider(KeycloakSession session, CompositeRepository cassandraRepository) {
        super(session);
        this.realmRepository = cassandraRepository;
    }

    @Override
    protected CassandraRealmAdapter createNewModel(RealmModel realm, Realm entity) {
        return createNewModel(entity, () -> {});
    }

    private CassandraRealmAdapter createNewModelWithRollback(RealmModel realm, Realm entity) {
        return createNewModel(entity, () -> {
            realmRepository.deleteRealm(entity);
            models.remove(entity.getId());
        });
    }

    private CassandraRealmAdapter createNewModel(Realm entity, Runnable rollbackAction) {
        return new CassandraRealmAdapter(entity, session, realmRepository) {
            @Override
            public void rollback() {
                rollbackAction.run();
            }
        };
    }

    @Override
    public RealmModel createRealm(String name) {
        return createRealm(KeycloakModelUtils.generateId(), name);
    }

    @Override
    public RealmModel createRealm(String id, String name) {
        if (getRealmByName(name) != null) {
            throw new ModelDuplicateException("Realm with given name exists: " + name);
        }

        Realm existingRealm = realmRepository.getRealmById(id);
        if (existingRealm != null) {
            throw new ModelDuplicateException("Realm exists: " + id);
        }

        log.tracef("createRealm(%s, %s)%s", id, name, getShortStackTrace());

        Realm realm = new Realm(id, name, null, new HashMap<>());
        realmRepository.createRealm(realm);
        RealmModel realmModel =
                entityToAdapterFunc(null, this::createNewModelWithRollback).apply(realm);
        realmModel.setName(name);

        return realmModel;
    }

    @Override
    public RealmModel getRealm(String id) {
        if (id == null) return null;

        log.tracef("getRealm(%s)%s", id, getShortStackTrace());

        Realm realm = realmRepository.getRealmById(id);
        RealmModel result = entityToAdapterFunc(null).apply(realm);

        if (result != null && log.isTraceEnabled()) {
            log.tracef(
                    "Loaded realm with id %s, version %s and execution models %s",
                    result.getId(),
                    result.getAttribute(CassandraRealmAdapter.ENTITY_VERSION),
                    result.getAttribute(CassandraRealmAdapter.AUTHENTICATION_EXECUTION_MODELS));
        }

        return result;
    }

    @Override
    public RealmModel getRealmByName(String name) {
        if (name == null) return null;

        log.tracef("getRealm(%s)%s", name, getShortStackTrace());

        Realm realm = realmRepository.findRealmByName(name);
        RealmModel result = entityToAdapterFunc(null).apply(realm);

        if (result != null && log.isTraceEnabled()) {
            log.tracef(
                    "Loaded realm with id %s, version %s and execution models %s",
                    result.getId(),
                    result.getAttribute(CassandraRealmAdapter.ENTITY_VERSION),
                    result.getAttribute(CassandraRealmAdapter.AUTHENTICATION_EXECUTION_MODELS));
        }

        return result;
    }

    @Override
    public Stream<RealmModel> getRealmsStream() {
        return realmRepository.getAllRealms().stream().map(entityToAdapterFunc(null));
    }

    @Override
    public Stream<RealmModel> getRealmsWithProviderTypeStream(Class<?> type) {
        return getRealmsStream()
                .filter(r -> r.getComponentsStream()
                        .anyMatch(c -> c.getProviderType().equals(type.getName())));
    }

    @Override
    public boolean removeRealm(String id) {
        log.tracef("removeRealm(%s)%s", id, getShortStackTrace());
        Realm realm = realmRepository.getRealmById(id);

        if (realm == null) return false;

        RealmModel realmModel = getRealm(id);
        session.invalidate(REALM_BEFORE_REMOVE, realmModel);
        realmRepository.deleteRealm(realm);
        ((CassandraRealmAdapter) realmModel).markDeleted();
        models.remove(id);
        session.invalidate(REALM_AFTER_REMOVE, realmModel);

        return true;
    }

    @Override
    public void removeExpiredClientInitialAccess() {
        List<ClientInitialAccess> cias = realmRepository.getAllClientInitialAccesses();
        if (cias != null)
            cias.stream()
                    .filter(this::checkIfExpired)
                    .collect(Collectors.toSet())
                    .forEach(realmRepository::deleteClientInitialAccess);
    }

    private boolean checkIfExpired(ClientInitialAccess cia) {
        return cia.getRemainingCount() < 1 || isExpired(cia, true);
    }

    @Override
    public void saveLocalizationText(RealmModel realm, String locale, String key, String text) {
        if (locale == null || key == null || text == null) return;

        Map<String, String> current = realm.getRealmLocalizationTextsByLocale(locale);

        if (current == null) {
            current = new HashMap<>();
        }

        current.put(key, text);
        realm.createOrUpdateRealmLocalizationTexts(locale, current);
    }

    @Override
    public void saveLocalizationTexts(RealmModel realm, String locale, Map<String, String> textMap) {
        if (locale == null || textMap == null) return;

        realm.createOrUpdateRealmLocalizationTexts(locale, textMap);
    }

    @Override
    public boolean updateLocalizationText(RealmModel realm, String locale, String key, String text) {
        if (locale == null
                || key == null
                || text == null
                || (!realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return false;

        Map<String, String> realmLocalizationTextsByLocale = realm.getRealmLocalizationTextsByLocale(locale);
        if (realmLocalizationTextsByLocale == null || !realmLocalizationTextsByLocale.containsKey(key)) {
            return false;
        }

        realmLocalizationTextsByLocale.put(key, text);
        realm.createOrUpdateRealmLocalizationTexts(locale, realmLocalizationTextsByLocale);
        return true;
    }

    @Override
    public boolean deleteLocalizationTextsByLocale(RealmModel realm, String locale) {
        return realm.removeRealmLocalizationTexts(locale);
    }

    @Override
    public boolean deleteLocalizationText(RealmModel realm, String locale, String key) {
        if (locale == null
                || key == null
                || (!realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return false;

        Map<String, String> realmLocalizationTextsByLocale = realm.getRealmLocalizationTextsByLocale(locale);
        if (realmLocalizationTextsByLocale == null || !realmLocalizationTextsByLocale.containsKey(key)) {
            return false;
        }

        realmLocalizationTextsByLocale.remove(key);
        realm.createOrUpdateRealmLocalizationTexts(locale, realmLocalizationTextsByLocale);
        return true;
    }

    @Override
    public String getLocalizationTextsById(RealmModel realm, String locale, String key) {
        if (locale == null
                || key == null
                || (!realm.getRealmLocalizationTextsByLocale(locale).containsKey(key))) return null;
        return realm.getRealmLocalizationTextsByLocale(locale).get(key);
    }
}
