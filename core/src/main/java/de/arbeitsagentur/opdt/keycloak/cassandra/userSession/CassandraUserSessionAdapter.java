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
package de.arbeitsagentur.opdt.keycloak.cassandra.userSession;

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration.CassandraSessionExpiration.setUserSessionExpiration;
import static de.arbeitsagentur.opdt.keycloak.common.ExpirationUtils.isExpired;
import static org.keycloak.models.Constants.SESSION_NOTE_LIGHTWEIGHT_USER;

import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration.SessionExpirationData;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.Profile;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.light.LightweightUserAdapter;

@JBossLog
@EqualsAndHashCode(of = "userSessionEntity")
public class CassandraUserSessionAdapter implements UserSessionModel {
    public static final String SESSION_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "maxLifespanOverride";
    public static final String SESSION_OFFLINE_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "offlineMaxLifespanOverride";
    public static final String SESSION_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "idleTimeoutOverride";
    public static final String SESSION_OFFLINE_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "offlineIdleTimeoutOverride";
    public static final String CLIENT_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "clientMaxLifespanOverride";
    public static final String CLIENT_OFFLINE_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "clientOfflineMaxLifespanOverride";
    public static final String CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "clientIdleTimeoutOverride";
    public static final String CLIENT_OFFLINE_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE =
            AttributeTypes.INTERNAL_ATTRIBUTE_PREFIX + "clientOfflineIdleTimeoutOverride";

    private static final Set<String> SESSION_EXPIRATION_ATTRIBUTES = Set.of(
            SESSION_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE,
            SESSION_OFFLINE_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE,
            SESSION_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE,
            SESSION_OFFLINE_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE,
            CLIENT_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE,
            CLIENT_OFFLINE_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE,
            CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE,
            CLIENT_OFFLINE_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final UserSession userSessionEntity;
    private final UserSessionRepository userSessionRepository;

    private boolean updated = false;
    private boolean deleted = false;

    public CassandraUserSessionAdapter(
            KeycloakSession session,
            RealmModel realm,
            UserSession userSessionEntity,
            UserSessionRepository userSessionRepository) {
        this.session = session;
        this.realm = realm;
        this.userSessionEntity = userSessionEntity;
        this.userSessionRepository = userSessionRepository;
    }

    public UserSession getUserSessionEntity() {
        return userSessionEntity;
    }

    // Updates in AuthenticatedClientSession
    public void markAsUpdated() {
        updated = true;
    }

    public void markAsDeleted() {
        deleted = true;
    }

    @Override
    public String getId() {
        return userSessionEntity.getId();
    }

    @Override
    public RealmModel getRealm() {
        return realm;
    }

    @Override
    public String getBrokerSessionId() {
        return userSessionEntity.getBrokerSessionId();
    }

    @Override
    public String getBrokerUserId() {
        return userSessionEntity.getBrokerUserId();
    }

    @Override
    public UserModel getUser() {
        if (Profile.isFeatureEnabled(Profile.Feature.TRANSIENT_USERS)
                && userSessionEntity.getNotes().containsKey(SESSION_NOTE_LIGHTWEIGHT_USER)) {
            LightweightUserAdapter lua = LightweightUserAdapter.fromString(
                    session, realm, userSessionEntity.getNotes().get(SESSION_NOTE_LIGHTWEIGHT_USER));
            lua.setUpdateHandler(lua1 -> {
                if (lua == lua1) { // Ensure there is no conflicting user model, only the latest lightweight
                    // user can be used
                    setNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua1.serialize());
                }
            });

            return lua;
        }

        return session.users().getUserById(getRealm(), userSessionEntity.getUserId());
    }

    @Override
    public String getLoginUsername() {
        return userSessionEntity.getLoginUsername();
    }

    @Override
    public String getIpAddress() {
        return userSessionEntity.getIpAddress();
    }

    @Override
    public String getAuthMethod() {
        return userSessionEntity.getAuthMethod();
    }

    @Override
    public boolean isRememberMe() {
        Boolean rememberMe = userSessionEntity.getRememberMe();
        return rememberMe != null ? rememberMe : false;
    }

