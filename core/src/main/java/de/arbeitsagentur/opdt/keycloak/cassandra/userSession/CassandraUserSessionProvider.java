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

import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration.CassandraSessionExpiration.setClientSessionExpiration;
import static de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration.CassandraSessionExpiration.setUserSessionExpiration;
import static de.arbeitsagentur.opdt.keycloak.common.ExpirationUtils.isExpired;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;
import static org.keycloak.models.Constants.SESSION_NOTE_LIGHTWEIGHT_USER;
import static org.keycloak.models.UserSessionModel.CORRESPONDING_SESSION_ID;
import static org.keycloak.models.UserSessionModel.SessionPersistenceState.TRANSIENT;

import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.CassandraModelTransaction;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.expiration.SessionExpirationData;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.common.Profile;
import org.keycloak.common.util.Time;
import org.keycloak.device.DeviceActivityManager;
import org.keycloak.models.*;
import org.keycloak.models.light.LightweightUserAdapter;
import org.keycloak.models.utils.KeycloakModelUtils;

@JBossLog
@RequiredArgsConstructor
public class CassandraUserSessionProvider implements UserSessionProvider {
    private final KeycloakSession session;
    private final UserSessionRepository userSessionRepository;

    private final Map<String, UserSession> transientUserSessions = new HashMap<>();
    private final Map<String, CassandraUserSessionAdapter> sessionModels = new HashMap<>();

    private Function<UserSession, CassandraUserSessionAdapter> entityToAdapterFunc(RealmModel realm) {
        // Clone entity before returning back, to avoid giving away a reference to the live object to
        // the caller
        return (origEntity) -> {
            if (origEntity == null) {
                return null;
            }

            if (isExpired(origEntity, false)) {
                if (TRANSIENT == origEntity.getPersistenceState()) {
                    transientUserSessions.remove(origEntity.getId());
                } else {
                    userSessionRepository.deleteUserSession(origEntity);
                }

                CassandraUserSessionAdapter model = sessionModels.get(origEntity.getId());
                if (model != null) {
                    model.markAsDeleted();
                }

                sessionModels.remove(origEntity.getId());
                return null;
            } else {
                if (sessionModels.containsKey(origEntity.getId())) {
                    return sessionModels.get(origEntity.getId());
                }

                CassandraUserSessionAdapter cassandraUserSessionAdapter =
                        new CassandraUserSessionAdapter(session, realm, origEntity, userSessionRepository);

                if (Profile.isFeatureEnabled(Profile.Feature.TRANSIENT_USERS)
                        && origEntity.getNotes().containsKey(SESSION_NOTE_LIGHTWEIGHT_USER)) {
                    LightweightUserAdapter lua = LightweightUserAdapter.fromString(
                            session, realm, origEntity.getNotes().get(SESSION_NOTE_LIGHTWEIGHT_USER));
                    lua.setUpdateHandler(lua1 -> {
                        if (lua == lua1) { // Ensure there is no conflicting user model, only the latest
                            // lightweight user can be used
                            cassandraUserSessionAdapter.setNote(SESSION_NOTE_LIGHTWEIGHT_USER, lua1.serialize());
                        }
                    });
                    cassandraUserSessionAdapter.bindUser(lua);
                }

                session.getTransactionManager().enlistAfterCompletion((CassandraModelTransaction) () -> {
                    cassandraUserSessionAdapter.flush();
                    sessionModels.remove(cassandraUserSessionAdapter.getId());
                });
                sessionModels.put(cassandraUserSessionAdapter.getId(), cassandraUserSessionAdapter);

                return cassandraUserSessionAdapter;
            }
        };
    }

    @Override
    public KeycloakSession getKeycloakSession() {
        return session;
    }

