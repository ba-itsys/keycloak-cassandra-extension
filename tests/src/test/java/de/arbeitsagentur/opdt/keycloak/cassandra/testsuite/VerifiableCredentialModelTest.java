/*
 * Copyright 2026 IT-Systemhaus der Bundesagentur fuer Arbeit
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
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.hamcrest.MatcherAssert.assertThat;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.HashMap;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.common.util.Time;
import org.keycloak.models.IssuedVerifiableCredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelException;
import org.keycloak.models.UserVerifiableCredentialModel;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class VerifiableCredentialModelTest extends CassandraModelTest {

    private static final String REALM_NAME = "vc";
    private static final String USER_ID = "vc-user";
    private static final String CLIENT_SCOPE_ID = "vc-scope-1";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = VcRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testUserVerifiableCredentialCrud(KeycloakSession testSession) {
        HashMap<String, List<String>> attributes = new HashMap<>();
        attributes.put("credentialSubject", List.of("value"));

        String id = withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel model = new UserVerifiableCredentialModel(null, CLIENT_SCOPE_ID);
            model.setUserAttributes(attributes);
            UserVerifiableCredentialModel stored = session.users().addVerifiableCredential(USER_ID, model);
            assertThat(stored.getId(), Matchers.notNullValue());
            Assert.assertEquals(CLIENT_SCOPE_ID, stored.getClientScopeId());
            assertThat(stored.getRevision(), Matchers.notNullValue());
            return stored.getId();
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel byId = session.users().getVerifiableCredentialById(id);
            Assert.assertNotNull(byId);
            Assert.assertEquals(CLIENT_SCOPE_ID, byId.getClientScopeId());
            Assert.assertEquals(attributes, byId.getUserAttributes());
            UserVerifiableCredentialModel byScope =
                    session.users().getVerifiableCredentialByClientScope(USER_ID, CLIENT_SCOPE_ID);
            Assert.assertNotNull(byScope);
            Assert.assertEquals(id, byScope.getId());
            List<UserVerifiableCredentialModel> byUser =
                    session.users().getVerifiableCredentialsByUser(USER_ID).toList();
            assertThat(byUser, Matchers.hasSize(1));
            return null;
        });

        String originalRevision = withRealm(testSession, REALM_NAME, (session, realm) -> session.users()
                .getVerifiableCredentialById(id)
                .getRevision());

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel updated =
                    session.users().updateVerifiableCredential(USER_ID, CLIENT_SCOPE_ID);
            Assert.assertNotEquals(originalRevision, updated.getRevision());
            Assert.assertNotNull(updated.getUpdatedDate());
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertTrue(session.users().removeVerifiableCredential(USER_ID, CLIENT_SCOPE_ID));
            Assert.assertNull(session.users().getVerifiableCredentialById(id));
            assertThat(session.users().getVerifiableCredentialsByUser(USER_ID).toList(), Matchers.empty());
            return null;
        });
    }

    @TestOnServer
    public void testAddVerifiableCredentialRequiresClientScope(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel model = new UserVerifiableCredentialModel(null, null);
            Assert.assertThrows(ModelException.class, () -> session.users().addVerifiableCredential(USER_ID, model));
            return null;
        });
    }

    @TestOnServer
    public void testAddVerifiableCredentialRejectsDuplicate(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel model = new UserVerifiableCredentialModel(null, CLIENT_SCOPE_ID);
            model.setUserAttributes(new HashMap<>());
            session.users().addVerifiableCredential(USER_ID, model);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel duplicate = new UserVerifiableCredentialModel(null, CLIENT_SCOPE_ID);
            duplicate.setUserAttributes(new HashMap<>());
            Assert.assertThrows(
                    ModelException.class, () -> session.users().addVerifiableCredential(USER_ID, duplicate));
            return null;
        });
    }

    @TestOnServer
    public void testIssuedVerifiableCredentials(KeycloakSession testSession) {
        String vcId = withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel model = new UserVerifiableCredentialModel(null, CLIENT_SCOPE_ID);
            model.setUserAttributes(new HashMap<>());
            return session.users().addVerifiableCredential(USER_ID, model).getId();
        });

        String issuedId = withRealm(testSession, REALM_NAME, (session, realm) -> {
            IssuedVerifiableCredentialModel issued = new IssuedVerifiableCredentialModel(USER_ID, vcId, "vc-client");
            IssuedVerifiableCredentialModel saved = session.users().addIssuedVerifiableCredential(issued);
            assertThat(saved.getId(), Matchers.notNullValue());
            assertThat(saved.getIssuedAt(), Matchers.notNullValue());
            assertThat(saved.getRevision(), Matchers.notNullValue());
            return saved.getId();
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            List<IssuedVerifiableCredentialModel> issued = session.users()
                    .getIssuedVerifiableCredentialsStreamByUser(USER_ID)
                    .toList();
            assertThat(
                    issued.stream().map(IssuedVerifiableCredentialModel::getId).toList(), Matchers.contains(issuedId));
            Assert.assertTrue(session.users().removeIssuedVerifiableCredential(issuedId));
            assertThat(
                    session.users()
                            .getIssuedVerifiableCredentialsStreamByUser(USER_ID)
                            .toList(),
                    Matchers.empty());
            return null;
        });
    }

    @TestOnServer
    public void testRemoveVerifiableCredentialCascadesToIssued(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel model = new UserVerifiableCredentialModel(null, CLIENT_SCOPE_ID);
            model.setUserAttributes(new HashMap<>());
            String id = session.users().addVerifiableCredential(USER_ID, model).getId();
            session.users()
                    .addIssuedVerifiableCredential(new IssuedVerifiableCredentialModel(USER_ID, id, "vc-client"));
            return id;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(
                    session.users()
                            .getIssuedVerifiableCredentialsStreamByUser(USER_ID)
                            .toList(),
                    Matchers.hasSize(1));
            Assert.assertTrue(session.users().removeVerifiableCredential(USER_ID, CLIENT_SCOPE_ID));
            assertThat(
                    session.users()
                            .getIssuedVerifiableCredentialsStreamByUser(USER_ID)
                            .toList(),
                    Matchers.empty());
            return null;
        });
    }

    @TestOnServer
    public void testIssuedVerifiableCredentialExpiry(KeycloakSession testSession) {
        String vcId = withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserVerifiableCredentialModel model = new UserVerifiableCredentialModel(null, CLIENT_SCOPE_ID);
            model.setUserAttributes(new HashMap<>());
            return session.users().addVerifiableCredential(USER_ID, model).getId();
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            IssuedVerifiableCredentialModel issued = new IssuedVerifiableCredentialModel(USER_ID, vcId, "vc-client");
            issued.setExpiresAt(Time.currentTimeMillis() + 3000L);
            session.users().addIssuedVerifiableCredential(issued);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> session.users()
                .getIssuedVerifiableCredentialsStreamByUser(USER_ID)
                .toList());

        sleep(5000);

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(
                    session.users()
                            .getIssuedVerifiableCredentialsStreamByUser(USER_ID)
                            .toList(),
                    Matchers.empty());
            return null;
        });
    }

    private static void sleep(int waitTimeMs) {
        try {
            Thread.sleep(waitTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public static class VcRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.users(UserBuilder.create().username(USER_ID).id(USER_ID).email("vc-user@example.com"));
            return realm;
        }
    }
}
