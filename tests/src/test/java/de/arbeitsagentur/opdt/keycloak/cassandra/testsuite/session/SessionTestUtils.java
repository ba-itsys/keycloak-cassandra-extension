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

import static org.junit.Assert.*;
import static org.keycloak.models.Constants.SESSION_NOTE_LIGHTWEIGHT_USER;

import java.util.*;
import org.junit.Assert;
import org.keycloak.models.*;
import org.keycloak.models.light.LightweightUserAdapter;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.testframework.realm.RealmConfigBuilder;

public class SessionTestUtils {
    public static RealmConfigBuilder configureSessionRealm(RealmConfigBuilder realm) {
        realm.ssoSessionIdleTimeout(1800)
                .ssoSessionMaxLifespan(36000)
                .clientSessionIdleTimeout(500)
                .update(rep -> rep.setOfflineSessionIdleTimeout(Constants.DEFAULT_OFFLINE_SESSION_IDLE_TIMEOUT));
        realm.addUser("user1").email("user1@localhost");
        realm.addUser("user2").email("user2@localhost");
        return configureSessionClients(realm);
    }

    public static RealmConfigBuilder configureSessionClients(RealmConfigBuilder realm) {
        realm.addClient("test-app");
        realm.addClient("third-party").consentRequired(true);
        return realm;
    }

    public static UserSessionModel[] createSessionsTransientUser(KeycloakSession session, String realmId) {
        RealmModel realm = session.realms().getRealm(realmId);
        UserSessionModel[] sessions = new UserSessionModel[3];

        LightweightUserAdapter lightweightUserAdapter1 = new LightweightUserAdapter(session, null);
        lightweightUserAdapter1.setUsername("user1");

        sessions[0] = session.sessions()
                .createUserSession(realm, lightweightUserAdapter1, "user1", "127.0.0.1", "form", true, null, null);
        sessions[0].setNote(SESSION_NOTE_LIGHTWEIGHT_USER, lightweightUserAdapter1.serialize());

        createClientSession(
                session, realmId, realm.getClientByClientId("test-app"), sessions[0], "http://redirect", "state");
        createClientSession(
                session, realmId, realm.getClientByClientId("third-party"), sessions[0], "http://redirect", "state");

        sessions[1] = session.sessions()
                .createUserSession(realm, lightweightUserAdapter1, "user1", "127.0.0.2", "form", true, null, null);
        sessions[1].setNote(SESSION_NOTE_LIGHTWEIGHT_USER, lightweightUserAdapter1.serialize());
        createClientSession(
                session, realmId, realm.getClientByClientId("test-app"), sessions[1], "http://redirect", "state");

        LightweightUserAdapter lightweightUserAdapter2 = new LightweightUserAdapter(session, null);
        lightweightUserAdapter2.setUsername("user2");

        sessions[2] = session.sessions()
                .createUserSession(realm, lightweightUserAdapter2, "user2", "127.0.0.3", "form", true, null, null);
        sessions[2].setNote(SESSION_NOTE_LIGHTWEIGHT_USER, lightweightUserAdapter2.serialize());
        createClientSession(
                session, realmId, realm.getClientByClientId("test-app"), sessions[2], "http://redirect", "state");

        return sessions;
    }

    public static UserSessionModel[] createSessions(KeycloakSession session, String realmId) {
        RealmModel realm = session.realms().getRealm(realmId);
        UserSessionModel[] sessions = new UserSessionModel[3];
        sessions[0] = session.sessions()
                .createUserSession(
                        realm,
                        session.users().getUserByUsername(realm, "user1"),
                        "user1",
                        "127.0.0.1",
                        "form",
                        true,
                        null,
                        null);

        createClientSession(
                session, realmId, realm.getClientByClientId("test-app"), sessions[0], "http://redirect", "state");
        createClientSession(
                session, realmId, realm.getClientByClientId("third-party"), sessions[0], "http://redirect", "state");

        sessions[1] = session.sessions()
                .createUserSession(
                        realm,
                        session.users().getUserByUsername(realm, "user1"),
                        "user1",
                        "127.0.0.2",
                        "form",
                        true,
                        null,
                        null);
        createClientSession(
                session, realmId, realm.getClientByClientId("test-app"), sessions[1], "http://redirect", "state");

        sessions[2] = session.sessions()
                .createUserSession(
                        realm,
                        session.users().getUserByUsername(realm, "user2"),
                        "user2",
                        "127.0.0.3",
                        "form",
                        true,
                        null,
                        null);
        createClientSession(
                session, realmId, realm.getClientByClientId("test-app"), sessions[2], "http://redirect", "state");

        return sessions;
    }

