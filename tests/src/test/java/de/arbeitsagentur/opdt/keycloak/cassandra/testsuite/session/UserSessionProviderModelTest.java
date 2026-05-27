/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import static de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session.SessionTestUtils.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static org.keycloak.models.utils.KeycloakModelUtils.generateId;
import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraTransientUsersKeycloakServerConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraTransientUsersKeycloakServerConfig.class)
public class UserSessionProviderModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "user-session-provider";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = UserSessionProviderRealmConfig.class)
    ManagedRealm managedRealm;

    // Copied / Adapted from org.keycloak.testsuite.model.session.UserSessionProviderModelTest
    @TestOnServer
    public void testMultipleSessionsRemovalInOneTransaction(KeycloakSession testSession) {

        UserSessionModel[] origSessions = inCommittedTransaction(testSession, session -> {
            return createSessions(session, REALM_NAME);
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertEquals(origSessions[0], userSession);

            userSession = session.sessions().getUserSession(realm, origSessions[1].getId());
            Assert.assertEquals(origSessions[1], userSession);
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            session.sessions().removeUserSession(realm, origSessions[0]);
            session.sessions().removeUserSession(realm, origSessions[1]);
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertNull(userSession);

            userSession = session.sessions().getUserSession(realm, origSessions[1].getId());
            Assert.assertNull(userSession);
        });
    }

    @TestOnServer
    public void testExpiredClientSessions(KeycloakSession testSession) {

        UserSessionModel[] origSessions = inCommittedTransaction(testSession, session -> {
            // create some user and client sessions
            return createSessions(session, REALM_NAME);
        });

        AtomicReference<List<String>> clientSessionIds = new AtomicReference<>();
        clientSessionIds.set(origSessions[0].getAuthenticatedClientSessions().values().stream()
                .map(AuthenticatedClientSessionModel::getId)
                .collect(Collectors.toList()));

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertEquals(origSessions[0], userSession);

            AuthenticatedClientSessionModel clientSession = session.sessions()
                    .getClientSession(
                            userSession,
                            realm.getClientByClientId("test-app"),
                            origSessions[0]
                                    .getAuthenticatedClientSessionByClient(realm.getClientByClientId("test-app")
                                            .getId())
                                    .getId(),
                            false);
            Assert.assertEquals(
                    origSessions[0]
                            .getAuthenticatedClientSessionByClient(
                                    realm.getClientByClientId("test-app").getId())
                            .getId(),
                    clientSession.getId());

            userSession = session.sessions().getUserSession(realm, origSessions[1].getId());
            Assert.assertEquals(origSessions[1], userSession);
        });

        // not possible to expire client session without expiring user sessions with time offset in map
        // storage because
        // expiration in map storage takes min of (clientSessionIdleExpiration, ssoSessionIdleTimeout)
        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());

            userSession.getAuthenticatedClientSessions().values().stream().forEach(clientSession -> {
                // expire client sessions
                clientSession.setTimestamp(1);
            });
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            // assert the user session is still there
            UserSessionModel userSession = session.sessions().getUserSession(realm, origSessions[0].getId());
            Assert.assertEquals(origSessions[0], userSession);

            // assert the client sessions are expired
            clientSessionIds.get().forEach(clientSessionId -> {
                Assert.assertNull(session.sessions()
                        .getClientSession(userSession, realm.getClientByClientId("test-app"), clientSessionId, false));
                Assert.assertNull(session.sessions()
                        .getClientSession(
                                userSession, realm.getClientByClientId("third-party"), clientSessionId, false));
            });
        });
    }

    @TestOnServer
    public void testTransientUserSessionIsNotPersisted(KeycloakSession testSession) {

        String id = inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);
            UserSessionModel userSession = session.sessions()
                    .createUserSession(
                            generateId(),
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            false,
                            null,
                            null,
                            UserSessionModel.SessionPersistenceState.TRANSIENT);

            ClientModel testApp = realm.getClientByClientId("test-app");
            AuthenticatedClientSessionModel clientSession =
                    session.sessions().createClientSession(realm, testApp, userSession);

            // assert the client sessions are present
            assertThat(
                    session.sessions().getClientSession(userSession, testApp, clientSession.getId(), false),
                    notNullValue());
            return userSession.getId();
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);
            UserSessionModel userSession = session.sessions().getUserSession(realm, id);

            // in new transaction transient session should not be present
            assertThat(userSession, nullValue());
        });
    }

    @TestOnServer
    public void testClientSessionIsNotPersistedForTransientUserSession(KeycloakSession testSession) {

        Object[] transientUserSessionWithClientSessionId = inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);
            UserSessionModel userSession = session.sessions()
                    .createUserSession(
                            null,
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            false,
                            null,
                            null,
                            UserSessionModel.SessionPersistenceState.TRANSIENT);
            ClientModel testApp = realm.getClientByClientId("test-app");
            AuthenticatedClientSessionModel clientSession =
                    session.sessions().createClientSession(realm, testApp, userSession);

            // assert the client sessions are present
            assertThat(
                    session.sessions().getClientSession(userSession, testApp, clientSession.getId(), false),
                    notNullValue());
            Object[] result = new Object[2];
            result[0] = userSession;
            result[1] = clientSession.getId();
            return result;
        });
        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);
            ClientModel testApp = realm.getClientByClientId("test-app");
            UserSessionModel userSession = (UserSessionModel) transientUserSessionWithClientSessionId[0];
            String clientSessionId = (String) transientUserSessionWithClientSessionId[1];
            // in new transaction transient session should not be present
            assertThat(session.sessions().getClientSession(userSession, testApp, clientSessionId, false), nullValue());
        });
    }

    @TestOnServer
    public void testCreateManyUserSessions(KeycloakSession testSession) {

        Set<String> userSessionIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        KeycloakSessionFactory factory = testSession.getKeycloakSessionFactory();
        IntStream.range(0, 4 * 30)
                .forEach(i -> runJobInTransaction(factory, session -> {
                    RealmModel realm = session.realms().getRealmByName(REALM_NAME);
                    UserModel user = session.users().getUserByUsername(realm, "user1");
                    UserSessionModel userSession =
                            session.sessions().createUserSession(realm, user, "user1", "", "", false, null, null);
                    userSessionIds.add(userSession.getId());
                }));

        assertThat(userSessionIds, Matchers.iterableWithSize(4 * 30));

        runJobInTransaction(factory, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);
            userSessionIds.forEach(id -> Assert.assertNotNull(session.sessions().getUserSession(realm, id)));
        });
    }

    // Based off of UserSessionProviderTests (Arquillian)
    @TestOnServer
    public void testCreateSessions(KeycloakSession testSession) {

        int started = Time.currentTime();

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            assertSession(
                    s.sessions().getUserSession(r, sessions[0].getId()),
                    s.users().getUserByUsername(r, "user1"),
                    "127.0.0.1",
                    started,
                    started,
                    "test-app",
                    "third-party");
            assertSession(
                    s.sessions().getUserSession(r, sessions[1].getId()),
                    s.users().getUserByUsername(r, "user1"),
                    "127.0.0.2",
                    started,
                    started,
                    "test-app");
            assertSession(
                    s.sessions().getUserSession(r, sessions[2].getId()),
                    s.users().getUserByUsername(r, "user2"),
                    "127.0.0.3",
                    started,
                    started,
                    "test-app");
            return null;
        });
    }

    @TestOnServer
    public void testCreateSessionsTransientUser(KeycloakSession testSession) {

        int started = Time.currentTime();

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessionsTransientUser(s, r.getId());
            assertSessionLightweightUser(
                    s.sessions().getUserSession(r, sessions[0].getId()),
                    "user1",
                    "127.0.0.1",
                    started,
                    started,
                    "test-app",
                    "third-party");
            assertSessionLightweightUser(
                    s.sessions().getUserSession(r, sessions[1].getId()),
                    "user1",
                    "127.0.0.2",
                    started,
                    started,
                    "test-app");
            assertSessionLightweightUser(
                    s.sessions().getUserSession(r, sessions[2].getId()),
                    "user2",
                    "127.0.0.3",
                    started,
                    started,
                    "test-app");
            return null;
        });
    }

    @TestOnServer
    public void testUpdateSession(KeycloakSession testSession) {

        int lastRefresh = Time.currentTime();
        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            s.sessions().getUserSession(r, sessions[0].getId()).setLastSessionRefresh(lastRefresh);
            assertEquals(
                    lastRefresh,
                    s.sessions().getUserSession(r, sessions[0].getId()).getLastSessionRefresh());
            return null;
        });
    }

    @TestOnServer
    public void testUpdateSessionInSameTransaction(KeycloakSession testSession) {

        int lastRefresh = Time.currentTime();
        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            s.sessions().getUserSession(r, sessions[0].getId()).setLastSessionRefresh(lastRefresh);
            assertEquals(
                    lastRefresh,
                    s.sessions().getUserSession(r, sessions[0].getId()).getLastSessionRefresh());
            return null;
        });
    }

    @TestOnServer
    public void testRestartSession(KeycloakSession testSession) {

        int started = Time.currentTime();
        UserSessionModel[] sessions = withRealm(testSession, REALM_NAME, (s, r) -> createSessions(s, r.getId()));

        try {
            Time.setOffset(100);
            withRealm(testSession, REALM_NAME, (s, r) -> {
                UserSessionModel userSession = s.sessions().getUserSession(r, sessions[0].getId());
                assertSession(
                        userSession,
                        s.users().getUserByUsername(r, "user1"),
                        "127.0.0.1",
                        started,
                        started,
                        "test-app",
                        "third-party");

                userSession.restartSession(
                        r, s.users().getUserByUsername(r, "user2"), "user2", "127.0.0.6", "form", true, null, null);

                userSession = s.sessions().getUserSession(r, sessions[0].getId());
                assertSession(
                        userSession,
                        s.users().getUserByUsername(r, "user2"),
                        "127.0.0.6",
                        started + 100,
                        started + 100);
                return null;
            });
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testCreateClientSession(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            Map<String, AuthenticatedClientSessionModel> clientSessions =
                    s.sessions().getUserSession(r, sessions[0].getId()).getAuthenticatedClientSessions();
            assertEquals(2, clientSessions.size());

            String clientUUID = r.getClientByClientId("test-app").getId();

            AuthenticatedClientSessionModel session1 = clientSessions.get(clientUUID);

            assertNull(session1.getAction());
            assertEquals(
                    r.getClientByClientId("test-app").getClientId(),
                    session1.getClient().getClientId());
            assertEquals(sessions[0].getId(), session1.getUserSession().getId());
            assertEquals("http://redirect", session1.getRedirectUri());
            assertEquals("state", session1.getNote(OIDCLoginProtocol.STATE_PARAM));
            return null;
        });
    }

    @TestOnServer
    public void testUpdateClientSession(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            String userSessionId = sessions[0].getId();
            String clientUUID = r.getClientByClientId("test-app").getId();
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessions().get(clientUUID);

            int time = clientSession.getTimestamp();
            assertNull(clientSession.getAction());

            clientSession.setAction(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name());
            clientSession.setTimestamp(time + 10);

            AuthenticatedClientSessionModel updated = s.sessions()
                    .getUserSession(r, userSessionId)
                    .getAuthenticatedClientSessions()
                    .get(clientUUID);
            assertEquals(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name(), updated.getAction());
            assertEquals(time + 10, updated.getTimestamp());
            return null;
        });
    }

    @TestOnServer
    public void testUpdateClientSessionWithGetByClientId(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            String userSessionId = sessions[0].getId();
            String clientUUID = r.getClientByClientId("test-app").getId();
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(clientUUID);

            int time = clientSession.getTimestamp();
            assertNull(clientSession.getAction());

            clientSession.setAction(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name());
            clientSession.setTimestamp(time + 10);

            AuthenticatedClientSessionModel updated =
                    s.sessions().getUserSession(r, userSessionId).getAuthenticatedClientSessionByClient(clientUUID);
            assertEquals(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name(), updated.getAction());
            assertEquals(time + 10, updated.getTimestamp());
            return null;
        });
    }

    @TestOnServer
    public void testUpdateClientSessionInSameTransaction(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            String userSessionId = sessions[0].getId();
            String clientUUID = r.getClientByClientId("test-app").getId();
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            AuthenticatedClientSessionModel clientSession =
                    userSession.getAuthenticatedClientSessionByClient(clientUUID);

            clientSession.setAction(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name());
            clientSession.setNote("foo", "bar");

            AuthenticatedClientSessionModel updated =
                    s.sessions().getUserSession(r, userSessionId).getAuthenticatedClientSessionByClient(clientUUID);
            assertEquals(AuthenticatedClientSessionModel.Action.LOGGED_OUT.name(), updated.getAction());
            assertEquals("bar", updated.getNote("foo"));
            return null;
        });
    }

    @TestOnServer
    public void testGetUserSessions(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());
            assertSessions(
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user1"))
                            .collect(Collectors.toList()),
                    sessions[0],
                    sessions[1]);
            assertSessions(
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                            .collect(Collectors.toList()),
                    sessions[2]);
            return null;
        });
    }

    @TestOnServer
    public void testRemoveUserSessionsByUser(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> createSessions(s, r.getId()));

        final Map<String, Integer> clientSessionsKept = new HashMap<>();
        withRealm(testSession, REALM_NAME, (s, r) -> {
            clientSessionsKept.putAll(s.sessions()
                    .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                    .collect(Collectors.toMap(UserSessionModel::getId, model -> model.getAuthenticatedClientSessions()
                            .keySet()
                            .size())));

            s.sessions().removeUserSessions(r, s.users().getUserByUsername(r, "user1"));
            return null;
        });

        withRealm(testSession, REALM_NAME, (s, r) -> {
            assertEquals(
                    0,
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user1"))
                            .count());
            List<UserSessionModel> userSessions = s.sessions()
                    .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                    .collect(Collectors.toList());

            assertSame(userSessions.size(), 1);

            for (UserSessionModel userSession : userSessions) {
                Assert.assertEquals(
                        (int) clientSessionsKept.get(userSession.getId()),
                        userSession.getAuthenticatedClientSessions().size());
            }
            return null;
        });
    }

    @TestOnServer
    public void testRemoveUserSession(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel userSession = createSessions(s, r.getId())[0];

            s.sessions().removeUserSession(r, userSession);

            assertNull(s.sessions().getUserSession(r, userSession.getId()));
            return null;
        });
    }

    @TestOnServer
    public void testRemoveUserSessionsByRealm(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            createSessions(s, r.getId());
            s.sessions().removeUserSessions(r);

            assertEquals(
                    0,
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user1"))
                            .count());
            assertEquals(
                    0,
                    s.sessions()
                            .getUserSessionsStream(r, s.users().getUserByUsername(r, "user2"))
                            .count());
            return null;
        });
    }

    @TestOnServer
    public void testOnClientRemoved(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel[] sessions = createSessions(s, r.getId());

            String thirdPartyClientUUID = r.getClientByClientId("third-party").getId();

            Map<String, Set<String>> clientSessionsKept = new HashMap<>();
            for (UserSessionModel session : sessions) {
                // session associated with the model was closed, load it by id into a new session
                session = s.sessions().getUserSession(r, session.getId());
                Set<String> clientUUIDS =
                        new HashSet<>(session.getAuthenticatedClientSessions().keySet());
                clientUUIDS.remove(thirdPartyClientUUID); // This client will be later removed, hence his
                // clientSessions too
                clientSessionsKept.put(session.getId(), clientUUIDS);
            }

            r.removeClient(thirdPartyClientUUID);

            for (UserSessionModel session : sessions) {
                session = s.sessions().getUserSession(r, session.getId());
                Set<String> clientUUIDS =
                        session.getAuthenticatedClientSessions().keySet();
                assertEquals(clientUUIDS, clientSessionsKept.get(session.getId()));
            }

            // Revert client
            r.addClient("third-party");
            return null;
        });
    }

    @TestOnServer
    public void testTransientUserSession(KeycloakSession testSession) {

        String userSessionId = UUID.randomUUID().toString();
        // create an user session, but don't persist it to infinispan
        withRealm(testSession, REALM_NAME, (s, r) -> {
            ClientModel client = r.getClientByClientId("test-app");
            long sessionsBefore = s.sessions().getActiveUserSessions(r, client);

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            userSessionId,
                            r,
                            s.users().getUserByUsername(r, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null,
                            UserSessionModel.SessionPersistenceState.TRANSIENT);
            AuthenticatedClientSessionModel clientSession = s.sessions().createClientSession(r, client, userSession);
            assertEquals(userSession, clientSession.getUserSession());

            assertSession(
                    userSession,
                    s.users().getUserByUsername(r, "user1"),
                    "127.0.0.1",
                    userSession.getStarted(),
                    userSession.getStarted(),
                    "test-app");

            // Can find session by ID in current transaction
            UserSessionModel foundSession = s.sessions().getUserSession(r, userSessionId);
            Assert.assertEquals(userSession, foundSession);

            // Count of sessions should be still the same
            Assert.assertEquals(sessionsBefore, s.sessions().getActiveUserSessions(r, client));
            return null;
        });

        // create an user session whose last refresh exceeds the max session idle timeout.
        withRealm(testSession, REALM_NAME, (s, r) -> {
            UserSessionModel userSession = s.sessions().getUserSession(r, userSessionId);
            Assert.assertNull(userSession);
            return null;
        });
    }

    @TestOnServer
    public void testGetByClient(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            final UserSessionModel[] sessions = createSessions(s, REALM_NAME);

            runJobInTransaction(s.getKeycloakSessionFactory(), (KeycloakSession kcSession) -> {
                assertSessions(
                        kcSession
                                .sessions()
                                .getUserSessionsStream(r, r.getClientByClientId("test-app"))
                                .collect(Collectors.toList()),
                        sessions[0],
                        sessions[1],
                        sessions[2]);
                assertSessions(
                        kcSession
                                .sessions()
                                .getUserSessionsStream(r, r.getClientByClientId("third-party"))
                                .collect(Collectors.toList()),
                        sessions[0]);
            });
            return null;
        });
    }

    @TestOnServer
    public void testGetByClientPaginated(KeycloakSession testSession) {
        try {
            withRealm(testSession, REALM_NAME, (s, r) -> {
                RealmModel realm = s.realms().getRealmByName(REALM_NAME);

                for (int i = 0; i < 25; i++) {
                    Time.setOffset(i);
                    UserSessionModel userSession = s.sessions()
                            .createUserSession(
                                    realm,
                                    s.users().getUserByUsername(realm, "user1"),
                                    "user1",
                                    "127.0.0." + i,
                                    "form",
                                    false,
                                    null,
                                    null);
                    AuthenticatedClientSessionModel clientSession =
                            s.sessions().createClientSession(realm, realm.getClientByClientId("test-app"), userSession);
                    assertNotNull(clientSession);
                    clientSession.setRedirectUri("http://redirect");
                    clientSession.setNote(OIDCLoginProtocol.STATE_PARAM, "state");
                    clientSession.setTimestamp(userSession.getStarted());
                    userSession.setLastSessionRefresh(userSession.getStarted());
                }
                return null;
            });

            withRealm(testSession, REALM_NAME, (s, r) -> {
                assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 0, 1, 1);
                assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 0, 10, 10);
                assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 10, 10, 10);
                assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 20, 10, 5);
                assertPaginatedSession(s, r, r.getClientByClientId("test-app"), 30, 10, 0);
                return null;
            });
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testCreateAndGetInSameTransaction(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName(REALM_NAME);
            ClientModel client = realm.getClientByClientId("test-app");
            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            AuthenticatedClientSessionModel clientSession =
                    createClientSession(s, realm.getId(), client, userSession, "http://redirect", "state");

            UserSessionModel userSessionLoaded = s.sessions().getUserSession(realm, userSession.getId());
            AuthenticatedClientSessionModel clientSessionLoaded =
                    userSessionLoaded.getAuthenticatedClientSessions().get(client.getId());
            Assert.assertNotNull(userSessionLoaded);
            Assert.assertNotNull(clientSessionLoaded);

            Assert.assertEquals(
                    userSession.getId(), clientSessionLoaded.getUserSession().getId());
            Assert.assertEquals(
                    1, userSessionLoaded.getAuthenticatedClientSessions().size());
            return null;
        });
    }

    @TestOnServer
    public void testAuthenticatedClientSessions(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName(REALM_NAME);
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);

            ClientModel client1 = realm.getClientByClientId("test-app");
            ClientModel client2 = realm.getClientByClientId("third-party");

            // Create client1 session
            AuthenticatedClientSessionModel clientSession1 =
                    s.sessions().createClientSession(realm, client1, userSession);
            clientSession1.setAction("foo1");
            int currentTime1 = Time.currentTime();
            clientSession1.setTimestamp(currentTime1);

            // Create client2 session
            AuthenticatedClientSessionModel clientSession2 =
                    s.sessions().createClientSession(realm, client2, userSession);
            clientSession2.setAction("foo2");
            int currentTime2 = Time.currentTime();
            clientSession2.setTimestamp(currentTime2);

            // Ensure sessions are here
            userSession = s.sessions().getUserSession(realm, userSession.getId());
            Map<String, AuthenticatedClientSessionModel> clientSessions = userSession.getAuthenticatedClientSessions();
            Assert.assertEquals(2, clientSessions.size());
            testAuthenticatedClientSession(
                    clientSessions.get(client1.getId()), "test-app", userSession.getId(), "foo1", currentTime1);
            testAuthenticatedClientSession(
                    clientSessions.get(client2.getId()), "third-party", userSession.getId(), "foo2", currentTime2);

            // Update session1
            clientSessions.get(client1.getId()).setAction("foo1-updated");

            // Ensure updated
            userSession = s.sessions().getUserSession(realm, userSession.getId());
            clientSessions = userSession.getAuthenticatedClientSessions();
            testAuthenticatedClientSession(
                    clientSessions.get(client1.getId()), "test-app", userSession.getId(), "foo1-updated", currentTime1);

            // Rewrite session2
            clientSession2 = s.sessions().createClientSession(realm, client2, userSession);
            clientSession2.setAction("foo2-rewrited");
            int currentTime3 = Time.currentTime();
            clientSession2.setTimestamp(currentTime3);

            // Ensure updated
            userSession = s.sessions().getUserSession(realm, userSession.getId());
            clientSessions = userSession.getAuthenticatedClientSessions();
            Assert.assertEquals(2, clientSessions.size());
            testAuthenticatedClientSession(
                    clientSessions.get(client1.getId()), "test-app", userSession.getId(), "foo1-updated", currentTime1);
            testAuthenticatedClientSession(
                    clientSessions.get(client2.getId()),
                    "third-party",
                    userSession.getId(),
                    "foo2-rewrited",
                    currentTime3);

            // remove session
            clientSession1 = userSession.getAuthenticatedClientSessions().get(client1.getId());
            clientSession1.detachFromUserSession();

            userSession = s.sessions().getUserSession(realm, userSession.getId());
            clientSessions = userSession.getAuthenticatedClientSessions();
            Assert.assertEquals(1, clientSessions.size());
            Assert.assertNull(clientSessions.get(client1.getId()));
            return null;
        });
    }

    private static void testAuthenticatedClientSession(
            AuthenticatedClientSessionModel clientSession,
            String expectedClientId,
            String expectedUserSessionId,
            String expectedAction,
            int expectedTimestamp) {
        Assert.assertEquals(expectedClientId, clientSession.getClient().getClientId());
        Assert.assertEquals(
                expectedUserSessionId, clientSession.getUserSession().getId());
        Assert.assertEquals(expectedAction, clientSession.getAction());
        Assert.assertEquals(expectedTimestamp, clientSession.getTimestamp());
    }

    private static void assertPaginatedSession(
            KeycloakSession session, RealmModel realm, ClientModel client, int start, int max, int expectedSize) {
        assertEquals(
                expectedSize,
                session.sessions()
                        .getUserSessionsStream(realm, client, start, max)
                        .count());
    }

    // Own Tests
    @TestOnServer
    public void testUserSessionNotes(KeycloakSession testSession) {

        String sessionId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session =
                    s.sessions().createUserSession(realm, testuser, "testuser", "127.0.0.1", "test", false, null, null);
            session.setNote("key1", "value1");
            session.setNote("key2", "value2");

            UserSessionModel newlyLoadedSession = s.sessions().getUserSession(realm, session.getId());
            newlyLoadedSession.setNote("key3", "value3");

            UserSessionModel currentSession = s.sessions().getUserSession(realm, session.getId());
            assertThat(currentSession.getNotes().entrySet(), hasSize(4));
            assertTrue(currentSession.getNotes().containsKey("KC_DEVICE_NOTE"));
            assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
            assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
            assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));

            return session.getId();
        });

        // New transaction
        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session = s.sessions().getUserSession(realm, sessionId);
            session.setNote("key4", "value4");

            UserSessionModel currentSession = s.sessions().getUserSession(realm, sessionId);
            assertThat(currentSession.getNotes().entrySet(), hasSize(5));
            assertTrue(currentSession.getNotes().containsKey("KC_DEVICE_NOTE"));
            assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
            assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
            assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));
            assertThat(currentSession.getNotes().get("key4"), equalTo("value4"));

            return null;
        });
    }

    @TestOnServer
    public void testClientSessionToUserSessionReference(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            ClientModel client = s.clients().addClient(realm, "testclient");
            UserSessionModel session =
                    s.sessions().createUserSession(realm, testuser, "testuser", "127.0.0.1", "test", false, null, null);
            session.setNote("key1", "value1");

            AuthenticatedClientSessionModel clientSession = s.sessions().createClientSession(realm, client, session);
            clientSession.setNote("ckey", "cval");

            session.setNote("key2", "value2");
            clientSession.getUserSession().setNote("key3", "value3");

            UserSessionModel currentSession = s.sessions().getUserSession(realm, session.getId());
            assertThat(currentSession.getNotes().entrySet(), hasSize(4));
            assertTrue(currentSession.getNotes().containsKey("KC_DEVICE_NOTE"));
            assertThat(currentSession.getNotes().get("key1"), equalTo("value1"));
            assertThat(currentSession.getNotes().get("key2"), equalTo("value2"));
            assertThat(currentSession.getNotes().get("key3"), equalTo("value3"));

            return session.getId();
        });
    }

    @TestOnServer
    public void testBrokerUserSessions(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session = s.sessions()
                    .createUserSession(
                            realm, testuser, "testuser", "127.0.0.1", "test", false, "brokerSession", "brokerUserId");

            UserSessionModel currentSession = s.sessions().getUserSessionByBrokerSessionId(realm, "brokerSession");
            assertThat(currentSession.getBrokerSessionId(), is("brokerSession"));
            assertThat(currentSession.getBrokerUserId(), is("brokerUserId"));

            List<UserSessionModel> brokerSessions = s.sessions()
                    .getUserSessionByBrokerUserIdStream(realm, "brokerUserId")
                    .collect(Collectors.toList());
            assertThat(brokerSessions, hasSize(1));
            assertThat(brokerSessions.get(0).getBrokerSessionId(), is("brokerSession"));
            assertThat(brokerSessions.get(0).getBrokerUserId(), is("brokerUserId"));

            UserSessionModel sessionByPredicate = s.sessions()
                    .getUserSessionWithPredicate(realm, session.getId(), false, s2 -> s2.getBrokerUserId()
                            .equals("brokerUserId"));
            assertThat(sessionByPredicate.getBrokerSessionId(), is("brokerSession"));
            assertThat(sessionByPredicate.getBrokerUserId(), is("brokerUserId"));

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserModel testuser = s.users().getUserByUsername(realm, "user1");
            UserSessionModel session = s.sessions()
                    .createUserSession(
                            realm, testuser, "testuser", "127.0.0.1", "test", false, "brokerSession", "brokerUserId");
            s.sessions().createOfflineUserSession(session);

            List<UserSessionModel> brokerSessions = s.sessions()
                    .getOfflineUserSessionByBrokerUserIdStream(realm, "brokerUserId")
                    .collect(Collectors.toList());
            assertThat(brokerSessions, hasSize(1));
            assertThat(brokerSessions.get(0).getBrokerSessionId(), is("brokerSession"));
            assertThat(brokerSessions.get(0).getBrokerUserId(), is("brokerUserId"));

            UserSessionModel sessionByPredicate = s.sessions()
                    .getUserSessionWithPredicate(realm, session.getId(), true, s2 -> s2.getBrokerUserId()
                            .equals("brokerUserId"));
            assertThat(sessionByPredicate.getBrokerSessionId(), is("brokerSession"));
            assertThat(sessionByPredicate.getBrokerUserId(), is("brokerUserId"));

            return session.getId();
        });
    }

    @TestOnServer
    public void testActiveClientSessionStats(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName(REALM_NAME);
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);

            ClientModel client1 = realm.getClientByClientId("test-app");
            ClientModel client2 = realm.getClientByClientId("third-party");

            // Create client1 session
            AuthenticatedClientSessionModel clientSession1 =
                    s.sessions().createClientSession(realm, client1, userSession);
            clientSession1.setAction("foo1");
            int currentTime1 = Time.currentTime();
            clientSession1.setTimestamp(currentTime1);

            // Create client2 session
            AuthenticatedClientSessionModel clientSession2 =
                    s.sessions().createClientSession(realm, client2, userSession);
            clientSession2.setAction("foo2");
            int currentTime2 = Time.currentTime();
            clientSession2.setTimestamp(currentTime2);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, r) -> {
            RealmModel realm = s.realms().getRealmByName(REALM_NAME);
            ClientModel client1 = realm.getClientByClientId("test-app");
            ClientModel client2 = realm.getClientByClientId("third-party");

            Map<String, Long> stats = s.sessions().getActiveClientSessionStats(realm, false);
            assertThat(stats.entrySet(), hasSize(2));
            assertThat(stats.get(client1.getId()), is(1L));
            assertThat(stats.get(client2.getId()), is(1L));

            return null;
        });
    }

    @TestOnServer
    public void testRemoveSessions(KeycloakSession testSession) {

        String sessionId = withRealm(testSession, REALM_NAME, (s, realm) -> s.sessions()
                .createUserSession(
                        realm,
                        s.users().getUserByUsername(realm, "user1"),
                        "user1",
                        "127.0.0.2",
                        "form",
                        true,
                        null,
                        null)
                .getId());
        withRealm(testSession, REALM_NAME, (s, realm) -> s.clients().addClient(realm, "clientId"));

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertNotNull(s.sessions().getUserSession(realm, sessionId));
            s.sessions().removeUserSessions(realm, s.users().getUserByUsername(realm, "user1"));

            return null;
        });

        String offlineSessionId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertNull(s.sessions().getUserSession(realm, sessionId));

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            return s.sessions().createOfflineUserSession(userSession).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId);
            assertTrue(offlineUserSession.isOffline());

            s.sessions().removeOfflineUserSession(realm, offlineUserSession);

            return null;
        });

        String offlineSessionId2 = withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId);
            assertFalse(offlineUserSession.isOffline()); // Returned corresponding live session

            UserSessionModel userSession = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            return s.sessions().createOfflineUserSession(userSession).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId2);
            assertTrue(offlineUserSession.isOffline());

            s.sessions()
                    .removeOfflineUserSession(
                            realm,
                            s.sessions()
                                    .getUserSession(
                                            realm,
                                            offlineUserSession.getNote(UserSessionModel.CORRESPONDING_SESSION_ID)));

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserSessionModel offlineUserSession = s.sessions().getOfflineUserSession(realm, offlineSessionId2);
            assertFalse(offlineUserSession.isOffline()); // Returned corresponding live session

            return null;
        });
    }

    @TestOnServer
    public void testImportUserSessions(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> s.clients().addClient(realm, "clientId"));
        UserSessionModel userSession1 = withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserSessionModel model = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            s.sessions().createClientSession(realm, s.clients().getClientByClientId(realm, "clientId"), model);

            return model;
        });

        UserSessionModel userSession2 = withRealm(testSession, REALM_NAME, (s, realm) -> {
            UserSessionModel model = s.sessions()
                    .createUserSession(
                            realm,
                            s.users().getUserByUsername(realm, "user2"),
                            "user2",
                            "127.0.0.2",
                            "form",
                            true,
                            null,
                            null);
            s.sessions().createClientSession(realm, s.clients().getClientByClientId(realm, "clientId"), model);

            return model;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            s.sessions().removeUserSessions(realm);
            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList()),
                    hasSize(0));
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user2"))
                            .collect(Collectors.toList()),
                    hasSize(0));

            s.sessions().importUserSessions(Arrays.asList(userSession1, userSession2), false);
            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList()),
                    hasSize(1));
            assertThat(
                    s.sessions()
                            .getUserSessionsStream(realm, s.users().getUserByUsername(realm, "user2"))
                            .collect(Collectors.toList()),
                    hasSize(1));
            Map<String, Long> stats = s.sessions().getActiveClientSessionStats(realm, false);
            assertThat(
                    stats.get(s.clients().getClientByClientId(realm, "clientId").getId()), is(2L));

            return null;
        });
    }

    public static class UserSessionProviderRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            return configureSessionRealm(realm);
        }
    }
}
