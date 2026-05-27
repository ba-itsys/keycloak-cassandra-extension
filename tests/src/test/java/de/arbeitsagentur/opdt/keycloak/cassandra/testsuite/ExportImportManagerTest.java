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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticatorFactory;
import org.keycloak.authentication.authenticators.browser.UsernamePasswordFormFactory;
import org.keycloak.common.crypto.CryptoIntegration;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.exportimport.ExportAdapter;
import org.keycloak.exportimport.ExportOptions;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.resources.KeycloakApplication;
import org.keycloak.storage.DatastoreProvider;
import org.keycloak.storage.ExportImportManager;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.remote.annotations.TestOnServer;
import org.keycloak.util.JsonSerialization;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class ExportImportManagerTest extends CassandraModelTest {
    private static final String REALM_NAME = "test";
    private static final String DEFAULT_ROLE_NAME = "realmRole2";
    private static final String CLIENT_BASE_URL = "https://client.example.test/app/auth";
    private static final String CLIENT_MANAGEMENT_URL = "https://client.example.test/app/admin";
    private static final String CLIENT_REDIRECT_URI = "https://client.example.test/app/auth/*";

    private static void configureExportImportRealm(KeycloakSession s) {
        RealmModel existingRealm = s.realms().getRealmByName(REALM_NAME);
        if (existingRealm != null) {
            s.realms().removeRealm(existingRealm.getId());
        }
        RealmModel realm = s.realms().createRealm(REALM_NAME);
        realm.setSslRequired(SslRequired.NONE);
        realm.setSsoSessionMaxLifespan(60);
        realm.setSsoSessionIdleTimeout(30);
        realm.setClientSessionMaxLifespan(100);
        realm.setAccessCodeLifespan(10);
        realm.setAccessCodeLifespanLogin(20);
        realm.setAccessTokenLifespan(30);
        realm.setAccessCodeLifespanUserAction(40);
        realm.setAccessTokenLifespanForImplicitFlow(50);
        realm.setUserManagedAccessAllowed(false);
        realm.setActionTokenGeneratedByAdminLifespan(10);
        realm.setActionTokenGeneratedByUserLifespan(20);
        realm.setAttribute("testAttribute", "testValue");
        realm.setDisplayName("test realm");
        realm.setDisplayNameHtml("<p>test realm</p>");
        realm.setSupportedLocales(new HashSet<>(Arrays.asList("de-DE")));
        realm.setLoginTheme("test-theme");
        realm.setWebAuthnPolicy(WebAuthnPolicy.DEFAULT_POLICY);
        realm.setRememberMe(true);
        realm.setSsoSessionIdleTimeoutRememberMe(60);
        realm.setSsoSessionMaxLifespanRememberMe(80);

        ClientModel client1 = s.clients().addClient(realm, "client1");
        s.clients().addClient(realm, "client2");
        client1.setServiceAccountsEnabled(true);
        UserModel client1Svc = s.users().addUser(realm, "client1Svc");
        client1Svc.setServiceAccountClientLink(client1.getId());

        ClientScopeModel scope1 = s.clientScopes().addClientScope(realm, "scope1");
        ClientScopeModel scope2 = s.clientScopes().addClientScope(realm, "scope2");
        client1.addClientScope(scope1, true);
        client1.addClientScope(scope2, false);

        RoleModel scopeMappingRealmRole = s.roles().addRealmRole(realm, "scopeMappingRole");
        RoleModel scopeMappingClientRole1 = s.roles().addClientRole(client1, "scopeMappingRoleClient");
        RoleModel scopeMappingClientRole2 = s.roles().addClientRole(client1, "scopeMappingRoleClient2");
        scopeMappingRealmRole.addCompositeRole(scopeMappingClientRole1);
        scopeMappingRealmRole.addCompositeRole(s.roles().addRealmRole(realm, "compositeRealmRole"));

        client1.addScopeMapping(scopeMappingRealmRole);
        client1.addScopeMapping(scopeMappingClientRole1);
        client1.addScopeMapping(scopeMappingClientRole2);

        s.roles().addClientRole(client1, "clientRole1");
        s.roles().addClientRole(client1, "clientRole2");
        RoleModel realmRole1 = s.roles().addRealmRole(realm, "realmRole1");
        RoleModel realmRole2 = s.roles().addRealmRole(realm, "realmRole2");

        realm.setDefaultRole(realmRole2);

        // Client Scope
        ClientScopeModel clientScope1 = s.clientScopes().addClientScope(realm, "realmScope");
        clientScope1.addScopeMapping(realmRole1);

        // Auth Flows and Clients copied from Keycloak-Tests

        // Parent flow
        AuthenticationFlowModel browser = new AuthenticationFlowModel();
        browser.setAlias("parent-flow");
        browser.setDescription("browser based authentication");
        browser.setProviderId("basic-flow");
        browser.setTopLevel(true);
        browser.setBuiltIn(true);
        browser = realm.addAuthenticationFlow(browser);
        realm.setBrowserFlow(browser);

        // Subflow2
        AuthenticationFlowModel subflow2 = new AuthenticationFlowModel();
        subflow2.setTopLevel(false);
        subflow2.setBuiltIn(true);
        subflow2.setAlias("subflow-2");
        subflow2.setDescription("username+password AND pushButton");
        subflow2.setProviderId("basic-flow");
        subflow2 = realm.addAuthenticationFlow(subflow2);

        AuthenticationExecutionModel execution = new AuthenticationExecutionModel();
        execution.setParentFlow(browser.getId());
        execution.setRequirement(AuthenticationExecutionModel.Requirement.ALTERNATIVE);
        execution.setFlowId(subflow2.getId());
        execution.setPriority(20);
        execution.setAuthenticatorFlow(true);
        realm.addAuthenticatorExecution(execution);

        // Subflow2 - push the button
        execution = new AuthenticationExecutionModel();
        execution.setParentFlow(subflow2.getId());
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED);
        execution.setAuthenticator("push-button-authenticator");
        execution.setPriority(10);
        execution.setAuthenticatorFlow(false);

        realm.addAuthenticatorExecution(execution);

        // Subflow2 - username-password
        execution = new AuthenticationExecutionModel();
        execution.setParentFlow(subflow2.getId());
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED);
        execution.setAuthenticator(UsernamePasswordFormFactory.PROVIDER_ID);
        execution.setPriority(20);
        execution.setAuthenticatorFlow(false);

        realm.addAuthenticatorExecution(execution);

        ClientModel client = realm.addClient("test-app-flow");
        client.setSecret("password");
        client.setBaseUrl(CLIENT_BASE_URL);
        client.setManagementUrl(CLIENT_MANAGEMENT_URL);
        client.setEnabled(true);
        client.addRedirectUri(CLIENT_REDIRECT_URI);
        client.setAuthenticationFlowBindingOverride(AuthenticationFlowBindings.BROWSER_BINDING, browser.getId());
        client.setPublicClient(false);

        // Parent flow
        AuthenticationFlowModel directGrant = new AuthenticationFlowModel();
        directGrant.setAlias("direct-override-flow");
        directGrant.setDescription("direct grant based authentication");
        directGrant.setProviderId("basic-flow");
        directGrant.setTopLevel(true);
        directGrant.setBuiltIn(true);
        directGrant = realm.addAuthenticationFlow(directGrant);

        execution = new AuthenticationExecutionModel();
        execution.setParentFlow(directGrant.getId());
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED);
        execution.setAuthenticator("username-only");
        execution.setPriority(10);
        execution.setAuthenticatorFlow(false);

        realm.addAuthenticatorExecution(execution);

        AuthenticationFlowModel challengeOTP = new AuthenticationFlowModel();
        challengeOTP.setAlias("challenge-override-flow");
        challengeOTP.setDescription("challenge grant based authentication");
        challengeOTP.setProviderId("basic-flow");
        challengeOTP.setTopLevel(true);
        challengeOTP.setBuiltIn(true);

        challengeOTP = realm.addAuthenticationFlow(challengeOTP);

        execution = new AuthenticationExecutionModel();
        execution.setParentFlow(challengeOTP.getId());
        execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED);
        execution.setAuthenticator(OTPFormAuthenticatorFactory.PROVIDER_ID);
        execution.setPriority(10);
        realm.addAuthenticatorExecution(execution);

        client = realm.addClient("test-app-direct-override");
        client.setSecret("password");
        client.setBaseUrl(CLIENT_BASE_URL);
        client.setManagementUrl(CLIENT_MANAGEMENT_URL);
        client.setEnabled(true);
        client.addRedirectUri(CLIENT_REDIRECT_URI);
        client.setPublicClient(false);
        client.setDirectAccessGrantsEnabled(true);
        client.setAuthenticationFlowBindingOverride(AuthenticationFlowBindings.BROWSER_BINDING, browser.getId());
        client.setAuthenticationFlowBindingOverride(
                AuthenticationFlowBindings.DIRECT_GRANT_BINDING, directGrant.getId());

        // User
        UserModel user = s.users().addUser(realm, "testuser");
        user.setAttribute("testKey", Arrays.asList("testValue"));

        s.users().addFederatedIdentity(realm, user, new FederatedIdentityModel("idpId", "idpUserId", "idpUserName"));
    }

    @TestOnServer
    public void testExportImport(KeycloakSession testSession) {
        inCommittedTransaction(testSession, ExportImportManagerTest::configureExportImportRealm);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ExportImportManager exportImportManager =
                    session.getProvider(DatastoreProvider.class).getExportImportManager();
            exportImportManager.exportRealm(
                    realm, new ExportOptions(true, true, true, false, false), new ExportAdapter() {
                        @Override
                        public void setType(String s) {
                            // noop
                        }

                        @Override
                        public void writeToOutputStream(ConsumerOfOutputStream consumerOfOutputStream) {
                            try {
                                consumerOfOutputStream.accept(outputStream);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });

            session.realms().removeRealm(realm.getId());

            return null;
        });

        inCommittedTransaction(testSession, session -> {
            RealmRepresentation rep;
            try {
                rep = JsonSerialization.readValue(outputStream.toByteArray(), RealmRepresentation.class);
            } catch (IOException e) {
                throw new ModelException("unable to read contents from stream", e);
            }

            RealmModel newRealm = session.realms().createRealm(rep.getId(), rep.getRealm());
            // DefaultRole must be set beforehand to prevent NPE...
            RoleModel newlySavedDefaultRole = session.roles().addRealmRole(newRealm, DEFAULT_ROLE_NAME);
            newRealm.setDefaultRole(newlySavedDefaultRole);
            CryptoIntegration.init(KeycloakApplication.class.getClassLoader());

            ExportImportManager exportImportManager =
                    session.getProvider(DatastoreProvider.class).getExportImportManager();
            exportImportManager.importRealm(rep, newRealm, () -> {});
        });

        inCommittedTransaction(testSession, session -> {
            RealmModel importedRealm = session.realms().getRealmByName(REALM_NAME);
            assertThat(importedRealm.getName(), is(REALM_NAME));
            assertThat(importedRealm.getSslRequired(), is(SslRequired.NONE));
            assertThat(importedRealm.getSsoSessionMaxLifespan(), is(60));
            assertThat(importedRealm.getSsoSessionIdleTimeout(), is(30));
            assertThat(importedRealm.getClientSessionMaxLifespan(), is(100));
            assertThat(importedRealm.getAccessCodeLifespan(), is(10));
            assertThat(importedRealm.getAccessCodeLifespanLogin(), is(20));
            assertThat(importedRealm.getAccessTokenLifespan(), is(30));
            assertThat(importedRealm.getAccessCodeLifespanUserAction(), is(40));
            assertThat(importedRealm.getAccessTokenLifespanForImplicitFlow(), is(50));
            assertThat(importedRealm.isUserManagedAccessAllowed(), is(false));
            assertThat(importedRealm.getActionTokenGeneratedByAdminLifespan(), is(10));
            assertThat(importedRealm.getActionTokenGeneratedByUserLifespan(), is(20));
            assertThat(importedRealm.getDisplayName(), is("test realm"));
            assertThat(importedRealm.getDisplayNameHtml(), is("<p>test realm</p>"));
            assertThat(
                    importedRealm.getSupportedLocalesStream().collect(Collectors.toList()),
                    containsInAnyOrder("de-DE"));
            assertThat(importedRealm.getLoginTheme(), is("test-theme"));
            assertThat(importedRealm.isRememberMe(), is(true));
            assertThat(importedRealm.getSsoSessionIdleTimeoutRememberMe(), is(60));
            assertThat(importedRealm.getSsoSessionMaxLifespanRememberMe(), is(80));
            assertThat(importedRealm.getAttribute("testAttribute"), is("testValue"));

            List<ClientScopeModel> realmScopes = importedRealm
                    .getClientScopesStream()
                    .filter(scope -> List.of("realmScope", "scope1", "scope2").contains(scope.getName()))
                    .collect(Collectors.toList());
            assertThat(realmScopes, hasSize(3));
            assertThat(
                    realmScopes.stream().map(ClientScopeModel::getName).collect(Collectors.toList()),
                    containsInAnyOrder("realmScope", "scope1", "scope2"));

            List<RoleModel> scopeRoles = realmScopes.stream()
                    .filter(c -> c.getName().equals("realmScope"))
                    .flatMap(ClientScopeModel::getRealmScopeMappingsStream)
                    .filter(role -> role.getName().equals("realmRole1"))
                    .collect(Collectors.toList());
            assertThat(scopeRoles, hasSize(1));
            assertThat(scopeRoles.get(0).getName(), is("realmRole1"));

            ClientModel importedClient = session.clients().getClientByClientId(importedRealm, "client1");
            assertThat(
                    importedClient.getClientScopes(true).values().stream()
                            .map(ClientScopeModel::getName)
                            .collect(Collectors.toList()),
                    hasItem("scope1"));
            assertThat(
                    importedClient.getClientScopes(false).values().stream()
                            .map(ClientScopeModel::getName)
                            .collect(Collectors.toList()),
                    hasItem("scope2"));
            assertThat(importedClient.getScopeMappingsStream().collect(Collectors.toList()), hasSize(3));
            assertThat(
                    importedClient
                            .getScopeMappingsStream()
                            .map(RoleModel::getName)
                            .collect(Collectors.toList()),
                    containsInAnyOrder("scopeMappingRole", "scopeMappingRoleClient", "scopeMappingRoleClient2"));

            List<RoleModel> clientRoles =
                    session.roles().getClientRolesStream(importedClient).collect(Collectors.toList());
            assertThat(clientRoles, hasSize(4));
            assertThat(
                    clientRoles.stream().map(RoleModel::getName).collect(Collectors.toList()),
                    containsInAnyOrder(
                            "clientRole1", "clientRole2", "scopeMappingRoleClient", "scopeMappingRoleClient2"));

            List<RoleModel> realmRoles =
                    session.roles().getRealmRolesStream(importedRealm).collect(Collectors.toList());
            assertThat(realmRoles, hasSize(4));
            assertThat(
                    realmRoles.stream().map(RoleModel::getName).collect(Collectors.toList()),
                    containsInAnyOrder("realmRole1", "realmRole2", "scopeMappingRole", "compositeRealmRole"));
            List<RoleModel> compositeRoles = realmRoles.stream()
                    .filter(r -> r.getName().equals("scopeMappingRole"))
                    .flatMap(RoleModel::getCompositesStream)
                    .collect(Collectors.toList());
            assertThat(compositeRoles, hasSize(2));
            assertThat(
                    compositeRoles.stream().map(RoleModel::getName).collect(Collectors.toList()),
                    containsInAnyOrder("scopeMappingRoleClient", "compositeRealmRole"));
            assertThat(importedRealm.getDefaultRole().getName(), is("realmRole2"));

            AuthenticationFlowModel browserFlow = importedRealm.getBrowserFlow();
            assertThat(browserFlow.getAlias(), is("parent-flow"));
            assertThat(browserFlow.getProviderId(), is("basic-flow"));

            AuthenticationFlowModel subflow2 = importedRealm.getFlowByAlias("subflow-2");
            AuthenticationExecutionModel subflow2Execution =
                    importedRealm.getAuthenticationExecutionByFlowId(subflow2.getId());
            assertThat(subflow2Execution.getParentFlow(), is(browserFlow.getId()));
            assertThat(subflow2Execution.getRequirement(), is(AuthenticationExecutionModel.Requirement.ALTERNATIVE));

            assertThat(subflow2.getAlias(), is("subflow-2"));
            assertThat(subflow2Execution.getFlowId(), is(subflow2.getId()));

            List<AuthenticationExecutionModel> subflow2ChildExecutions = importedRealm
                    .getAuthenticationExecutionsStream(subflow2.getId())
                    .collect(Collectors.toList());
            assertThat(subflow2ChildExecutions, hasSize(2));
            assertThat(
                    subflow2ChildExecutions.stream()
                            .map(AuthenticationExecutionModel::getAuthenticator)
                            .collect(Collectors.toList()),
                    containsInAnyOrder("push-button-authenticator", UsernamePasswordFormFactory.PROVIDER_ID));
            assertThat(
                    subflow2ChildExecutions.stream().allMatch(e -> e.getRequirement()
                            .equals(AuthenticationExecutionModel.Requirement.REQUIRED)),
                    is(true));

            ClientModel testAppFlowClient = session.clients().getClientByClientId(importedRealm, "test-app-flow");
            assertThat(testAppFlowClient.getBaseUrl(), is(CLIENT_BASE_URL));
            assertThat(testAppFlowClient.getManagementUrl(), is(CLIENT_MANAGEMENT_URL));
            assertThat(testAppFlowClient.isEnabled(), is(true));
            assertThat(testAppFlowClient.getRedirectUris(), hasSize(1));
            assertThat(testAppFlowClient.getRedirectUris().iterator().next(), is(CLIENT_REDIRECT_URI));
            assertThat(testAppFlowClient.getAuthenticationFlowBindingOverrides().values(), hasSize(1));
            assertThat(
                    testAppFlowClient.getAuthenticationFlowBindingOverride(AuthenticationFlowBindings.BROWSER_BINDING),
                    is(browserFlow.getId()));
            assertThat(testAppFlowClient.isPublicClient(), is(false));

            UserModel testuser = session.users().getUserByUsername(importedRealm, "testuser");
            assertThat(testuser.getFirstAttribute("testKey"), is("testValue"));

            FederatedIdentityModel importedFederatedIdentity =
                    session.users().getFederatedIdentity(importedRealm, testuser, "idpId");
            assertThat(importedFederatedIdentity.getUserId(), is("idpUserId"));
            assertThat(importedFederatedIdentity.getUserName(), is("idpUserName"));

            UserModel importedServiceAccount = session.users().getServiceAccount(importedClient);
            assertThat(importedServiceAccount.getUsername(), is("client1Svc"));

            return null;
        });
    }
}
