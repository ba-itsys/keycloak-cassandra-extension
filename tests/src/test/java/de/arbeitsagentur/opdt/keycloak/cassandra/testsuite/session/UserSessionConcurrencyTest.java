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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.stream.IntStream;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserSessionConcurrencyTest extends CassandraModelTest {

    private static final int CLIENTS_COUNT = 10;
    private static final String REALM_NAME = "user-session-concurrency";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = UserSessionConcurrencyRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testConcurrentNotesChange(KeycloakSession testSession) throws InterruptedException {

        // Create user session
        String uId = withRealm(testSession, REALM_NAME, (session, realm) -> session.sessions()
                        .createUserSession(
                                realm,
                                session.users().getUserByUsername(realm, "user1"),
                                "user1",
                                "127.0.0.1",
                                "form",
                                true,
                                null,
                                null))
                .getId();

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserSessionModel uSession = session.sessions().getUserSession(realm, uId);
            IntStream.range(0, CLIENTS_COUNT).forEach(i -> {
                ClientModel client = realm.getClientByClientId("client" + i);
                AuthenticatedClientSessionModel cSession =
                        session.sessions().createClientSession(realm, client, uSession);
                cSession.setNote(OIDCLoginProtocol.STATE_PARAM, "initial-" + i);
            });
            return null;
        });

        IntStream.range(0, CLIENTS_COUNT)
                .parallel()
                .forEach(i -> runJobInTransaction(testSession.getKeycloakSessionFactory(), session -> {
                    RealmModel realm = session.realms().getRealmByName(REALM_NAME);
                    ClientModel client = realm.getClientByClientId("client" + i);

                    UserSessionModel uSession = session.sessions().getUserSession(realm, uId);
                    AuthenticatedClientSessionModel cSession =
                            uSession.getAuthenticatedClientSessionByClient(client.getId());
                    cSession.setNote(OIDCLoginProtocol.STATE_PARAM, "state-" + i);
                }));

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserSessionModel uSession = session.sessions().getUserSession(realm, uId);
            assertThat(uSession.getAuthenticatedClientSessions(), aMapWithSize(CLIENTS_COUNT));

            long updatedNotes = 0;
            for (int i = 0; i < CLIENTS_COUNT; i++) {
                ClientModel client = realm.getClientByClientId("client" + (i % CLIENTS_COUNT));
                AuthenticatedClientSessionModel cSession =
                        uSession.getAuthenticatedClientSessionByClient(client.getId());

                String note = cSession.getNote(OIDCLoginProtocol.STATE_PARAM);
                assertThat(note, anyOf(startsWith("initial-"), startsWith("state-")));
                if (note.startsWith("state-")) {
                    updatedNotes++;
                }
            }
            assertThat(updatedNotes, greaterThan(0L));

            return null;
        });
    }

    public static class UserSessionConcurrencyRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            realm.ssoSessionIdleTimeout(1800).ssoSessionMaxLifespan(36000).clientSessionIdleTimeout(500);
            realm.addUser("user1").email("user1@localhost");
            realm.addUser("user2").email("user2@localhost");
            IntStream.range(0, CLIENTS_COUNT).forEach(i -> realm.addClient("client" + i));
            return realm;
        }
    }
}
