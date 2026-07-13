/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionAdapter;
import java.util.stream.Collectors;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserSessionExpirationTest extends CassandraModelTest {
    private static final String REALM_NAME = "user-session-expiration";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = UserSessionExpirationRealmConfig.class)
    ManagedRealm managedRealm;

    private static void sleep(int waitTimeMs) {
        try {
            Thread.sleep(waitTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    @TestOnServer
    public void testClientSessionIdleTimeout(KeycloakSession testSession) {
        try {
            // Set low ClientSessionIdleTimeout
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                realm.setSsoSessionIdleTimeout(1800);
                realm.setSsoSessionMaxLifespan(36000);
                realm.setClientSessionIdleTimeout(5);
                return null;
            });

            String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                    .createUserSession(
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null)
                    .getId());

            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(3);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(5);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    nullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(0));
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testClientSessionIdleTimeoutOverride(KeycloakSession testSession) {
        try {
            // Set low ClientSessionIdleTimeout
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                realm.setSsoSessionIdleTimeout(1800);
                realm.setSsoSessionMaxLifespan(36000);
                realm.setClientSessionIdleTimeout(10);
                return null;
            });

            String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                    .createUserSession(
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null)
                    .getId());

            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(3);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            withRealm(testSession, REALM_NAME, (session, realm) -> {
                session.sessions()
                        .getUserSession(realm, uSId)
                        .setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "5");
                return null;
            });

            Time.setOffset(6);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(8);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    nullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(0));
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testClientSessionIdleTimeoutOverrideGreaterThanOldValue(KeycloakSession testSession) {
        try {
            // Set low ClientSessionIdleTimeout
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                realm.setSsoSessionIdleTimeout(1800);
                realm.setSsoSessionMaxLifespan(36000);
                realm.setClientSessionIdleTimeout(10);
                return null;
            });

            String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                    .createUserSession(
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null)
                    .getId());

            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(3);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            withRealm(testSession, REALM_NAME, (session, realm) -> {
                session.sessions()
                        .getUserSession(realm, uSId)
                        .setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "5");
                return null;
            });

            // Should do nothing, since new override is greater than old override
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                session.sessions()
                        .getUserSession(realm, uSId)
                        .setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "7");
                return null;
            });

            Time.setOffset(6);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(8);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    nullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(0));
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testClientSessionIdleTimeoutOverrideGreaterThanOldRealmValue(KeycloakSession testSession) {
        try {
            // Set low ClientSessionIdleTimeout
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                realm.setSsoSessionIdleTimeout(1800);
                realm.setSsoSessionMaxLifespan(36000);
                realm.setClientSessionIdleTimeout(5);
                return null;
            });

            String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                    .createUserSession(
                            realm,
                            session.users().getUserByUsername(realm, "user1"),
                            "user1",
                            "127.0.0.1",
                            "form",
                            true,
                            null,
                            null)
                    .getId());

            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(3);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            // Should do nothing, since new override is greater than old override
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                session.sessions()
                        .getUserSession(realm, uSId)
                        .setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "7");
                return null;
            });

            Time.setOffset(6);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    notNullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(1));

            Time.setOffset(8);
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSession(realm, uSId)),
                    nullValue());
            assertThat(
                    withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                            .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                            .collect(Collectors.toList())),
                    hasSize(0));
        } finally {
            Time.setOffset(0);
        }
    }

    @TestOnServer
    public void testClientSessionIdleTimeoutOverrideTtl(KeycloakSession testSession) throws InterruptedException {

        // Set low ClientSessionIdleTimeout
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(10);
            return null;
        });

        String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                .createUserSession(
                        realm,
                        session.users().getUserByUsername(realm, "user1"),
                        "user1",
                        "127.0.0.1",
                        "form",
                        true,
                        null,
                        null)
                .getId());

        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSession(realm, uSId)),
                notNullValue());
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                        .collect(Collectors.toList())),
                hasSize(1));

        sleep(3000);
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSession(realm, uSId)),
                notNullValue());
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                        .collect(Collectors.toList())),
                hasSize(1));

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.sessions()
                    .getUserSession(realm, uSId)
                    .setNote(CassandraUserSessionAdapter.CLIENT_IDLE_TIMEOUT_OVERRIDE_ATTRIBUTE, "5");
            return null;
        });

        sleep(3000);
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSession(realm, uSId)),
                notNullValue());
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                        .collect(Collectors.toList())),
                hasSize(1));

        sleep(2000);
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSession(realm, uSId)),
                nullValue());
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                        .collect(Collectors.toList())),
                hasSize(0));
    }

    @TestOnServer
    public void testDeleteSession(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            realm.setSsoSessionIdleTimeout(1800);
            realm.setSsoSessionMaxLifespan(36000);
            realm.setClientSessionIdleTimeout(5);
            return null;
        });

        String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                .createUserSession(
                        realm,
                        session.users().getUserByUsername(realm, "user1"),
                        "user1",
                        "127.0.0.1",
                        "form",
                        true,
                        null,
                        null)
                .getId());

        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSession(realm, uSId)),
                notNullValue());
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                        .collect(Collectors.toList())),
                hasSize(1));

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);
            session.sessions().removeUserSession(realm, userSession);
            return null;
        });

        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSession(realm, uSId)),
                nullValue());
        assertThat(
                withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .getUserSessionsStream(realm, session.users().getUserByUsername(realm, "user1"))
                        .collect(Collectors.toList())),
                hasSize(0));
    }

    public static class UserSessionExpirationRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.users(
                    UserBuilder.create().username("user1").email("user1@localhost"),
                    UserBuilder.create().username("user2").email("user2@localhost"));
            return realm;
        }
    }
}
