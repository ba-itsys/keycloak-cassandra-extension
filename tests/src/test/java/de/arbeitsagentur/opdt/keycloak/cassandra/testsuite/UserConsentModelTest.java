/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.keycloak.models.utils.KeycloakModelUtils.getClientScopeByName;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.keycloak.models.*;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

/**
 * Ported from
 *
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserConsentModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "user-consent";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = UserConsentRealmConfig.class)
    ManagedRealm managedRealm;

    private static void createConsents(KeycloakSession testSession, String realmName) {
        withRealm(testSession, realmName, (session, realm) -> {
            setupConsents(session, realm.getId());
            return null;
        });
    }

    private static void setupConsents(KeycloakSession s, String realmId) {
        RealmModel realm = s.realms().getRealm(realmId);
        ClientModel fooClient = realm.getClientByClientId("foo-client");
        ClientModel barClient = realm.getClientByClientId("bar-client");
        ClientScopeModel fooScope = getClientScopeByName(realm, "foo");
        ClientScopeModel barScope = getClientScopeByName(realm, "bar");
        UserModel john = s.users().getUserByUsername(realm, "john");
        UserModel mary = s.users().getUserByUsername(realm, "mary");
        UserConsentModel johnFooGrant = new UserConsentModel(fooClient);
        johnFooGrant.addGrantedClientScope(fooScope);
        s.users().addConsent(realm, john.getId(), johnFooGrant);

        UserConsentModel johnBarGrant = new UserConsentModel(barClient);
        johnBarGrant.addGrantedClientScope(barScope);
        s.users().addConsent(realm, john.getId(), johnBarGrant);

        UserConsentModel maryFooGrant = new UserConsentModel(fooClient);
        maryFooGrant.addGrantedClientScope(fooScope);
        s.users().addConsent(realm, mary.getId(), maryFooGrant);

        ClientModel hardcodedClient = realm.getClientByClientId("hardcoded-client");
        UserConsentModel maryHardcodedGrant = new UserConsentModel(hardcodedClient);
        s.users().addConsent(realm, mary.getId(), maryHardcodedGrant);
    }

    private static boolean isClientScopeGranted(RealmModel realm, String scopeName, UserConsentModel consentModel) {
        ClientScopeModel clientScope = getClientScopeByName(realm, scopeName);
        return consentModel.isClientScopeGranted(clientScope);
    }

    @TestOnServer
    public void basicConsentTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            ClientModel barClient = realm.getClientByClientId("bar-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            UserModel mary = session.users().getUserByUsername(realm, "mary");

            UserConsentModel johnFooConsent =
                    session.users().getConsentByClient(realm, john.getId(), fooClient.getId());
            Assert.assertEquals(1, johnFooConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", johnFooConsent));
            Assert.assertNotNull("Created Date should be set", johnFooConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", johnFooConsent.getLastUpdatedDate());

            UserConsentModel johnBarConsent =
                    session.users().getConsentByClient(realm, john.getId(), barClient.getId());
            Assert.assertEquals(1, johnBarConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "bar", johnBarConsent));
            Assert.assertNotNull("Created Date should be set", johnBarConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", johnBarConsent.getLastUpdatedDate());

            UserConsentModel maryConsent = session.users().getConsentByClient(realm, mary.getId(), fooClient.getId());
            Assert.assertEquals(1, maryConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", maryConsent));
            Assert.assertNotNull("Created Date should be set", maryConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", maryConsent.getLastUpdatedDate());

            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");
            UserConsentModel maryHardcodedConsent =
                    session.users().getConsentByClient(realm, mary.getId(), hardcodedClient.getId());
            Assert.assertEquals(0, maryHardcodedConsent.getGrantedClientScopes().size());
            Assert.assertNotNull("Created Date should be set", maryHardcodedConsent.getCreatedDate());
            Assert.assertNotNull("Last Updated Date should be set", maryHardcodedConsent.getLastUpdatedDate());

            Assert.assertNull(session.users().getConsentByClient(realm, john.getId(), hardcodedClient.getId()));

            Assert.assertNull(session.users().getConsentByClient(realm, mary.getId(), barClient.getId()));

            return null;
        });
    }

    @TestOnServer
    public void getAllConsentTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            UserModel mary = session.users().getUserByUsername(realm, "mary");

            Assert.assertEquals(
                    2, session.users().getConsentsStream(realm, john.getId()).count());

            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");

            List<UserConsentModel> maryConsents =
                    session.users().getConsentsStream(realm, mary.getId()).collect(Collectors.toList());
            Assert.assertEquals(2, maryConsents.size());
            UserConsentModel maryConsent = maryConsents.get(0);
            UserConsentModel maryHardcodedConsent = maryConsents.get(1);
            if (maryConsents.get(0).getClient().getId().equals(hardcodedClient.getId())) {
                maryConsent = maryConsents.get(1);
                maryHardcodedConsent = maryConsents.get(0);
            }
            Assert.assertEquals(fooClient.getId(), maryConsent.getClient().getId());
            Assert.assertEquals(1, maryConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", maryConsent));

            Assert.assertEquals(
                    hardcodedClient.getId(), maryHardcodedConsent.getClient().getId());
            Assert.assertEquals(0, maryHardcodedConsent.getGrantedClientScopes().size());

            return null;
        });
    }

    @TestOnServer
    public void updateWithClientScopeRemovalTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");

            UserConsentModel johnConsent = session.users().getConsentByClient(realm, john.getId(), fooClient.getId());
            Assert.assertEquals(1, johnConsent.getGrantedClientScopes().size());

            // Remove foo protocol mapper from johnConsent
            ClientScopeModel fooScope = getClientScopeByName(realm, "foo");
            johnConsent.getGrantedClientScopes().remove(fooScope);

            session.users().updateConsent(realm, john.getId(), johnConsent);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");
            UserConsentModel johnConsent = session.users().getConsentByClient(realm, john.getId(), fooClient.getId());

            Assert.assertEquals(0, johnConsent.getGrantedClientScopes().size());
            Assert.assertTrue(
                    "Created date should be less than last updated date",
                    johnConsent.getCreatedDate() < johnConsent.getLastUpdatedDate());

            return null;
        });
    }

    @TestOnServer
    public void revokeTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");
            UserModel mary = session.users().getUserByUsername(realm, "mary");

            session.users().revokeConsentForClient(realm, john.getId(), fooClient.getId());
            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");
            session.users().revokeConsentForClient(realm, mary.getId(), hardcodedClient.getId());

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            ClientModel hardcodedClient = session.clients().getClientByClientId(realm, "hardcoded-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            Assert.assertNull(session.users().getConsentByClient(realm, john.getId(), fooClient.getId()));
            UserModel mary = session.users().getUserByUsername(realm, "mary");
            Assert.assertNull(session.users().getConsentByClient(realm, mary.getId(), hardcodedClient.getId()));

            return null;
        });
    }

    @TestOnServer
    public void deleteUserTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        AtomicReference<String> johnUserID = new AtomicReference<>();

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            UserModel john = session.users().getUserByUsername(realm, "john");
            johnUserID.set(john.getId());
            session.users().removeUser(realm, john);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertEquals(
                    0,
                    session.users().getConsentsStream(realm, johnUserID.get()).count());

            UserModel mary = session.users().getUserByUsername(realm, "mary");
            Assert.assertEquals(
                    2, session.users().getConsentsStream(realm, mary.getId()).count());

            return null;
        });
    }

    @TestOnServer
    public void deleteClientScopeTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel fooScope = getClientScopeByName(realm, "foo");
            realm.removeClientScope(fooScope.getId());

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");

            UserModel john = session.users().getUserByUsername(realm, "john");
            UserConsentModel johnConsent = session.users().getConsentByClient(realm, john.getId(), fooClient.getId());

            Assert.assertEquals(0, johnConsent.getGrantedClientScopes().size());

            return null;
        });
    }

    @TestOnServer
    public void deleteClientTest(KeycloakSession testSession) {

        createConsents(testSession, REALM_NAME);
        AtomicReference<String> barClientID = new AtomicReference<>();

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel barClient = realm.getClientByClientId("bar-client");
            barClientID.set(barClient.getId());

            realm.removeClient(barClient.getId());
            Assert.assertNull(realm.getClientByClientId("bar-client"));

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientModel fooClient = realm.getClientByClientId("foo-client");
            UserModel john = session.users().getUserByUsername(realm, "john");

            UserConsentModel johnFooConsent =
                    session.users().getConsentByClient(realm, john.getId(), fooClient.getId());
            Assert.assertEquals(1, johnFooConsent.getGrantedClientScopes().size());
            Assert.assertTrue(isClientScopeGranted(realm, "foo", johnFooConsent));

            Assert.assertNull(session.users().getConsentByClient(realm, john.getId(), barClientID.get()));

            return null;
        });
    }

    public static class UserConsentRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            realm.addClient("foo-client");
            realm.addClient("bar-client");
            realm.addClient("hardcoded-client");
            realm.addUser("john");
            realm.addUser("mary");
            realm.addClientScope(clientScope("foo"));
            realm.addClientScope(clientScope("bar"));
            return realm;
        }

        private static ClientScopeRepresentation clientScope(String name) {
            ClientScopeRepresentation rep = new ClientScopeRepresentation();
            rep.setName(name);
            rep.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
            return rep;
        }
    }
}
