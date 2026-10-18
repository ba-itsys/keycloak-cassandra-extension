/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession;

import static de.arbeitsagentur.opdt.keycloak.common.ExpirationUtils.isExpired;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.utils.SessionExpiration.getAuthSessionLifespan;

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.util.Time;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;

@JBossLog
@RequiredArgsConstructor
public class CassandraAuthSessionProvider implements AuthenticationSessionProvider {
    private final KeycloakSession session;
    private final AuthSessionRepository authSessionRepository;
    private final int authSessionsLimit;

    private Map<String, CassandraRootAuthSessionAdapter> sessionModels = new HashMap<>();

    private Function<RootAuthenticationSession, RootAuthenticationSessionModel> entityToAdapterFunc(RealmModel realm) {
        return origEntity -> {
            if (origEntity == null) {
                return null;
            }

            if (isExpired(origEntity, true)) {
                authSessionRepository.deleteRootAuthSession(origEntity);
                sessionModels.remove(origEntity.getId());
                return null;
            } else {
                if (sessionModels.containsKey(origEntity.getId())) {
                    return sessionModels.get(origEntity.getId());
                }

                CassandraRootAuthSessionAdapter adapter = new CassandraRootAuthSessionAdapter(
                        session, realm, origEntity, authSessionRepository, authSessionsLimit);
                session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) adapter::flush);
                sessionModels.put(origEntity.getId(), adapter);
                return adapter;
            }
        };
    }

    private Predicate<RootAuthenticationSession> entityRealmFilter(String realmId) {
        if (realmId == null) {
            return c -> false;
        }
        return entity -> Objects.equals(realmId, entity.getRealmId());
    }

    @Override
    public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm) {
        Objects.requireNonNull(realm, "The provided realm can't be null!");
        return createRootAuthenticationSession(realm, null);
    }

    @Override
    public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm, String id) {
        Objects.requireNonNull(realm, "The provided realm can't be null!");

        log.tracef("createRootAuthenticationSession(%s)%s", realm.getName(), getShortStackTrace());

        long timestamp = Time.currentTimeMillis();
        int authSessionLifespanSeconds = getAuthSessionLifespan(realm);
        RootAuthenticationSession entity = RootAuthenticationSession.builder()
                .id(id == null ? KeycloakModelUtils.generateId() : id)
                .realmId(realm.getId())
                .timestamp(timestamp)
                .expiration(timestamp + TimeAdapter.fromSecondsToMilliseconds(authSessionLifespanSeconds))
                .build();

        if (id != null && authSessionRepository.findRootAuthSessionById(id) != null) {
            throw new ModelDuplicateException("Root authentication session exists: " + entity.getId());
        }

        authSessionRepository.insertOrUpdate(entity);

        return entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public RootAuthenticationSessionModel getRootAuthenticationSession(
            RealmModel realm, String authenticationSessionId) {
        Objects.requireNonNull(realm, "The provided realm can't be null!");
        if (authenticationSessionId == null) {
            return null;
        }

        log.tracef(
                "getRootAuthenticationSession(%s, %s)%s",
                realm.getName(), authenticationSessionId, getShortStackTrace());

        RootAuthenticationSession entity = authSessionRepository.findRootAuthSessionById(authenticationSessionId);
        return (entity == null || !entityRealmFilter(realm.getId()).test(entity))
                ? null
                : entityToAdapterFunc(realm).apply(entity);
    }

    @Override
    public void removeRootAuthenticationSession(
            RealmModel realm, RootAuthenticationSessionModel authenticationSession) {
        Objects.requireNonNull(authenticationSession, "The provided root authentication session can't be null!");
        authSessionRepository.deleteRootAuthSession(authenticationSession.getId());
        sessionModels.remove(authenticationSession.getId());
        ((CassandraRootAuthSessionAdapter) authenticationSession).markDeleted();
    }

    @Override
    public void removeAllExpired() {
        log.tracef("removeAllExpired()%s", getShortStackTrace());
        log.warnf(
                "Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
    }

    @Override
    public void removeExpired(RealmModel realm) {
        log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
        log.warnf(
                "Clearing expired entities should not be triggered manually. It is responsibility of the store to clear these.");
    }

    @Override
    public void removeRootAuthenticationSessionsByAuthenticatedUser(
            RealmModel realm, UserModel user, String rootAuthenticationSessionIdToKeep) {
        Objects.requireNonNull(realm, "The provided realm can't be null!");
        Objects.requireNonNull(user, "The provided user can't be null!");
        log.tracef(
                "removeRootAuthenticationSessionsByAuthenticatedUser(%s, %s, %s)%s",
                realm.getName(), user.getId(), rootAuthenticationSessionIdToKeep, getShortStackTrace());

        for (String rootSessionId : authSessionRepository.findRootAuthSessionIdsByUser(realm.getId(), user.getId())) {
            if (Objects.equals(rootSessionId, rootAuthenticationSessionIdToKeep)) {
                continue;
            }
            // The index may be stale when the authenticated user of the session changed
            boolean authenticatedByUser =
                    authSessionRepository.findAuthSessionsByParentSessionId(rootSessionId).stream()
                            .anyMatch(authSession -> Objects.equals(authSession.getUserId(), user.getId()));
            if (authenticatedByUser) {
                authSessionRepository.deleteRootAuthSession(rootSessionId);
                CassandraRootAuthSessionAdapter loaded = sessionModels.remove(rootSessionId);
                if (loaded != null) {
                    loaded.markDeleted();
                }
            }
            authSessionRepository.deleteRootAuthSessionUserIndex(realm.getId(), user.getId(), rootSessionId);
        }
    }

    @Override
    public void onRealmRemoved(RealmModel realm) {
        // Just let them expire...
    }

    @Override
    public void onClientRemoved(RealmModel realm, ClientModel client) {
        // Just let them expire...
    }

    @Override
    public void updateNonlocalSessionAuthNotes(
            AuthenticationSessionCompoundId compoundId, Map<String, String> authNotesFragment) {
        if (compoundId == null) {
            return;
        }
        Objects.requireNonNull(authNotesFragment, "The provided authentication's notes map can't be null!");
        AuthenticationSession authenticationSession =
                authSessionRepository.findAuthSessionsByParentSessionId(compoundId.getRootSessionId()).stream()
                        .filter(s -> Objects.equals(s.getTabId(), compoundId.getTabId()))
                        .filter(s -> Objects.equals(s.getClientId(), compoundId.getClientUUID()))
                        .findFirst()
                        .orElse(null);

        if (authenticationSession != null) {
            authenticationSession.setAuthNotes(authNotesFragment);
        }
    }

    @Override
    public void close() {
        // Nothing to do
    }
}