    @Override
    public AuthenticatedClientSessionModel createClientSession(
            RealmModel realm, ClientModel client, UserSessionModel userSession) {
        log.tracef("createClientSession(%s, %s, %s)%s", realm, client, userSession, getShortStackTrace());

        if (userSession == null) {
            throw new IllegalStateException("User session is null.");
        }

        UserSession userSessionEntity = ((CassandraUserSessionAdapter) userSession).getUserSessionEntity();

        if (userSessionEntity == null) {
            throw new IllegalStateException("User session entity does not exist: " + userSession.getId());
        }

        AuthenticatedClientSessionValue entity =
                createAuthenticatedClientSessionEntityInstance(null, client.getId(), false);
        String started = entity.getTimestamp() != null
                ? String.valueOf(TimeAdapter.fromMilliSecondsToSeconds(entity.getTimestamp()))
                : String.valueOf(0);
        entity.getNotes().put(AuthenticatedClientSessionModel.STARTED_AT_NOTE, started);
        setClientSessionExpiration(
                entity, SessionExpirationData.builder().realm(realm).build(), client);

        userSessionRepository.addClientSession(realm, userSessionEntity, entity);

        return userSession.getAuthenticatedClientSessionByClient(client.getId());
    }

    @Override
    public AuthenticatedClientSessionModel getClientSession(
            UserSessionModel userSession, ClientModel client, boolean offline) {
        log.tracef("getClientSession(%s, %s, %s)%s", userSession, client, offline, getShortStackTrace());

        return getClientSession(userSession, client, StringUtils.EMPTY, offline);
    }

    @Override
    public AuthenticatedClientSessionModel getClientSession(
            UserSessionModel userSession, ClientModel client, String clientSessionId, boolean offline) {
        log.tracef(
                "getClientSession(%s, %s, %s, %s)%s",
                userSession, client, clientSessionId, offline, getShortStackTrace());

        if (userSession == null) {
            return null;
        }

        // Reload Session to filter out transient sessions
        CassandraUserSessionAdapter currentSession = getUserSession(userSession.getRealm(), userSession.getId());
        return currentSession == null ? null : currentSession.getAuthenticatedClientSessionByClient(client.getId());
    }

    @Override
    public UserSessionModel createUserSession(
            RealmModel realm,
            UserModel user,
            String loginUsername,
            String ipAddress,
            String authMethod,
            boolean rememberMe,
            String brokerSessionId,
            String brokerUserId) {
        return createUserSession(
                null,
                realm,
                user,
                loginUsername,
                ipAddress,
                authMethod,
                rememberMe,
                brokerSessionId,
                brokerUserId,
                UserSessionModel.SessionPersistenceState.PERSISTENT);
    }

    @Override
    public UserSessionModel createUserSession(
            String id,
            RealmModel realm,
            UserModel user,
            String loginUsername,
            String ipAddress,
            String authMethod,
            boolean rememberMe,
            String brokerSessionId,
            String brokerUserId,
            UserSessionModel.SessionPersistenceState persistenceState) {
        log.tracef(
                "createUserSession(%s, %s, %s, %s)%s",
                id, realm, loginUsername, persistenceState, getShortStackTrace());

        UserSession entity = createUserSessionEntityInstance(
                id,
                realm.getId(),
                user.getId(),
                loginUsername,
                ipAddress,
                authMethod,
                rememberMe,
                brokerSessionId,
                brokerUserId,
                false);

        entity.setPersistenceState(persistenceState);
        setUserSessionExpiration(
                entity, SessionExpirationData.builder().realm(realm).build());
        if (TRANSIENT == persistenceState) {
            if (id == null) {
                entity.setId(UUID.randomUUID().toString());
            }
            transientUserSessions.put(entity.getId(), entity);
        } else {
            if (id != null && userSessionRepository.findUserSessionById(id) != null) {
                throw new ModelDuplicateException("User session exists: " + id);
            }
            userSessionRepository.insert(realm, entity);
        }

        CassandraUserSessionAdapter userSession = entityToAdapterFunc(realm).apply(entity);

        if (userSession != null) {
            userSession.bindUser(user);
            DeviceActivityManager.attachDevice(userSession, session);
        }

        return userSession;
    }

