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

import static de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session.SessionTestUtils.configureSessionClients;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertNull;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraTransientUsersKeycloakServerConfig;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.light.LightweightUserAdapter;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.CommonClientSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraTransientUsersKeycloakServerConfig.class)
public class AuthenticationSessionTest extends CassandraModelTest {
    private static final String REALM_NAME = "authentication-session";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = AuthenticationSessionRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testLimitAuthSessions(KeycloakSession testSession) {
        try {
            AtomicReference<String> rootAuthSessionId = new AtomicReference<>();
            List<String> tabIds = withRealm(testSession, REALM_NAME, (session, realm) -> {
                RootAuthenticationSessionModel ras =
                        session.authenticationSessions().createRootAuthenticationSession(realm);
                rootAuthSessionId.set(ras.getId());
                ClientModel client = realm.getClientByClientId("test-app");
                return IntStream.range(0, 300)
                        .mapToObj(i -> {
                            Time.setOffset(i);
                            return ras.createAuthenticationSession(client);
                        })
                        .map(AuthenticationSessionModel::getTabId)
                        .collect(Collectors.toList());
            });

            String tabId = withRealm(testSession, REALM_NAME, (session, realm) -> {
                RootAuthenticationSessionModel ras =
                        session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
                ClientModel client = realm.getClientByClientId("test-app");

                // create 301st auth session
                return ras.createAuthenticationSession(client).getTabId();
            });

            withRealm(testSession, REALM_NAME, (session, realm) -> {
                RootAuthenticationSessionModel ras =
                        session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
                ClientModel client = realm.getClientByClientId("test-app");

                assertThat(ras.getAuthenticationSessions(), Matchers.aMapWithSize(300));

                Assert.assertEquals(
                        tabId, ras.getAuthenticationSession(client, tabId).getTabId());
                Assert.assertEquals(
                        ras, ras.getAuthenticationSession(client, tabId).getParentSession());

                // assert the first authentication session was deleted
                assertNull(ras.getAuthenticationSession(client, tabIds.get(0)));

                return null;
            });
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testAuthSessions(KeycloakSession testSession) {

        AtomicReference<String> rootAuthSessionId = new AtomicReference<>();
        List<String> tabIds = withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().createRootAuthenticationSession(realm);
            rootAuthSessionId.set(rootAuthSession.getId());

            ClientModel client = realm.getClientByClientId("test-app");
            return IntStream.range(0, 5)
                    .mapToObj(i -> {
                        AuthenticationSessionModel authSession = rootAuthSession.createAuthenticationSession(client);
                        authSession.setExecutionStatus(
                                "username", AuthenticationSessionModel.ExecutionStatus.ATTEMPTED);
                        authSession.setAuthNote("foo", "bar");
                        authSession.setClientNote("foo", "bar");
                        return authSession;
                    })
                    .map(AuthenticationSessionModel::getTabId)
                    .collect(Collectors.toList());
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNotNull(rootAuthSession);
            Assert.assertEquals(rootAuthSessionId.get(), rootAuthSession.getId());

            ClientModel client = realm.getClientByClientId("test-app");
            tabIds.forEach(tabId -> {
                AuthenticationSessionModel authSession = rootAuthSession.getAuthenticationSession(client, tabId);
                Assert.assertNotNull(authSession);

                Assert.assertEquals(
                        AuthenticationSessionModel.ExecutionStatus.ATTEMPTED,
                        authSession.getExecutionStatus().get("username"));
                Assert.assertEquals("bar", authSession.getAuthNote("foo"));
                Assert.assertEquals("bar", authSession.getClientNote("foo"));
            });

            // remove first two auth sessions
            rootAuthSession.removeAuthenticationSessionByTabId(tabIds.get(0));
            rootAuthSession.removeAuthenticationSessionByTabId(tabIds.get(1));

            // remove non existing session (should not throw)
            rootAuthSession.removeAuthenticationSessionByTabId("not-existing");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNotNull(rootAuthSession);
            Assert.assertEquals(rootAuthSessionId.get(), rootAuthSession.getId());

            assertThat(rootAuthSession.getAuthenticationSessions(), Matchers.aMapWithSize(3));

            assertNull(rootAuthSession.getAuthenticationSessions().get(tabIds.get(0)));
            assertNull(rootAuthSession.getAuthenticationSessions().get(tabIds.get(1)));
            IntStream.range(2, 4)
                    .mapToObj(i -> rootAuthSession.getAuthenticationSessions().get(tabIds.get(i)))
                    .forEach(Assert::assertNotNull);

            session.authenticationSessions().removeRootAuthenticationSession(realm, rootAuthSession);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            assertNull(rootAuthSession);

            return null;
        });
    }