    public static AuthenticatedClientSessionModel createClientSession(
            KeycloakSession session,
            String realmId,
            ClientModel client,
            UserSessionModel userSession,
            String redirect,
            String state) {
        RealmModel realm = session.realms().getRealm(realmId);
        AuthenticatedClientSessionModel clientSession =
                session.sessions().createClientSession(realm, client, userSession);
        clientSession.setRedirectUri(redirect);
        if (state != null) clientSession.setNote(OIDCLoginProtocol.STATE_PARAM, state);
        return clientSession;
    }

    public static void assertSessions(List<UserSessionModel> actualSessions, UserSessionModel... expectedSessions) {
        String[] expected = new String[expectedSessions.length];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = expectedSessions[i].getId();
        }

        String[] actual = new String[actualSessions.size()];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = actualSessions.get(i).getId();
        }

        Arrays.sort(expected);
        Arrays.sort(actual);

        assertArrayEquals(expected, actual);
    }

    public static void assertSessionLightweightUser(
            UserSessionModel session,
            String username,
            String ipAddress,
            int started,
            int lastRefresh,
            String... clients) {
        assertEquals(username, session.getUser().getUsername());
        assertEquals(ipAddress, session.getIpAddress());
        assertEquals(username, session.getLoginUsername());
        assertEquals("form", session.getAuthMethod());
        assertTrue(session.isRememberMe());
        assertTrue(session.getStarted() >= started - 1 && session.getStarted() <= started + 200);
        assertTrue(session.getLastSessionRefresh() >= lastRefresh - 1
                && session.getLastSessionRefresh() <= lastRefresh + 200);

        String[] actualClients =
                new String[session.getAuthenticatedClientSessions().size()];
        int i = 0;
        for (Map.Entry<String, AuthenticatedClientSessionModel> entry :
                session.getAuthenticatedClientSessions().entrySet()) {
            String clientUUID = entry.getKey();
            AuthenticatedClientSessionModel clientSession = entry.getValue();
            Assert.assertEquals(clientUUID, clientSession.getClient().getId());
            actualClients[i] = clientSession.getClient().getClientId();
            i++;
        }

        Arrays.sort(clients);
        Arrays.sort(actualClients);

        assertArrayEquals(clients, actualClients);
    }

    public static void assertSession(
            UserSessionModel session,
            UserModel user,
            String ipAddress,
            int started,
            int lastRefresh,
            String... clients) {
        assertEquals(user.getId(), session.getUser().getId());
        assertEquals(ipAddress, session.getIpAddress());
        assertEquals(user.getUsername(), session.getLoginUsername());
        assertEquals("form", session.getAuthMethod());
        assertTrue(session.isRememberMe());
        assertTrue(session.getStarted() >= started - 1 && session.getStarted() <= started + 200);
        assertTrue(session.getLastSessionRefresh() >= lastRefresh - 1
                && session.getLastSessionRefresh() <= lastRefresh + 200);

        String[] actualClients =
                new String[session.getAuthenticatedClientSessions().size()];
        int i = 0;
        for (Map.Entry<String, AuthenticatedClientSessionModel> entry :
                session.getAuthenticatedClientSessions().entrySet()) {
            String clientUUID = entry.getKey();
            AuthenticatedClientSessionModel clientSession = entry.getValue();
            Assert.assertEquals(clientUUID, clientSession.getClient().getId());
            actualClients[i] = clientSession.getClient().getClientId();
            i++;
        }

        Arrays.sort(clients);
        Arrays.sort(actualClients);

        assertArrayEquals(clients, actualClients);
    }
}
