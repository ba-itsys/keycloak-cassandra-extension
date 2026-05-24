/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.services.managers.UserSessionManager;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

/**
 * @author hmlnarik
 */
@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class OfflineSessionPersistenceTest extends CassandraModelTest {
    private static final String TEST_APP_CLIENT_ID = "test-app";
    private static final String THIRD_PARTY_CLIENT_ID = "third-party";
    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";

    @InjectRealm(
            ref = OfflineSessionRealmConfig.REALM_NAME,
            lifecycle = LifeCycle.METHOD,
            config = OfflineSessionRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testOfflineSessionsCrud(KeycloakSession session) {
        Map<String, Set<String>> offlineSessions = new HashMap<>();

        inCommittedTransaction(session, OfflineSessionPersistenceTest::createSessions);

        inCommittedTransaction(session, currentSession -> {
            RealmModel realm = currentSession.realms().getRealmByName(OfflineSessionRealmConfig.REALM_NAME);
            ClientModel testApp = realm.getClientByClientId(TEST_APP_CLIENT_ID);

            currentSession
                    .sessions()
                    .readOnlyStreamUserSessions(realm, testApp, -1, -1)
                    .map(userSession -> currentSession.sessions().getUserSession(realm, userSession.getId()))
                    .toList()
                    .forEach(userSession -> offlineSessions.put(
                            userSession.getId(),
                            createOfflineSessionIncludingClientSessions(currentSession, userSession)));
        });

        assertEquals(3, offlineSessions.size());

        inCommittedTransaction(session, currentSession -> {
            RealmModel realm = currentSession.realms().getRealmByName(OfflineSessionRealmConfig.REALM_NAME);
            UserSessionManager sessionManager = new UserSessionManager(currentSession);

            for (Map.Entry<String, Set<String>> entry : offlineSessions.entrySet()) {
                UserSessionModel offlineSession = sessionManager.findOfflineUserSession(realm, entry.getKey());
                assertNotNull(offlineSession);
                assertEquals(
                        entry.getValue(),
                        offlineSession.getAuthenticatedClientSessions().keySet());
            }

            UserModel user1 = currentSession.users().getUserByUsername(realm, USER_1);
            Set<ClientModel> user1Clients = sessionManager.findClientsWithOfflineToken(realm, user1);
            assertEquals(2, user1Clients.size());
            assertTrue(user1Clients.stream()
                    .allMatch(client -> client.getClientId().equals(TEST_APP_CLIENT_ID)
                            || client.getClientId().equals(THIRD_PARTY_CLIENT_ID)));

            UserModel user2 = currentSession.users().getUserByUsername(realm, USER_2);
            Set<ClientModel> user2Clients = sessionManager.findClientsWithOfflineToken(realm, user2);
            assertEquals(1, user2Clients.size());
            assertEquals(TEST_APP_CLIENT_ID, user2Clients.iterator().next().getClientId());

            ClientModel testApp = realm.getClientByClientId(TEST_APP_CLIENT_ID);
            ClientModel thirdParty = realm.getClientByClientId(THIRD_PARTY_CLIENT_ID);
            assertEquals(3, currentSession.sessions().getOfflineSessionsCount(realm, testApp));
            assertEquals(1, currentSession.sessions().getOfflineSessionsCount(realm, thirdParty));

            sessionManager.revokeOfflineToken(user1, testApp);
        });

        inCommittedTransaction(session, currentSession -> {
            RealmModel realm = currentSession.realms().getRealmByName(OfflineSessionRealmConfig.REALM_NAME);
            UserSessionManager sessionManager = new UserSessionManager(currentSession);
            ClientModel thirdParty = realm.getClientByClientId(THIRD_PARTY_CLIENT_ID);

            List<UserSessionModel> thirdPartySessions = currentSession
                    .sessions()
                    .readOnlyStreamOfflineUserSessions(realm, thirdParty, 0, 10)
                    .toList();
            assertEquals(1, thirdPartySessions.size());
            assertEquals("127.0.0.1", thirdPartySessions.get(0).getIpAddress());
            assertEquals(USER_1, thirdPartySessions.get(0).getUser().getUsername());

            UserModel user1 = currentSession.users().getUserByUsername(realm, USER_1);
            Set<ClientModel> user1Clients = sessionManager.findClientsWithOfflineToken(realm, user1);
            assertEquals(1, user1Clients.size());
            assertEquals(THIRD_PARTY_CLIENT_ID, user1Clients.iterator().next().getClientId());

            UserModel user2 = currentSession.users().getUserByUsername(realm, USER_2);
            Set<ClientModel> user2Clients = sessionManager.findClientsWithOfflineToken(realm, user2);
            assertEquals(1, user2Clients.size());
            assertEquals(TEST_APP_CLIENT_ID, user2Clients.iterator().next().getClientId());

            sessionManager.revokeOfflineToken(user1, thirdParty);
        });

        inCommittedTransaction(session, currentSession -> {
            RealmModel realm = currentSession.realms().getRealmByName(OfflineSessionRealmConfig.REALM_NAME);
            UserSessionManager sessionManager = new UserSessionManager(currentSession);
            ClientModel testApp = realm.getClientByClientId(TEST_APP_CLIENT_ID);
            ClientModel thirdParty = realm.getClientByClientId(THIRD_PARTY_CLIENT_ID);

            assertEquals(1, currentSession.sessions().getOfflineSessionsCount(realm, testApp));
            assertEquals(0, currentSession.sessions().getOfflineSessionsCount(realm, thirdParty));

            List<UserSessionModel> testAppSessions = currentSession
                    .sessions()
                    .readOnlyStreamOfflineUserSessions(realm, testApp, 0, 10)
                    .toList();
            assertEquals(1, testAppSessions.size());
            assertEquals("127.0.0.3", testAppSessions.get(0).getIpAddress());
            assertEquals(USER_2, testAppSessions.get(0).getUser().getUsername());

            UserModel user1 = currentSession.users().getUserByUsername(realm, USER_1);
            assertEquals(
                    0, sessionManager.findClientsWithOfflineToken(realm, user1).size());
        });
    }

    private static Set<String> createOfflineSessionIncludingClientSessions(
            KeycloakSession session, UserSessionModel userSession) {
        Set<String> offlineClientIds = new HashSet<>();
        UserSessionManager sessionManager = new UserSessionManager(session);
        for (AuthenticatedClientSessionModel clientSession :
                userSession.getAuthenticatedClientSessions().values()) {
            sessionManager.createOrUpdateOfflineSession(clientSession, userSession);
            offlineClientIds.add(clientSession.getClient().getId());
        }
        return offlineClientIds;
    }

    private static UserSessionModel[] createSessions(KeycloakSession session) {
        RealmModel realm = session.realms().getRealmByName(OfflineSessionRealmConfig.REALM_NAME);

        UserSessionModel[] sessions = new UserSessionModel[3];
        sessions[0] = createUserSession(session, realm, USER_1, "127.0.0.1");
        createClientSession(session, realm.getClientByClientId(TEST_APP_CLIENT_ID), sessions[0]);
        createClientSession(session, realm.getClientByClientId(THIRD_PARTY_CLIENT_ID), sessions[0]);

        sessions[1] = createUserSession(session, realm, USER_1, "127.0.0.2");
        createClientSession(session, realm.getClientByClientId(TEST_APP_CLIENT_ID), sessions[1]);

        sessions[2] = createUserSession(session, realm, USER_2, "127.0.0.3");
        createClientSession(session, realm.getClientByClientId(TEST_APP_CLIENT_ID), sessions[2]);

        return sessions;
    }

    private static UserSessionModel createUserSession(
            KeycloakSession session, RealmModel realm, String username, String ipAddress) {
        return session.sessions()
                .createUserSession(
                        null,
                        realm,
                        session.users().getUserByUsername(realm, username),
                        username,
                        ipAddress,
                        "form",
                        true,
                        null,
                        null,
                        UserSessionModel.SessionPersistenceState.PERSISTENT);
    }

    private static void createClientSession(KeycloakSession session, ClientModel client, UserSessionModel userSession) {
        AuthenticatedClientSessionModel clientSession =
                session.sessions().createClientSession(client.getRealm(), client, userSession);
        clientSession.setRedirectUri("http://redirect");
        clientSession.setNote(OIDCLoginProtocol.STATE_PARAM, "state");
    }

    public static class OfflineSessionRealmConfig implements RealmConfig {
        private static final String REALM_NAME = "offline-session";

        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            realm.name(REALM_NAME)
                    .ssoSessionMaxLifespan(10 * 60 * 60)
                    .ssoSessionIdleTimeout(60 * 60)
                    .update(rep -> {
                        rep.setOfflineSessionMaxLifespan(365 * 24 * 60 * 60);
                        rep.setOfflineSessionIdleTimeout(30 * 24 * 60 * 60);
                    });
            realm.addClient(TEST_APP_CLIENT_ID);
            realm.addClient(THIRD_PARTY_CLIENT_ID);
            realm.addUser(USER_1).email("user1@localhost");
            realm.addUser(USER_2).email("user2@localhost");
            return realm;
        }
    }
}