    @TestOnServer
    public void testRemoveExpiredAuthSessions(KeycloakSession testSession) {
        try {
            AtomicReference<String> rootAuthSessionId = new AtomicReference<>();
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                RootAuthenticationSessionModel rootAuthSession =
                        session.authenticationSessions().createRootAuthenticationSession(realm);
                ClientModel client = realm.getClientByClientId("test-app");
                rootAuthSession.createAuthenticationSession(client);
                rootAuthSessionId.set(rootAuthSession.getId());

                return null;
            });

            withRealm(testSession, REALM_NAME, (session, realm) -> {
                RootAuthenticationSessionModel rootAuthSession =
                        session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
                Assert.assertNotNull(rootAuthSession);

                Time.setOffset(1900);

                return null;
            });

            withRealm(testSession, REALM_NAME, (session, realm) -> {
                RootAuthenticationSessionModel rootAuthSession =
                        session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
                assertNull(rootAuthSession);

                return null;
            });
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testRemoveAuthSession(KeycloakSession testSession) {

        AtomicReference<String> rootAuthSessionId = new AtomicReference<>();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().createRootAuthenticationSession(realm);
            ClientModel client = realm.getClientByClientId("test-app");
            rootAuthSession.createAuthenticationSession(client);
            rootAuthSession.createAuthenticationSession(client);
            rootAuthSessionId.set(rootAuthSession.getId());

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNotNull(rootAuthSession);
            assertThat(rootAuthSession.getAuthenticationSessions().values(), hasSize(2));

            String tabId = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next()
                    .getTabId();
            rootAuthSession.removeAuthenticationSessionByTabId(tabId);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNotNull(rootAuthSession);
            assertThat(rootAuthSession.getAuthenticationSessions().values(), hasSize(1));

            String tabId = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next()
                    .getTabId();
            rootAuthSession.removeAuthenticationSessionByTabId(tabId);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNull(rootAuthSession);

            return null;
        });
    }

    @TestOnServer
    public void testRemoveRootAuthSession(KeycloakSession testSession) {

        AtomicReference<String> rootAuthSessionId = new AtomicReference<>();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().createRootAuthenticationSession(realm);
            ClientModel client = realm.getClientByClientId("test-app");
            rootAuthSession.createAuthenticationSession(client);
            rootAuthSessionId.set(rootAuthSession.getId());

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNotNull(rootAuthSession);

            session.authenticationSessions().removeRootAuthenticationSession(realm, rootAuthSession);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootAuthSessionId.get());
            Assert.assertNull(rootAuthSession);

            return null;
        });
    }

    @TestOnServer
    public void testAuthSessionProperties(KeycloakSession testSession) {

        String rootSessionId = withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "testuser");
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().createRootAuthenticationSession(realm);
            ClientModel client = realm.getClientByClientId("test-app");
            AuthenticationSessionModel authenticationSession = rootAuthSession.createAuthenticationSession(client);

            authenticationSession.setAuthenticatedUser(user);
            authenticationSession.setUserSessionNote("key1", "value1");
            authenticationSession.setUserSessionNote("key2", "value2");
            authenticationSession.setAuthNote("key1", "val1");
            authenticationSession.setAuthNote("key2", "val2");
            authenticationSession.setClientNote("key1", "val1");
            authenticationSession.setClientNote("key2", "val2");
            authenticationSession.setExecutionStatus("test-auth", CommonClientSessionModel.ExecutionStatus.CHALLENGED);
            authenticationSession.setClientScopes(Set.of("scope1", "scope2"));
            authenticationSession.setAction("act");
            authenticationSession.setProtocol("openid");
            authenticationSession.setRedirectUri("http://localhost:8080");
            authenticationSession.addRequiredAction("VERIFY_EMAIL");
            authenticationSession.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);

            return rootAuthSession.getId();
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("test-app");
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootSessionId);
            assertThat(rootAuthSession.getAuthenticationSessions().entrySet(), hasSize(1));

            AuthenticationSessionModel authSession = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next();
            assertThat(rootAuthSession.getAuthenticationSession(client, authSession.getTabId()), is(authSession));

            assertThat(authSession.getAuthenticatedUser().getUsername(), is("testuser"));
            assertThat(authSession.getAction(), is("act"));
            assertThat(authSession.getProtocol(), is("openid"));
            assertThat(authSession.getRedirectUri(), is("http://localhost:8080"));
            assertThat(authSession.getUserSessionNotes().entrySet(), hasSize(2));
            assertThat(authSession.getUserSessionNotes().get("key1"), is("value1"));
            assertThat(authSession.getUserSessionNotes().get("key2"), is("value2"));
            assertThat(authSession.getAuthNote("key1"), is("val1"));
            assertThat(authSession.getAuthNote("key2"), is("val2"));
            assertThat(authSession.getClientNotes().entrySet(), hasSize(2));
            assertThat(authSession.getClientNote("key1"), is("val1"));
            assertThat(authSession.getClientNote("key2"), is("val2"));
            assertThat(authSession.getExecutionStatus().entrySet(), hasSize(1));
            assertThat(
                    authSession.getExecutionStatus().get("test-auth"),
                    is(CommonClientSessionModel.ExecutionStatus.CHALLENGED));
            assertThat(authSession.getClientScopes(), hasSize(2));
            assertThat(authSession.getClientScopes(), containsInAnyOrder("scope1", "scope2"));
            assertThat(authSession.getRequiredActions(), hasSize(2));
            assertThat(authSession.getRequiredActions(), containsInAnyOrder("VERIFY_EMAIL", "CONFIGURE_TOTP"));

            authSession.removeAuthNote("key1");
            authSession.removeClientNote("key2");
            authSession.removeRequiredAction("CONFIGURE_TOTP");
            authSession.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootSessionId);
            assertThat(rootAuthSession.getAuthenticationSessions().entrySet(), hasSize(1));

            AuthenticationSessionModel authSession = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next();

            assertThat(authSession.getUserSessionNotes().entrySet(), hasSize(2));
            assertThat(authSession.getUserSessionNotes().get("key1"), is("value1"));
            assertThat(authSession.getUserSessionNotes().get("key2"), is("value2"));
            assertNull(authSession.getAuthNote("key1"));
            assertThat(authSession.getAuthNote("key2"), is("val2"));
            assertThat(authSession.getClientNotes().entrySet(), hasSize(1));
            assertThat(authSession.getClientNote("key1"), is("val1"));
            assertThat(authSession.getExecutionStatus().entrySet(), hasSize(1));
            assertThat(
                    authSession.getExecutionStatus().get("test-auth"),
                    is(CommonClientSessionModel.ExecutionStatus.CHALLENGED));
            assertThat(authSession.getRequiredActions(), hasSize(0));

            authSession.clearAuthNotes();
            authSession.clearClientNotes();
            authSession.clearUserSessionNotes();
            authSession.clearExecutionStatus();

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootSessionId);
            assertThat(rootAuthSession.getAuthenticationSessions().entrySet(), hasSize(1));

            AuthenticationSessionModel authSession = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next();

            assertThat(authSession.getUserSessionNotes().entrySet(), hasSize(0));
            assertNull(authSession.getAuthNote("key1"));
            assertNull(authSession.getAuthNote("key2"));
            assertThat(authSession.getClientNotes().entrySet(), hasSize(0));
            assertThat(authSession.getExecutionStatus().entrySet(), hasSize(0));
            assertThat(authSession.getRequiredActions(), hasSize(0));

            return null;
        });
    }

    @TestOnServer
    public void testAuthSessionPropertiesTransientUsers(KeycloakSession testSession) {

        String rootSessionId = withRealm(testSession, REALM_NAME, (session, realm) -> {
            LightweightUserAdapter lightweightUserAdapter = new LightweightUserAdapter(session, null);
            lightweightUserAdapter.setUsername("testuser");

            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().createRootAuthenticationSession(realm);
            ClientModel client = realm.getClientByClientId("test-app");
            AuthenticationSessionModel authenticationSession = rootAuthSession.createAuthenticationSession(client);

            authenticationSession.setAuthenticatedUser(lightweightUserAdapter);
            authenticationSession.setUserSessionNote("key1", "value1");
            authenticationSession.setUserSessionNote("key2", "value2");
            authenticationSession.setAuthNote("key1", "val1");
            authenticationSession.setAuthNote("key2", "val2");
            authenticationSession.setClientNote("key1", "val1");
            authenticationSession.setClientNote("key2", "val2");
            authenticationSession.setExecutionStatus("test-auth", CommonClientSessionModel.ExecutionStatus.CHALLENGED);
            authenticationSession.setClientScopes(Set.of("scope1", "scope2"));
            authenticationSession.setAction("act");
            authenticationSession.setProtocol("openid");
            authenticationSession.setRedirectUri("http://localhost:8080");
            authenticationSession.addRequiredAction("VERIFY_EMAIL");
            authenticationSession.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);

            return rootAuthSession.getId();
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel client = realm.getClientByClientId("test-app");
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootSessionId);
            assertThat(rootAuthSession.getAuthenticationSessions().entrySet(), hasSize(1));

            AuthenticationSessionModel authSession = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next();
            assertThat(rootAuthSession.getAuthenticationSession(client, authSession.getTabId()), is(authSession));

            assertThat(authSession.getAuthenticatedUser().getUsername(), is("testuser"));
            assertThat(authSession.getAction(), is("act"));
            assertThat(authSession.getProtocol(), is("openid"));
            assertThat(authSession.getRedirectUri(), is("http://localhost:8080"));
            assertThat(authSession.getUserSessionNotes().entrySet(), hasSize(3)); // +1 for transient user
            assertThat(authSession.getUserSessionNotes().get("key1"), is("value1"));
            assertThat(authSession.getUserSessionNotes().get("key2"), is("value2"));
            assertThat(authSession.getAuthNote("key1"), is("val1"));
            assertThat(authSession.getAuthNote("key2"), is("val2"));
            assertThat(authSession.getClientNotes().entrySet(), hasSize(2));
            assertThat(authSession.getClientNote("key1"), is("val1"));
            assertThat(authSession.getClientNote("key2"), is("val2"));
            assertThat(authSession.getExecutionStatus().entrySet(), hasSize(1));
            assertThat(
                    authSession.getExecutionStatus().get("test-auth"),
                    is(CommonClientSessionModel.ExecutionStatus.CHALLENGED));
            assertThat(authSession.getClientScopes(), hasSize(2));
            assertThat(authSession.getClientScopes(), containsInAnyOrder("scope1", "scope2"));
            assertThat(authSession.getRequiredActions(), hasSize(2));
            assertThat(authSession.getRequiredActions(), containsInAnyOrder("VERIFY_EMAIL", "CONFIGURE_TOTP"));

            authSession.removeAuthNote("key1");
            authSession.removeClientNote("key2");
            authSession.removeRequiredAction("CONFIGURE_TOTP");
            authSession.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootSessionId);
            assertThat(rootAuthSession.getAuthenticationSessions().entrySet(), hasSize(1));

            AuthenticationSessionModel authSession = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next();

            assertThat(authSession.getUserSessionNotes().entrySet(), hasSize(3)); // +1 for transient user
            assertThat(authSession.getUserSessionNotes().get("key1"), is("value1"));
            assertThat(authSession.getUserSessionNotes().get("key2"), is("value2"));
            assertNull(authSession.getAuthNote("key1"));
            assertThat(authSession.getAuthNote("key2"), is("val2"));
            assertThat(authSession.getClientNotes().entrySet(), hasSize(1));
            assertThat(authSession.getClientNote("key1"), is("val1"));
            assertThat(authSession.getExecutionStatus().entrySet(), hasSize(1));
            assertThat(
                    authSession.getExecutionStatus().get("test-auth"),
                    is(CommonClientSessionModel.ExecutionStatus.CHALLENGED));
            assertThat(authSession.getRequiredActions(), hasSize(0));

            authSession.clearAuthNotes();
            authSession.clearClientNotes();
            authSession.clearUserSessionNotes();
            authSession.clearExecutionStatus();

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RootAuthenticationSessionModel rootAuthSession =
                    session.authenticationSessions().getRootAuthenticationSession(realm, rootSessionId);
            assertThat(rootAuthSession.getAuthenticationSessions().entrySet(), hasSize(1));

            AuthenticationSessionModel authSession = rootAuthSession
                    .getAuthenticationSessions()
                    .values()
                    .iterator()
                    .next();

            assertThat(authSession.getUserSessionNotes().entrySet(), hasSize(0));
            assertNull(authSession.getAuthNote("key1"));
            assertNull(authSession.getAuthNote("key2"));
            assertThat(authSession.getClientNotes().entrySet(), hasSize(0));
            assertThat(authSession.getExecutionStatus().entrySet(), hasSize(0));
            assertThat(authSession.getRequiredActions(), hasSize(0));

            return null;
        });
    }

    public static class AuthenticationSessionRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            realm.update(rep -> rep.setAccessCodeLifespanLogin(1800));
            return configureSessionClients(realm);
        }
    }
}
