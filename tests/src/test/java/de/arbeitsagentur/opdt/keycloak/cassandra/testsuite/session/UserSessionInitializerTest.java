/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
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

import static de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session.SessionTestUtils.configureSessionRealm;
import static de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session.SessionTestUtils.createSessions;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 * @author <a href="mailto:mabartos@redhat.com">Martin Bartos</a>
 * @author <a href="mailto:mkanis@redhat.com">Martin Kanis</a>
 */
@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserSessionInitializerTest extends CassandraModelTest {
    private static final String REALM_NAME = "user-session-initializer";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = UserSessionInitializerRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testUserSessionInitializer(KeycloakSession testSession) {

        UserSessionModel[] origSessionIds = createSessionsInPersisterOnly(testSession, REALM_NAME);
        int started = origSessionIds[0].getStarted();

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            // Assert sessions are in
            ClientModel testApp = realm.getClientByClientId("test-app");
            ClientModel thirdparty = realm.getClientByClientId("third-party");

            assertThat(
                    "Count of offline sesions for client 'test-app'",
                    session.sessions().getOfflineSessionsCount(realm, testApp),
                    is((long) 3));
            assertThat(
                    "Count of offline sesions for client 'third-party'",
                    session.sessions().getOfflineSessionsCount(realm, thirdparty),
                    is((long) 1));

            List<UserSessionModel> loadedSessions = session.sessions()
                    .getOfflineUserSessionsStream(realm, testApp, 0, 10)
                    .collect(Collectors.toList());

            assertSessionLoaded(
                    loadedSessions,
                    origSessionIds[0].getId(),
                    session.users().getUserByUsername(realm, "user1"),
                    "127.0.0.1",
                    started,
                    started,
                    "test-app",
                    "third-party");
            assertSessionLoaded(
                    loadedSessions,
                    origSessionIds[1].getId(),
                    session.users().getUserByUsername(realm, "user1"),
                    "127.0.0.2",
                    started,
                    started,
                    "test-app");
            assertSessionLoaded(
                    loadedSessions,
                    origSessionIds[2].getId(),
                    session.users().getUserByUsername(realm, "user2"),
                    "127.0.0.3",
                    started,
                    started,
                    "test-app");
        });
    }

    @TestOnServer
    public void testUserSessionInitializerWithDeletingClient(KeycloakSession testSession) {

        UserSessionModel[] origSessionIds = createSessionsInPersisterOnly(testSession, REALM_NAME);
        int started = origSessionIds[0].getStarted();

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            // Delete one of the clients now
            ClientModel testApp = realm.getClientByClientId("test-app");
            realm.removeClient(testApp.getId());
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);

            // Assert sessions are in
            ClientModel thirdparty = realm.getClientByClientId("third-party");

            assertThat(
                    "Count of offline sessions for client 'third-party'",
                    session.sessions().getOfflineSessionsCount(realm, thirdparty),
                    is((long) 1));
            List<UserSessionModel> loadedSessions = session.sessions()
                    .getOfflineUserSessionsStream(realm, thirdparty, 0, 10)
                    .collect(Collectors.toList());

            assertThat("Size of loaded Sessions", loadedSessions.size(), is(1));
            assertSessionLoaded(
                    loadedSessions,
                    origSessionIds[0].getId(),
                    session.users().getUserByUsername(realm, "user1"),
                    "127.0.0.1",
                    started,
                    started,
                    "third-party");

            // Revert client
            realm.addClient("test-app");
        });
    }

    // Create sessions in persister + infinispan, but then delete them from infinispan cache by
    // reinitializing keycloak session factory
    private static UserSessionModel[] createSessionsInPersisterOnly(KeycloakSession testSession, String realmName) {
        UserSessionModel[] origSessions = inCommittedTransaction(testSession, session -> {
            return createSessions(session, realmName);
        });
        UserSessionModel[] res = new UserSessionModel[origSessions.length];

        withRealm(testSession, realmName, (session, realm) -> {
            int i = 0;
            for (UserSessionModel origSession : origSessions) {
                UserSessionModel userSession = session.sessions().getUserSession(realm, origSession.getId());
                UserSessionModel offlineUserSession = session.sessions().createOfflineUserSession(userSession);
                userSession.getAuthenticatedClientSessions().values().forEach(clientSession -> session.sessions()
                        .createOfflineClientSession(clientSession, offlineUserSession));

                res[i++] = offlineUserSession;
            }
            return null;
        });

        return res;
    }

    private static void assertSessionLoaded(
            List<UserSessionModel> sessions,
            String id,
            UserModel user,
            String ipAddress,
            int started,
            int lastRefresh,
            String... clients) {
        for (UserSessionModel session : sessions) {
            if (session.getId().equals(id)) {
                assertSession(session, user, ipAddress, started, lastRefresh, clients);
                return;
            }
        }
        Assert.fail("Session with ID " + id + " not found in the list");
    }

    private static void assertSession(
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
        assertTrue(session.getStarted() >= started - 1 && session.getStarted() <= started + 1);
        assertTrue(session.getLastSessionRefresh() >= lastRefresh - 1
                && session.getLastSessionRefresh() <= lastRefresh + 1);

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

        assertThat(actualClients, Matchers.arrayContainingInAnyOrder(clients));
    }

    public static class UserSessionInitializerRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            return configureSessionRealm(realm);
        }
    }
}
