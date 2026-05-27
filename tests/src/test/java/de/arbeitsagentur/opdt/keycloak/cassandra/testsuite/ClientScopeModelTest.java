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

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNull;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.keycloak.common.constants.KerberosConstants;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.OIDCLoginProtocolFactory;
import org.keycloak.protocol.oidc.mappers.UserPropertyMapper;
import org.keycloak.protocol.oidc.mappers.UserSessionNoteMapper;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class ClientScopeModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "client-scope-model";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testSearch(KeycloakSession testSession) {

        List<String> createdClientScopes = new ArrayList<>();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");
            createdClientScopes.add(clientScope.getId());

            clientScope.setName("Testscope");
            clientScope.setDescription("Desc");
            clientScope.setIsDynamicScope(false);
            clientScope.setProtocol("openid-connect");
            clientScope.setAttribute("testKey", "testVal");
            clientScope.setAttribute("testKey2", "testVal2");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            List<String> clientScopes = session.clientScopes()
                    .getClientScopesStream(realm)
                    .map(ClientScopeModel::getId)
                    .collect(Collectors.toList());
            assertThat(clientScopes, hasItem(createdClientScopes.get(0)));
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Map<String, String> searchMap = Map.of("testKey", "testVal", "testKey2", "testVal2");
            Map<String, String> searchMap2 = Map.of("testKey3", "testVal", "testKey2", "testVal2");
            List<String> clientScopes = session.clientScopes()
                    .getClientScopesByAttributes(realm, searchMap, false)
                    .map(ClientScopeModel::getId)
                    .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(1));

            clientScopes = session.clientScopes()
                    .getClientScopesByAttributes(realm, searchMap2, true)
                    .map(ClientScopeModel::getId)
                    .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(1));

            clientScopes = session.clientScopes()
                    .getClientScopesByAttributes(realm, searchMap2, false)
                    .map(ClientScopeModel::getId)
                    .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(0));

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            List<String> clientScopes = session.clientScopes()
                    .getClientScopesByProtocol(realm, "openid-connect")
                    .map(ClientScopeModel::getId)
                    .collect(Collectors.toList());
            assertThat(clientScopes, hasItem(createdClientScopes.get(0)));

            clientScopes = session.clientScopes()
                    .getClientScopesByProtocol(realm, "openid-connect1")
                    .map(ClientScopeModel::getId)
                    .collect(Collectors.toList());
            assertThat(clientScopes, hasSize(0));

            return null;
        });
    }

    @TestOnServer
    public void testBasicAttributes(KeycloakSession testSession) {

        List<String> createdClientScopes = new ArrayList<>();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");
            createdClientScopes.add(clientScope.getId());

            clientScope.setName("Testscope");
            clientScope.setDescription("Desc");
            clientScope.setIsDynamicScope(false);
            clientScope.setProtocol("openid-connect");
            clientScope.setAttribute("testKey", "testVal");
            clientScope.setAttribute("testKey2", "testVal2");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, createdClientScopes.get(0));
            assertThat(clientScope.getName(), is("Testscope"));
            assertThat(clientScope.getDescription(), is("Desc"));
            assertThat(clientScope.isDynamicScope(), is(false));
            assertThat(clientScope.getProtocol(), is("openid-connect"));
            assertThat(clientScope.getAttribute("testKey"), is("testVal"));
            assertThat(clientScope.getAttributes().get("testKey"), is("testVal"));
            assertThat(clientScope.getAttribute("testKey2"), is("testVal2"));
            assertThat(clientScope.getAttributes().get("testKey2"), is("testVal2"));

            clientScope.removeAttribute("testKey");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, createdClientScopes.get(0));
            assertNull(clientScope.getAttribute("testKey"));
            assertThat(clientScope.getAttributes().entrySet(), hasSize(2)); // includes entityVersion

            session.clientScopes().removeClientScope(realm, createdClientScopes.get(0));

            return null;
        });
    }

    @TestOnServer
    public void testProtocolMappers(KeycloakSession testSession) {

        ProtocolMapperModel usernameMapper = UserPropertyMapper.createClaimMapper(
                OIDCLoginProtocolFactory.USERNAME, "username", "preferred_username", "String", true, true, false);

        ProtocolMapperModel kerberosMapper = UserSessionNoteMapper.createClaimMapper(
                KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME,
                KerberosConstants.GSS_DELEGATION_CREDENTIAL,
                KerberosConstants.GSS_DELEGATION_CREDENTIAL,
                "String",
                true,
                false,
                false);

        List<String> createdClientScopes = new ArrayList<>();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");
            createdClientScopes.add(clientScope.getId());
            clientScope.addProtocolMapper(usernameMapper);
            clientScope.addProtocolMapper(kerberosMapper);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, createdClientScopes.get(0));

            ProtocolMapperModel actualUsernameMapper =
                    clientScope.getProtocolMapperByName("openid-connect", OIDCLoginProtocolFactory.USERNAME);
            assertThat(actualUsernameMapper, is(usernameMapper));

            ProtocolMapperModel actualKerberosMapper = clientScope.getProtocolMapperByName(
                    "openid-connect", KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME);
            assertThat(actualKerberosMapper, is(kerberosMapper));

            assertThat(clientScope.getProtocolMapperByName("saml", OIDCLoginProtocolFactory.USERNAME), nullValue());
            assertThat(clientScope.getProtocolMapperById(actualUsernameMapper.getId()), is(usernameMapper));

            ProtocolMapperModel updatedUsernameMapper = UserPropertyMapper.createClaimMapper(
                    OIDCLoginProtocolFactory.USERNAME,
                    "username",
                    "preferred_username_updated",
                    "String",
                    true,
                    true,
                    false);

            clientScope.updateProtocolMapper(updatedUsernameMapper);
            assertThat(
                    clientScope.getProtocolMapperByName("openid-connect", OIDCLoginProtocolFactory.USERNAME),
                    is(updatedUsernameMapper));

            clientScope.removeProtocolMapper(updatedUsernameMapper);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, createdClientScopes.get(0));

            assertThat(
                    clientScope.getProtocolMapperByName("openid-connect", OIDCLoginProtocolFactory.USERNAME),
                    nullValue());
            assertThat(
                    clientScope.getProtocolMapperByName(
                            "openid-connect", KerberosConstants.GSS_DELEGATION_CREDENTIAL_DISPLAY_NAME),
                    is(kerberosMapper));

            session.clientScopes().removeClientScope(realm, createdClientScopes.get(0));
            return null;
        });
    }

    @TestOnServer
    public void testScopeMappings(KeycloakSession testSession) {

        ClientModel client = withRealm(
                testSession, REALM_NAME, (session, realm) -> session.clients().addClient(realm, "myClient"));
        RoleModel realmRole = withRealm(testSession, REALM_NAME, (session, realm) -> realm.addRole("realmRole"));
        RoleModel clientRole = withRealm(
                testSession, REALM_NAME, (session, realm) -> session.roles().addClientRole(client, "clientRole"));

        List<String> createdClientScopes = new ArrayList<>();
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().addClientScope(realm, "myClientScope1");
            createdClientScopes.add(clientScope.getId());

            clientScope.addScopeMapping(realmRole);
            clientScope.addScopeMapping(clientRole);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            ClientScopeModel clientScope = session.clientScopes().getClientScopeById(realm, createdClientScopes.get(0));
            List<RoleModel> scopeMappings = clientScope.getScopeMappingsStream().collect(Collectors.toList());
            assertThat(scopeMappings, hasSize(2));
            assertThat(
                    scopeMappings.stream().map(RoleModel::getName).collect(Collectors.toList()),
                    containsInAnyOrder("realmRole", "clientRole"));

            List<RoleModel> realmScopeMappings =
                    clientScope.getRealmScopeMappingsStream().collect(Collectors.toList());
            assertThat(realmScopeMappings, hasSize(1));
            assertThat(realmScopeMappings.get(0).getName(), is("realmRole"));

            assertThat(clientScope.hasScope(realmRole), is(true));
            assertThat(clientScope.hasScope(clientRole), is(true));

            clientScope.deleteScopeMapping(realmRole);
            assertThat(clientScope.hasScope(realmRole), is(false));
            assertThat(clientScope.getRealmScopeMappingsStream().collect(Collectors.toList()), hasSize(0));
            assertThat(clientScope.getScopeMappingsStream().collect(Collectors.toList()), hasSize(1));

            session.clientScopes().removeClientScope(realm, createdClientScopes.get(0));

            return null;
        });
    }
}