    @Override
    public CassandraUserSessionAdapter getUserSession(RealmModel realm, String id) {
        Objects.requireNonNull(realm, "The provided realm can't be null!");

        log.tracef("getUserSession(%s, %s)%s", realm, id, getShortStackTrace());

        if (id == null) return null;

        CassandraUserSessionAdapter loadedSession = sessionModels.get(id);
        if (loadedSession == null) {
            UserSession session = userSessionRepository.findUserSessionById(id);
            return entityToAdapterFunc(realm).apply(session);
        } else {
            return loadedSession;
        }
    }

    @Override
    public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, UserModel user) {
        log.tracef("getUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

        return userSessionRepository.findUserSessionsByUserId(user.getId()).stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() == null || !s.getOffline())
                .map(entityToAdapterFunc((realm)));
    }

    @Override
    public Stream<UserSessionModel> getUserSessionsStream(RealmModel realm, ClientModel client) {
        log.tracef("getUserSessionsStream(%s, %s)%s", realm, client, getShortStackTrace());

        return userSessionRepository.findUserSessionsByClientId(client.getId()).stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() == null || !s.getOffline())
                .map(entityToAdapterFunc((realm)));
    }

    @Override
    public Stream<UserSessionModel> getUserSessionsStream(
            RealmModel realm, ClientModel client, Integer firstResult, Integer maxResults) {
        log.tracef(
                "getUserSessionsStream(%s, %s, %s, %s)%s",
                realm, client, firstResult, maxResults, getShortStackTrace());

        return getUserSessionsStream(realm, client)
                .filter(s -> s.getRealm().equals(realm))
                .filter(s -> !s.isOffline())
                .skip(firstResult != null && firstResult > 0 ? firstResult : 0)
                .limit(maxResults != null && maxResults > 0 ? maxResults : Long.MAX_VALUE);
    }

    @Override
    public Stream<UserSessionModel> getUserSessionByBrokerUserIdStream(RealmModel realm, String brokerUserId) {
        log.tracef("getUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

        return userSessionRepository.findUserSessionsByBrokerUserId(brokerUserId).stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() == null || !s.getOffline())
                .map(entityToAdapterFunc((realm)));
    }

    @Override
    public UserSessionModel getUserSessionByBrokerSessionId(RealmModel realm, String brokerSessionId) {
        log.tracef("getUserSessionByBrokerSessionId(%s, %s)%s", realm, brokerSessionId, getShortStackTrace());

        return userSessionRepository.findUserSessionsByBrokerSession(brokerSessionId).stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() == null || !s.getOffline())
                .map(entityToAdapterFunc((realm)))
                .findFirst()
                .orElse(null);
    }

    @Override
    public UserSessionModel getUserSessionWithPredicate(
            RealmModel realm, String id, boolean offline, Predicate<UserSessionModel> predicate) {
        log.tracef("getUserSessionWithPredicate(%s, %s, %s)%s", realm, id, offline, getShortStackTrace());

        Stream<CassandraUserSessionAdapter> userSessionEntityStream;
        if (offline) {
            userSessionEntityStream = getOfflineUserSessionEntityStream(realm, id)
                    .map(entityToAdapterFunc(realm))
                    .filter(Objects::nonNull);
        } else {
            CassandraUserSessionAdapter userSession = getUserSession(realm, id);
            userSessionEntityStream = userSession != null ? Stream.of(userSession) : Stream.empty();
        }

        return userSessionEntityStream.filter(predicate).findFirst().orElse(null);
    }

    @Override
    public long getActiveUserSessions(RealmModel realm, ClientModel client) {
        log.tracef("getActiveUserSessions(%s, %s)%s", realm, client, getShortStackTrace());

        return userSessionRepository.findUserSessionsByClientId(client.getId()).size();
    }

    @Override
    public Map<String, Long> getActiveClientSessionStats(RealmModel realm, boolean offline) {
        log.tracef("getActiveClientSessionStats(%s, %s)%s", realm, offline, getShortStackTrace());

        return userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() == offline)
                .map(entityToAdapterFunc(realm))
                .filter(Objects::nonNull)
                .map(UserSessionModel::getAuthenticatedClientSessions)
                .map(Map::keySet)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    @Override
    public void removeUserSession(RealmModel realm, UserSessionModel session) {
        Objects.requireNonNull(session, "The provided user session can't be null!");

        log.tracef("removeUserSession(%s, %s)%s", realm, session, getShortStackTrace());

        userSessionRepository.deleteUserSession(session.getId());
        ((CassandraUserSessionAdapter) session).markAsDeleted();
        sessionModels.remove(session.getId());
    }

    @Override
    public void removeUserSessions(RealmModel realm, UserModel user) {
        log.tracef("removeUserSessions(%s, %s)%s", realm, user, getShortStackTrace());

        userSessionRepository.findUserSessionsByUserId(user.getId()).forEach(session -> {
            userSessionRepository.deleteUserSession(session.getId());

            CassandraUserSessionAdapter model = sessionModels.get(session.getId());
            if (model != null) {
                model.markAsDeleted();
            }

            sessionModels.remove(session.getId());
        });
    }

    protected void removeAllUserSessions(RealmModel realm) {
        log.tracef("removeAllUserSessions(%s)%s", realm, getShortStackTrace());

        realm.getClientsStream()
                .flatMap(c -> userSessionRepository.findUserSessionsByClientId(c.getId()).stream())
                .forEach(session -> {
                    userSessionRepository.deleteUserSession(session.getId());
                    CassandraUserSessionAdapter model = sessionModels.get(session.getId());
                    if (model != null) {
                        model.markAsDeleted();
                    }

                    sessionModels.remove(session.getId());
                });
    }

    @Override
    public void removeAllExpired() {
        log.tracef("removeAllExpired()%s", getShortStackTrace());
        // NOOP: Handled by Cassandra-TTL
    }

    @Override
    public void removeExpired(RealmModel realm) {
        log.tracef("removeExpired(%s)%s", realm, getShortStackTrace());
        // NOOP: Handled by Cassandra-TTL
    }

    @Override
    public void removeUserSessions(RealmModel realm) {
        userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .forEach(session -> {
                    userSessionRepository.deleteUserSession(session.getId());
                    CassandraUserSessionAdapter model = sessionModels.get(session.getId());
                    if (model != null) {
                        model.markAsDeleted();
                    }

                    sessionModels.remove(session.getId());
                });
    }

    @Override
    public void onRealmRemoved(RealmModel realm) {
        log.tracef("onRealmRemoved(%s)%s", realm, getShortStackTrace());
        removeUserSessions(realm);
    }

    @Override
    public void onClientRemoved(RealmModel realm, ClientModel client) {
        // Sessions time out eventually... Looking for relevant client sessions can be very slow
    }

    @Override
    public UserSessionModel createOfflineUserSession(UserSessionModel userSession) {
        log.tracef("createOfflineUserSession(%s)%s", userSession, getShortStackTrace());

        if (userSession.getNote(CORRESPONDING_SESSION_ID) != null) {
            return getUserSession(userSession.getRealm(), userSession.getNote(CORRESPONDING_SESSION_ID));
        }

        UserSession offlineUserSession = createUserSessionEntityInstance(userSession, true);
        long currentTime = Time.currentTimeMillis();
        offlineUserSession.setTimestamp(currentTime);
        offlineUserSession.setLastSessionRefresh(currentTime);
        setUserSessionExpiration(
                offlineUserSession,
                SessionExpirationData.builder().realm(userSession.getRealm()).build());

        CassandraUserSessionAdapter offlineSessionAdapter =
                entityToAdapterFunc(userSession.getRealm()).apply(offlineUserSession);
        offlineSessionAdapter.setNote(CORRESPONDING_SESSION_ID, userSession.getId());
        userSessionRepository.insert(userSession.getRealm(), offlineUserSession);

        // set a reference for the offline user session to the original online user session
        CassandraUserSessionAdapter orgUserSessionAdapter = getUserSession(userSession.getRealm(), userSession.getId());
        orgUserSessionAdapter.setNote(CORRESPONDING_SESSION_ID, offlineUserSession.getId());
        userSessionRepository.insert(
                userSession.getRealm(),
                orgUserSessionAdapter
                        .getUserSessionEntity()); // Hack to set CORRESPONDING_SESSION_ID, which normally is
        // only set during insert

        return offlineSessionAdapter;
    }

    @Override
    public UserSessionModel getOfflineUserSession(RealmModel realm, String userSessionId) {
        log.tracef("getOfflineUserSession(%s, %s)%s", realm, userSessionId, getShortStackTrace());

        return getOfflineUserSessionEntityStream(realm, userSessionId)
                .filter(Objects::nonNull)
                .findFirst()
                .map(entityToAdapterFunc(realm))
                .orElse(entityToAdapterFunc(realm)
                        .apply(userSessionRepository.findFirstUserSessionByAttribute(
                                CORRESPONDING_SESSION_ID, userSessionId)));
    }

    @Override
    public void removeOfflineUserSession(RealmModel realm, UserSessionModel userSession) {
        Objects.requireNonNull(userSession, "The provided user session can't be null!");

        log.tracef("removeOfflineUserSession(%s, %s)%s", realm, userSession, getShortStackTrace());

        UserSession userSessionEntity = userSessionRepository.findUserSessionById(userSession.getId());
        if (userSessionEntity.getOffline() != null && userSessionEntity.getOffline()) {
            userSessionRepository.deleteUserSession(userSessionEntity);
            CassandraUserSessionAdapter model = sessionModels.get(userSessionEntity.getId());
            if (model != null) {
                model.markAsDeleted();
            }
            sessionModels.remove(userSessionEntity.getId());
        } else if (userSessionEntity.hasCorrespondingSession()) {
            String correspondingSessionId = userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);
            userSessionRepository.deleteCorrespondingUserSession(userSessionEntity);
            CassandraUserSessionAdapter model = sessionModels.get(correspondingSessionId);
            if (model != null) {
                model.markAsDeleted();
            }
            sessionModels.remove(correspondingSessionId);
        }
    }

    @Override
    public AuthenticatedClientSessionModel createOfflineClientSession(
            AuthenticatedClientSessionModel clientSession, UserSessionModel offlineUserSession) {
        log.tracef("createOfflineClientSession(%s, %s)%s", clientSession, offlineUserSession, getShortStackTrace());

        AuthenticatedClientSessionValue clientSessionEntity =
                createAuthenticatedClientSessionInstance(clientSession, true);
        int currentTime = Time.currentTime();
        clientSessionEntity
                .getNotes()
                .put(AuthenticatedClientSessionModel.STARTED_AT_NOTE, String.valueOf(currentTime));
        clientSessionEntity.setTimestamp(Time.currentTimeMillis());
        RealmModel realm = clientSession.getRealm();
        setClientSessionExpiration(
                clientSessionEntity,
                SessionExpirationData.builder().realm(realm).build(),
                clientSession.getClient());

        Optional<UserSession> userSessionEntity = getOfflineUserSessionEntityStream(realm, offlineUserSession.getId())
                .findFirst();
        if (userSessionEntity.isPresent()) {
            UserSession userSession = userSessionEntity.get();
            String clientId = clientSession.getClient().getId();

            CassandraUserSessionAdapter userSessionModel =
                    entityToAdapterFunc(realm).apply(userSession);

            userSessionRepository.addClientSession(realm, userSessionModel.getUserSessionEntity(), clientSessionEntity);

            return userSessionModel.getAuthenticatedClientSessionByClient(clientId);
        }

        return null;
    }

    @Override
    public Stream<UserSessionModel> getOfflineUserSessionsStream(RealmModel realm, UserModel user) {
        log.tracef("getOfflineUserSessionsStream(%s, %s)%s", realm, user, getShortStackTrace());

        return userSessionRepository.findUserSessionsByUserId(user.getId()).stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() != null && s.getOffline())
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public Stream<UserSessionModel> getOfflineUserSessionByBrokerUserIdStream(RealmModel realm, String brokerUserId) {
        log.tracef("getOfflineUserSessionByBrokerUserIdStream(%s, %s)%s", realm, brokerUserId, getShortStackTrace());

        return userSessionRepository.findUserSessionsByBrokerUserId(brokerUserId).stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() != null && s.getOffline())
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public long getOfflineSessionsCount(RealmModel realm, ClientModel client) {
        log.tracef("getOfflineSessionsCount(%s, %s)%s", realm, client, getShortStackTrace());

        return userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() != null && s.getOffline())
                .flatMap(s -> s.getClientSessions().values().stream())
                .filter(s -> s.getClientId().equals(client.getId()))
                .count();
    }

    @Override
    public Stream<UserSessionModel> getOfflineUserSessionsStream(
            RealmModel realm, ClientModel client, Integer firstResult, Integer maxResults) {
        log.tracef(
                "getOfflineUserSessionsStream(%s, %s, %s, %s)%s",
                realm, client, firstResult, maxResults, getShortStackTrace());

        return userSessionRepository.findAll().stream()
                .filter(s -> s.getRealmId().equals(realm.getId()))
                .filter(s -> s.getOffline() != null && s.getOffline())
                .filter(s -> s.getClientSessions().containsKey(client.getId()))
                .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
                .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
                .sorted(Comparator.comparing(UserSession::getLastSessionRefresh))
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public void importUserSessions(Collection<UserSessionModel> persistentUserSessions, boolean offline) {
        if (persistentUserSessions == null || persistentUserSessions.isEmpty()) {
            return;
        }

        persistentUserSessions.stream()
                .map(pus -> {
                    UserSession userSessionEntity = createUserSessionEntityInstance(
                            null,
                            pus.getRealm().getId(),
                            pus.getUser().getId(),
                            pus.getLoginUsername(),
                            pus.getIpAddress(),
                            pus.getAuthMethod(),
                            pus.isRememberMe(),
                            pus.getBrokerSessionId(),
                            pus.getBrokerUserId(),
                            offline);
                    userSessionEntity.setPersistenceState(UserSessionModel.SessionPersistenceState.PERSISTENT);

                    for (Map.Entry<String, AuthenticatedClientSessionModel> entry :
                            pus.getAuthenticatedClientSessions().entrySet()) {
                        AuthenticatedClientSessionValue clientSession =
                                createAuthenticatedClientSessionInstance(entry.getValue(), offline);

                        // Update timestamp to same value as userSession. LastSessionRefresh of userSession
                        // from DB will have correct value
                        clientSession.setTimestamp(userSessionEntity.getLastSessionRefresh());

                        RealmModel realm = session.realms().getRealm(userSessionEntity.getRealmId());
                        userSessionRepository.insert(realm, userSessionEntity);
                        userSessionRepository.addClientSession(realm, userSessionEntity, clientSession);

                        CassandraUserSessionAdapter adapter =
                                entityToAdapterFunc(pus.getRealm()).apply(userSessionEntity);
                        sessionModels.put(adapter.getId(), adapter);
                    }

                    return userSessionEntity;
                })
                .forEach(userSession ->
                        userSessionRepository.insert(session.realms().getRealm(userSession.getRealmId()), userSession));
    }

    @Override
    public void close() {
        // Nothing to do
    }

    @Override
    public int getStartupTime(RealmModel realm) {
        return realm.getNotBefore();
    }

    private Stream<UserSession> getOfflineUserSessionEntityStream(RealmModel realm, String userSessionId) {
        if (userSessionId == null) {
            return Stream.empty();
        }

        // first get a user entity by ID
        // check if it's an offline user session
        UserSession userSessionEntity = getUserSessionById(userSessionId);
        if (userSessionEntity != null) {
            if (Boolean.TRUE.equals(userSessionEntity.getOffline())) {
                return Stream.of(userSessionEntity);
            }
        } else {
            // no session found by the given ID, try to find by corresponding session ID
            return userSessionRepository.findUserSessionsByAttribute(CORRESPONDING_SESSION_ID, userSessionId).stream();
        }

        // it's online user session so lookup offline user session by corresponding session id reference
        CassandraUserSessionAdapter sessionModel = sessionModels.get(userSessionId);
        String offlineUserSessionId = sessionModel != null
                ? sessionModel.getNote(CORRESPONDING_SESSION_ID)
                : userSessionEntity.getNotes().get(CORRESPONDING_SESSION_ID);
        if (offlineUserSessionId != null) {
            return Stream.ofNullable(getUserSessionById(offlineUserSessionId));
        }

        return Stream.empty();
    }

    private UserSession getUserSessionById(String id) {
        if (id == null) return null;

        UserSession userSessionEntity = transientUserSessions.get(id);

        if (userSessionEntity == null) {
            return userSessionRepository.findUserSessionById(id);
        }
        return userSessionEntity;
    }

    private UserSession createUserSessionEntityInstance(UserSessionModel userSession, boolean offline) {
        UserSession entity = createUserSessionEntityInstance(
                null,
                userSession.getRealm().getId(),
                userSession.getUser().getId(),
                userSession.getLoginUsername(),
                userSession.getIpAddress(),
                userSession.getAuthMethod(),
                userSession.isRememberMe(),
                userSession.getBrokerSessionId(),
                userSession.getBrokerUserId(),
                offline);

        entity.setNotes(new HashMap<>(userSession.getNotes()));
        entity.setState(userSession.getState());
        entity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(userSession.getStarted()));
        entity.setLastSessionRefresh(TimeAdapter.fromSecondsToMilliseconds(userSession.getLastSessionRefresh()));

        return entity;
    }

    private UserSession createUserSessionEntityInstance(
            String id,
            String realmId,
            String userId,
            String loginUsername,
            String ipAddress,
            String authMethod,
            boolean rememberMe,
            String brokerSessionId,
            String brokerUserId,
            boolean offline) {
        long timestamp = Time.currentTimeMillis();

        return UserSession.builder()
                .id(id == null ? KeycloakModelUtils.generateId() : id)
                .realmId(realmId)
                .userId(userId)
                .loginUsername(loginUsername)
                .ipAddress(ipAddress)
                .authMethod(authMethod)
                .rememberMe(rememberMe)
                .brokerSessionId(brokerSessionId)
                .brokerUserId(brokerUserId)
                .offline(offline)
                .timestamp(timestamp)
                .lastSessionRefresh(timestamp)
                .notes(new HashMap<>())
                .build();
    }

    private AuthenticatedClientSessionValue createAuthenticatedClientSessionEntityInstance(
            String id, String clientId, boolean offline) {
        return AuthenticatedClientSessionValue.builder()
                .id(id == null ? KeycloakModelUtils.generateId() : id)
                .clientId(clientId)
                .offline(offline)
                .timestamp(Time.currentTimeMillis())
                .notes(new HashMap<>())
                .build();
    }

    private AuthenticatedClientSessionValue createAuthenticatedClientSessionInstance(
            AuthenticatedClientSessionModel clientSession, boolean offline) {
        AuthenticatedClientSessionValue entity = createAuthenticatedClientSessionEntityInstance(
                null, clientSession.getClient().getId(), offline);

        entity.setAction(clientSession.getAction());
        entity.setAuthMethod(clientSession.getProtocol());

        entity.setNotes(new HashMap<>(clientSession.getNotes()));
        entity.setRedirectUri(clientSession.getRedirectUri());
        entity.setTimestamp(TimeAdapter.fromSecondsToMilliseconds(clientSession.getTimestamp()));

        return entity;
    }
}