    @Override
    public int getStarted() {
        Long started = userSessionEntity.getTimestamp();
        return started != null
                ? TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                        TimeAdapter.fromMilliSecondsToSeconds(started))
                : 0;
    }

    @Override
    public int getLastSessionRefresh() {
        Long lastSessionRefresh = userSessionEntity.getLastSessionRefresh();
        return lastSessionRefresh != null
                ? TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                        TimeAdapter.fromMilliSecondsToSeconds(lastSessionRefresh))
                : 0;
    }

    @Override
    public void setLastSessionRefresh(int seconds) {
        userSessionEntity.setLastSessionRefresh(TimeAdapter.fromSecondsToMilliseconds(seconds));

        // whenever the lastSessionRefresh is changed recompute the expiration time
        setUserSessionExpiration(userSessionEntity, getSessionExpirationData());
        updated = true;
    }

    @Override
    public boolean isOffline() {
        Boolean offline = userSessionEntity.getOffline();
        return offline != null ? offline : false;
    }

    @Override
    public SessionPersistenceState getPersistenceState() {
        return userSessionEntity.getPersistenceState();
    }

    @Override
    public Map<String, AuthenticatedClientSessionModel> getAuthenticatedClientSessions() {
        List<AuthenticatedClientSessionValue> authenticatedClientSessions =
                new ArrayList<>(userSessionEntity.getClientSessions().values());

        return authenticatedClientSessions.stream()
                .filter(Objects::nonNull)
                .filter(this::filterAndRemoveExpiredClientSessions)
                .filter(this::matchingOfflineFlag)
                .filter(this::filterAndRemoveClientSessionWithoutClient)
                .collect(Collectors.toMap(
                        AuthenticatedClientSessionValue::getClientId, this::clientSessionEntityToModel));
    }

    @Override
    public void removeAuthenticatedClientSessions(Collection<String> removedClientUUIDS) {
        removedClientUUIDS.forEach(
                clientId -> userSessionEntity.getClientSessions().remove(clientId));
        updated = true;
    }

    @Override
    public String getNote(String name) {
        return userSessionEntity.getNotes().get(name);
    }

    @Override
    public void setNote(String name, String value) {
        if (value == null) {
            removeNote(name);
            return;
        }

        if (SESSION_EXPIRATION_ATTRIBUTES.contains(name)) {
            String oldOverride = userSessionEntity.getNotes().get(name);
            if (oldOverride == null || Long.parseLong(value) <= Long.parseLong(oldOverride)) {
                userSessionEntity.getNotes().put(name, value);
                restartExpirationWithLifespanOverride();
                updated = true;
            } else {
                log.warnf(
                        "Trying to override %s with new value of %s which is greater than the old override-value of %s. This is not allowed.",
                        name, value, oldOverride);
            }
        } else if (!userSessionEntity.getNotes().containsKey(name)
                || !userSessionEntity.getNotes().get(name).equals(value)) {
            userSessionEntity.getNotes().put(name, value);
            updated = true;
        }
    }

    private void restartExpirationWithLifespanOverride() {
        long timestamp = Time.currentTimeMillis();
        userSessionEntity.setTimestamp(timestamp);
        userSessionEntity.setLastSessionRefresh(timestamp);

        getAuthenticatedClientSessions()
                .values()
                .forEach(s -> s.setTimestamp(TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                        TimeAdapter.fromMilliSecondsToSeconds(timestamp))));

        updated = true;
    }

    @Override
    public void removeNote(String name) {
        if (userSessionEntity.getNotes().containsKey(name)) {
            userSessionEntity.getNotes().remove(name);
            updated = true;
        }
    }

    @Override
    public Map<String, String> getNotes() {
        return userSessionEntity.getNotes();
    }

    @Override
    public State getState() {
        return userSessionEntity.getState();
    }

    @Override
    public void setState(State state) {
        if (userSessionEntity.getState() == null
                || !userSessionEntity.getState().equals(state)) {
            userSessionEntity.setState(state);
            updated = true;
        }
    }

    @Override
    public void restartSession(
            RealmModel realm,
            UserModel user,
            String loginUsername,
            String ipAddress,
            String authMethod,
            boolean rememberMe,
            String brokerSessionId,
            String brokerUserId) {
        String correspondingSessionId = userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);

        userSessionEntity.setRealmId(realm.getId());
        userSessionEntity.setUserId(user.getId());
        userSessionEntity.setLoginUsername(loginUsername);
        userSessionEntity.setIpAddress(ipAddress);
        userSessionEntity.setAuthMethod(authMethod);
        userSessionEntity.setRememberMe(rememberMe);
        userSessionEntity.setBrokerSessionId(brokerSessionId);
        userSessionEntity.setBrokerUserId(brokerUserId);
        userSessionEntity.setTimestamp(Time.currentTimeMillis());
        userSessionEntity.setLastSessionRefresh(Time.currentTimeMillis());
        userSessionEntity.setState(null);
        userSessionEntity.setNotes(new HashMap<>());
        userSessionEntity.setClientSessions(new HashMap<>());

        if (correspondingSessionId != null) {
            userSessionEntity.getNotes().put(CORRESPONDING_SESSION_ID, correspondingSessionId);
        }

        updated = true;
    }

    public void flush() {
        if (updated && !deleted) {
            setUserSessionExpiration(userSessionEntity, getSessionExpirationData());
            userSessionRepository.update(userSessionEntity);
            updated = false;
        }
    }

    private boolean filterAndRemoveExpiredClientSessions(AuthenticatedClientSessionValue clientSession) {
        try {
            if (isExpired(clientSession, false)) {
                userSessionEntity.getClientSessions().remove(clientSession.getClientId());
                updated = true;
                return false;
            }
        } catch (ModelIllegalStateException ex) {
            userSessionEntity.getClientSessions().remove(clientSession.getClientId());
            updated = true;
            return false;
        }

        return true;
    }

    private boolean matchingOfflineFlag(AuthenticatedClientSessionValue clientSession) {
        Boolean isClientSessionOffline = clientSession.isOffline();

        // If client session doesn't have offline flag default to false
        if (isClientSessionOffline == null) return !isOffline();

        return isOffline() == isClientSessionOffline;
    }

    private boolean filterAndRemoveClientSessionWithoutClient(AuthenticatedClientSessionValue clientSession) {
        ClientModel client = realm.getClientById(clientSession.getClientId());

        if (client == null) {
            userSessionEntity.getClientSessions().remove(clientSession.getClientId());
            updated = true;

            // Filter out entities that doesn't have client
            return false;
        }

        // client session has client so we do not filter it out
        return true;
    }

    private AuthenticatedClientSessionModel clientSessionEntityToModel(
            AuthenticatedClientSessionValue clientSessionEntity) {
        return new CassandraAuthenticatedClientSessionAdapter(session, realm, this, clientSessionEntity) {
            @Override
            public void detachFromUserSession() {
                // TODO: what are the intended semantics of "detach"?
                userSessionEntity.getClientSessions().remove(clientSessionEntity.getClientId());
                updated = true;

                this.userSession = null;
            }
        };
    }

    public SessionExpirationData getSessionExpirationData() {
        Integer lifespanOverride = getOverride(SESSION_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE);
        Integer idleTimeoutOverride = getOverride(SESSION_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE);
        Integer offlineLifespanOverride = getOverride(SESSION_OFFLINE_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE);
        Integer offlineIdleTimeoutOverride = getOverride(SESSION_OFFLINE_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE);
        Integer clientLifespanOverride = getOverride(CLIENT_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE);
        Integer clientIdleTimeoutOverride = getOverride(CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE);
        Integer clientOfflineLifespanOverride = getOverride(CLIENT_OFFLINE_MAX_LIFESPAN_OVERRIDE_ATTRIBUTE);
        Integer clientOfflineIdleTimeoutOverride = getOverride(CLIENT_OFFLINE_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE);

        return SessionExpirationData.builder()
                .realm(realm)
                .maxLifespanOverride(lifespanOverride)
                .idleTimeoutOverride(idleTimeoutOverride)
                .offlineMaxLifespanOverride(offlineLifespanOverride)
                .offlineIdleTimeoutOverride(offlineIdleTimeoutOverride)
                .clientMaxLifespanOverride(clientLifespanOverride)
                .clientIdleTimeoutOverride(clientIdleTimeoutOverride)
                .offlineClientMaxLifespanOverride(clientOfflineLifespanOverride)
                .offlineClientIdleTimeoutOverride(clientOfflineIdleTimeoutOverride)
                .build();
    }

    private Integer getOverride(String sessionMaxLifespanOverrideAttribute) {
        String lifespanNote = getNote(sessionMaxLifespanOverrideAttribute);
        return lifespanNote == null ? null : Integer.parseInt(lifespanNote);
    }
}
