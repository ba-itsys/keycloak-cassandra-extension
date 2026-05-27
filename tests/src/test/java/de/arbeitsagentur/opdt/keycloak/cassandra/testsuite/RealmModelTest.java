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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.keycloak.models.utils.KeycloakModelUtils.buildGroupPath;

import de.arbeitsagentur.opdt.keycloak.cassandra.realm.CassandraRealmAdapter;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.common.util.Time;
import org.keycloak.component.ComponentModel;
import org.keycloak.keys.KeyProvider;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderEventListener;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class RealmModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "realm-model";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD)
    ManagedRealm managedRealm;

    private static RealmModel createRealm(KeycloakSession session, String name) {
        RealmModel realm = session.realms().getRealmByName(name);
        if (realm != null) {
            session.realms().removeRealm(realm.getId());
        }
        return session.realms().createRealm(name);
    }

    @TestOnServer
    public void staleRealmUpdate(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            realm.setAttribute("key", "val");

            return null;
        });

        boolean staleExceptionOccured = false;
        try {
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("3"));

                realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "2");

                return null;
            });
        } catch (Exception e) {
            staleExceptionOccured = true;
        }

        assertTrue(staleExceptionOccured);

        staleExceptionOccured = false;
        try {
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("3"));

                realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "4");

                return null;
            });
        } catch (Exception e) {
            staleExceptionOccured = true;
        }

        assertTrue(staleExceptionOccured);
    }

    @TestOnServer
    public void workingRealmUpdate(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            realm.setAttribute("key", "val");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("3"));

            realm.setAttribute("key", "val2");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("4"));
            assertThat(realm.getAttribute("key"), is("val2"));

            return null;
        });
    }

    @TestOnServer
    public void getRealmByName(KeycloakSession testSession) {

        inCommittedTransaction(testSession, s -> {
            s.realms().createRealm("my-realm");
            RealmModel realmByName = s.realms().getRealmByName("my-realm");

            assertThat(realmByName.getName(), is("my-realm"));

            realmByName.setName("my-updated-realm");
        });

        inCommittedTransaction(testSession, s -> {
            RealmModel realmByName = s.realms().getRealmByName("my-updated-realm");

            assertThat(realmByName.getName(), is("my-updated-realm"));

            s.realms().removeRealm(realmByName.getId());
            assertNull(s.realms().getRealmByName("my-updated-realm"));
        });
    }

    @TestOnServer
    public void entityVersionAttribute(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("2"));
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY), is("2"));

            realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY, "42");
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("2"));
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY), is("2"));

            realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "42");
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION), is("42"));
            assertThat(realm.getAttribute(CassandraRealmAdapter.ENTITY_VERSION_READONLY), is("42"));

            realm.setAttribute(CassandraRealmAdapter.ENTITY_VERSION, "2");
            return null;
        });
    }

    @TestOnServer
    public void testRealmLocalizationTexts(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            // Assert emptyMap
            assertThat(realm.getRealmLocalizationTexts(), anEmptyMap());
            // Add a localization test
            session.realms().saveLocalizationTexts(realm, "en", Map.of("key-a", "text-a_en"));
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            // Assert the map contains the added value
            assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(1));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(equalTo("en"), allOf(aMapWithSize(1), hasEntry(equalTo("key-a"), equalTo("text-a_en")))));
            assertThat(session.realms().getLocalizationTextsById(realm, "en", "key-a"), is("text-a_en"));

            // Add another localization text to previous locale
            session.realms().saveLocalizationTexts(realm, "en", Map.of("key-a", "text-a_en", "key-b", "text-b_en"));
            session.realms().saveLocalizationText(realm, "en", "key-c", "text-c_en");
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(1));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(
                            equalTo("en"),
                            allOf(
                                    aMapWithSize(3),
                                    hasEntry(equalTo("key-a"), equalTo("text-a_en")),
                                    hasEntry(equalTo("key-b"), equalTo("text-b_en")),
                                    hasEntry(equalTo("key-c"), equalTo("text-c_en")))));

            // Add new locale
            session.realms().saveLocalizationText(realm, "de", "key-a", "text-a_de");
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            // Check everything created successfully
            assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(2));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(
                            equalTo("en"),
                            allOf(
                                    aMapWithSize(3),
                                    hasEntry(equalTo("key-a"), equalTo("text-a_en")),
                                    hasEntry(equalTo("key-b"), equalTo("text-b_en")),
                                    hasEntry(equalTo("key-c"), equalTo("text-c_en")))));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(equalTo("de"), allOf(aMapWithSize(1), hasEntry(equalTo("key-a"), equalTo("text-a_de")))));

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.realms().updateLocalizationText(realm, "en", "key-b", "updated");
            assertThat(session.realms().getLocalizationTextsById(realm, "en", "key-b"), is("updated"));

            session.realms().deleteLocalizationText(realm, "en", "key-a");
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(2));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(
                            equalTo("en"),
                            allOf(
                                    aMapWithSize(2),
                                    hasEntry(equalTo("key-b"), equalTo("updated")),
                                    hasEntry(equalTo("key-c"), equalTo("text-c_en")))));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(equalTo("de"), allOf(aMapWithSize(1), hasEntry(equalTo("key-a"), equalTo("text-a_de")))));

            assertThat(session.realms().getLocalizationTextsById(realm, "en", "key-b"), is("updated"));

            session.realms().deleteLocalizationTextsByLocale(realm, "de");
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(realm.getRealmLocalizationTexts(), aMapWithSize(1));
            assertThat(
                    realm.getRealmLocalizationTexts(),
                    hasEntry(
                            equalTo("en"),
                            allOf(
                                    aMapWithSize(2),
                                    hasEntry(equalTo("key-b"), equalTo("updated")),
                                    hasEntry(equalTo("key-c"), equalTo("text-c_en")))));

            return null;
        });
    }

    @TestOnServer
    public void testRealmPreRemoveDoesntRemoveEntitiesFromOtherRealms(KeycloakSession testSession) {

        String realm1Id = inCommittedTransaction(testSession, session -> {
            RealmModel realm = createRealm(session, "realm1");
            realm.setDefaultRole(
                    session.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
            return realm.getId();
        });
        String realm2Id = inCommittedTransaction(testSession, session -> {
            RealmModel realm = createRealm(session, "realm2");
            realm.setDefaultRole(
                    session.roles().addRealmRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName()));
            return realm.getId();
        });

        String clientRealm1 = withRealm(testSession, realm1Id, (keycloakSession, realmModel) -> {
            ClientModel clientRealm = realmModel.addClient("clientRealm1");
            return clientRealm.getId();
        });

        inCommittedTransaction(testSession, (Consumer<KeycloakSession>)
                keycloakSession -> keycloakSession.realms().removeRealm(realm2Id));

        ClientModel client = withRealm(testSession, realm1Id, (keycloakSession, realmModel) -> {
            return realmModel.getClientById(clientRealm1);
        });

        assertThat(client, notNullValue());
    }

    @TestOnServer
    public void testMoveGroup(KeycloakSession testSession) {

        ProviderEventListener providerEventListener = null;
        try {
            List<GroupModel.GroupPathChangeEvent> groupPathChangeEvents = new ArrayList<>();
            providerEventListener = event -> {
                if (event instanceof GroupModel.GroupPathChangeEvent) {
                    groupPathChangeEvents.add((GroupModel.GroupPathChangeEvent) event);
                }
            };
            testSession.getKeycloakSessionFactory().register(providerEventListener);

            withRealm(testSession, REALM_NAME, (session, realm) -> {
                GroupModel groupA = realm.createGroup("a");
                GroupModel groupB = realm.createGroup("b");

                final String previousPath = "/a";
                assertThat(buildGroupPath(groupA), equalTo(previousPath));

                realm.moveGroup(groupA, groupB);

                final String expectedNewPath = "/b/a";
                assertThat(buildGroupPath(groupA), equalTo(expectedNewPath));

                assertThat(groupPathChangeEvents, hasSize(1));
                GroupModel.GroupPathChangeEvent groupPathChangeEvent = groupPathChangeEvents.get(0);
                assertThat(groupPathChangeEvent.getPreviousPath(), equalTo(previousPath));
                assertThat(groupPathChangeEvent.getNewPath(), equalTo(expectedNewPath));

                return null;
            });
        } finally {
            if (providerEventListener != null) {
                testSession.getKeycloakSessionFactory().unregister(providerEventListener);
            }
        }
    }

    @TestOnServer
    public void testAuthenticationFlows(KeycloakSession testSession) {

        String flowId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationFlowModel browser = new AuthenticationFlowModel();
            browser.setAlias("myFlow");
            browser.setDescription("browser based authentication");
            browser.setProviderId("basic-flow");
            browser.setTopLevel(true);
            browser.setBuiltIn(true);

            return realm.addAuthenticationFlow(browser).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationFlowModel readFlow = realm.getAuthenticationFlowById(flowId);
            assertThat(readFlow.getAlias(), is("myFlow"));
            assertThat(readFlow.getDescription(), is("browser based authentication"));
            assertThat(readFlow.getProviderId(), is("basic-flow"));
            assertTrue(readFlow.isTopLevel());
            assertTrue(readFlow.isBuiltIn());

            readFlow.setDescription("test");

            realm.updateAuthenticationFlow(readFlow);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationFlowModel readFlow = realm.getAuthenticationFlowById(flowId);
            assertThat(readFlow.getAlias(), is("myFlow"));
            assertThat(readFlow.getDescription(), is("test"));
            assertThat(readFlow.getProviderId(), is("basic-flow"));
            assertTrue(readFlow.isTopLevel());
            assertTrue(readFlow.isBuiltIn());

            realm.removeAuthenticationFlow(readFlow);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationFlowModel readFlow = realm.getAuthenticationFlowById(flowId);
            assertNull(readFlow);

            return null;
        });
    }

    @TestOnServer
    public void testAuthenticatorExecutions(KeycloakSession testSession) {

        String executionId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationExecutionModel execution = new AuthenticationExecutionModel();
            execution.setParentFlow("test");
            execution.setRequirement(AuthenticationExecutionModel.Requirement.REQUIRED);
            execution.setAuthenticator("username-only");
            execution.setPriority(10);
            execution.setAuthenticatorFlow(false);

            return realm.addAuthenticatorExecution(execution).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationExecutionModel execution = realm.getAuthenticationExecutionById(executionId);
            assertThat(execution.getParentFlow(), is("test"));
            assertThat(execution.getRequirement(), is(AuthenticationExecutionModel.Requirement.REQUIRED));
            assertThat(execution.getAuthenticator(), is("username-only"));
            assertThat(execution.getPriority(), is(10));
            assertFalse(execution.isAuthenticatorFlow());

            execution.setRequirement(AuthenticationExecutionModel.Requirement.ALTERNATIVE);

            realm.updateAuthenticatorExecution(execution);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationExecutionModel execution = realm.getAuthenticationExecutionById(executionId);
            assertThat(execution.getParentFlow(), is("test"));
            assertThat(execution.getRequirement(), is(AuthenticationExecutionModel.Requirement.ALTERNATIVE));
            assertThat(execution.getAuthenticator(), is("username-only"));
            assertThat(execution.getPriority(), is(10));
            assertFalse(execution.isAuthenticatorFlow());

            realm.removeAuthenticatorExecution(execution);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticationExecutionModel execution = realm.getAuthenticationExecutionById(executionId);
            assertNull(execution);

            return null;
        });
    }

    @TestOnServer
    public void testAuthenticatorConfigs(KeycloakSession testSession) {

        String configId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticatorConfigModel config = new AuthenticatorConfigModel();
            config.setAlias("test");
            config.setConfig(Map.of("key1", "val1", "key2", "val2"));

            return realm.addAuthenticatorConfig(config).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticatorConfigModel config = realm.getAuthenticatorConfigById(configId);
            assertThat(config.getAlias(), is("test"));
            assertThat(config.getConfig().entrySet(), hasSize(2));
            assertThat(config.getConfig().get("key1"), is("val1"));
            assertThat(config.getConfig().get("key2"), is("val2"));

            config.getConfig().put("key1", "updatedVal1");

            realm.updateAuthenticatorConfig(config);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticatorConfigModel config = realm.getAuthenticatorConfigByAlias("test");
            assertThat(config.getAlias(), is("test"));
            assertThat(config.getConfig().entrySet(), hasSize(2));
            assertThat(config.getConfig().get("key1"), is("updatedVal1"));
            assertThat(config.getConfig().get("key2"), is("val2"));

            realm.removeAuthenticatorConfig(config);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            AuthenticatorConfigModel config = realm.getAuthenticatorConfigByAlias("test");
            assertNull(config);

            return null;
        });
    }

    @TestOnServer
    public void testRequiredActionProviders(KeycloakSession testSession) {

        String providerId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            RequiredActionProviderModel requiredActionProviderModel = new RequiredActionProviderModel();
            requiredActionProviderModel.setAlias("test");
            requiredActionProviderModel.setProviderId("consent-provider");
            requiredActionProviderModel.setDefaultAction(false);
            requiredActionProviderModel.setPriority(20);
            requiredActionProviderModel.setEnabled(true);

            return realm.addRequiredActionProvider(requiredActionProviderModel).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            RequiredActionProviderModel provider = realm.getRequiredActionProviderById(providerId);
            assertThat(provider.getAlias(), is("test"));
            assertThat(provider.getProviderId(), is("consent-provider"));
            assertFalse(provider.isDefaultAction());
            assertThat(provider.getPriority(), is(20));
            assertTrue(provider.isEnabled());

            provider.setProviderId("test-provider");

            realm.updateRequiredActionProvider(provider);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            RequiredActionProviderModel provider = realm.getRequiredActionProviderByAlias("test");
            assertThat(provider.getAlias(), is("test"));
            assertThat(provider.getProviderId(), is("test-provider"));
            assertFalse(provider.isDefaultAction());
            assertThat(provider.getPriority(), is(20));
            assertTrue(provider.isEnabled());

            realm.removeRequiredActionProvider(provider);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            RequiredActionProviderModel provider = realm.getRequiredActionProviderByAlias("test");
            assertNull(provider);

            return null;
        });
    }

    @TestOnServer
    public void testIdentityProviders(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderModel provider = new IdentityProviderModel();
            provider.setAlias("test");
            provider.setProviderId("idp-provider");
            provider.setDisplayName("External IDP");
            provider.setEnabled(true);

            realm.addIdentityProvider(provider);
            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderModel provider = realm.getIdentityProviderByAlias("test");
            assertThat(provider.getAlias(), is("test"));
            assertThat(provider.getProviderId(), is("idp-provider"));
            assertThat(provider.getDisplayName(), is("External IDP"));
            assertTrue(provider.isEnabled());

            assertTrue(realm.isIdentityFederationEnabled());

            provider.setProviderId("test-provider");

            realm.updateIdentityProvider(provider);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderModel provider = realm.getIdentityProviderByAlias("test");
            assertThat(provider.getAlias(), is("test"));
            assertThat(provider.getProviderId(), is("test-provider"));
            assertThat(provider.getDisplayName(), is("External IDP"));
            assertTrue(provider.isEnabled());

            realm.removeIdentityProviderByAlias("test");

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderModel provider = realm.getIdentityProviderByAlias("test");
            assertNull(provider);
            assertFalse(realm.isIdentityFederationEnabled());

            return null;
        });
    }

    @TestOnServer
    public void testIdentityProviderMappers(KeycloakSession testSession) {

        String mapperId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderMapperModel mapper = new IdentityProviderMapperModel();
            mapper.setName("test");
            mapper.setIdentityProviderMapper("username");
            mapper.setIdentityProviderAlias("testIdp");
            mapper.setConfig(Map.of("key1", "value1"));

            return realm.addIdentityProviderMapper(mapper).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderMapperModel mapper = realm.getIdentityProviderMapperById(mapperId);
            assertThat(mapper.getName(), is("test"));
            assertThat(mapper.getIdentityProviderMapper(), is("username"));
            assertThat(mapper.getIdentityProviderAlias(), is("testIdp"));
            assertThat(mapper.getConfig().entrySet(), hasSize(1));
            assertThat(mapper.getConfig().get("key1"), is("value1"));

            mapper.getConfig().put("key2", "value2");

            realm.updateIdentityProviderMapper(mapper);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            IdentityProviderMapperModel mapper = realm.getIdentityProviderMapperByName("testIdp", "test");
            assertThat(mapper.getName(), is("test"));
            assertThat(mapper.getIdentityProviderMapper(), is("username"));
            assertThat(mapper.getIdentityProviderAlias(), is("testIdp"));
            assertThat(mapper.getConfig().entrySet(), hasSize(2));
            assertThat(mapper.getConfig().get("key1"), is("value1"));
            assertThat(mapper.getConfig().get("key2"), is("value2"));

            realm.removeIdentityProviderMapper(mapper);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(
                    realm.getIdentityProviderMappersByAliasStream("testIdp").collect(Collectors.toList()), hasSize(0));
            assertThat(realm.getIdentityProviderMappersStream().collect(Collectors.toList()), hasSize(0));

            return null;
        });
    }

    @TestOnServer
    public void testClientInitialAccesses(KeycloakSession testSession) {

        String modelId = withRealm(testSession, REALM_NAME, (s, realm) -> realm.createClientInitialAccessModel(60, 2)
                .getId());

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            ClientInitialAccessModel clientInitialAccessModel = realm.getClientInitialAccessModel(modelId);
            assertThat(clientInitialAccessModel.getCount(), is(2));
            assertThat(clientInitialAccessModel.getRemainingCount(), is(2));

            realm.decreaseRemainingCount(clientInitialAccessModel);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            List<ClientInitialAccessModel> clientInitialAccessModels =
                    realm.getClientInitialAccesses().collect(Collectors.toList());
            assertThat(clientInitialAccessModels, hasSize(1));
            assertThat(clientInitialAccessModels.get(0).getCount(), is(2));
            assertThat(clientInitialAccessModels.get(0).getRemainingCount(), is(1));

            realm.removeClientInitialAccessModel(modelId);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getClientInitialAccesses().collect(Collectors.toList()), hasSize(0));

            realm.createClientInitialAccessModel(200, 2);

            try {
                Time.setOffset(201);
                s.realms().removeExpiredClientInitialAccess();
            } finally {
                Time.setOffset(0);
            }

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getClientInitialAccesses().collect(Collectors.toList()), hasSize(0));

            realm.createClientInitialAccessModel(3, 2);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getClientInitialAccesses().collect(Collectors.toList()), hasSize(0));

            return null;
        });
    }

    @TestOnServer
    public void testComponents(KeycloakSession testSession) {

        String componentId = withRealm(testSession, REALM_NAME, (s, realm) -> {
            ComponentModel component = new ComponentModel();
            component.setName("test");
            component.setParentId("testParent");
            component.setProviderType(KeyProvider.class.getName());
            component.setProviderId("aes-generated");
            component.setConfig(new MultivaluedHashMap<>(Map.of("key1", List.of("value1", "value2"))));

            return realm.addComponentModel(component).getId();
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            ComponentModel model = realm.getComponent(componentId);
            assertThat(model.getName(), is("test"));
            assertThat(model.getParentId(), is("testParent"));
            assertThat(model.getProviderType(), is(KeyProvider.class.getName()));
            assertThat(model.getProviderId(), is("aes-generated"));
            assertThat(model.getConfig().get("key1"), is(List.of("value1", "value2")));

            List<RealmModel> realms = s.realms()
                    .getRealmsWithProviderTypeStream(KeyProvider.class)
                    .collect(Collectors.toList());
            assertThat(realms.stream().map(RealmModel::getId).collect(Collectors.toSet()), hasItem(realm.getId()));

            model.getConfig().put("key1", List.of("value1", "value3"));

            realm.updateComponent(model);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            ComponentModel model =
                    realm.getComponentsStream("testParent").findFirst().orElse(null);
            assertThat(model.getName(), is("test"));
            assertThat(model.getParentId(), is("testParent"));
            assertThat(model.getProviderType(), is(KeyProvider.class.getName()));
            assertThat(model.getProviderId(), is("aes-generated"));
            assertThat(model.getConfig().get("key1"), is(List.of("value1", "value3")));

            realm.removeComponent(model);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getComponentsStream("testParent").collect(Collectors.toList()), hasSize(0));
            assertThat(
                    realm.getComponentsStream("testParent", KeyProvider.class.getName())
                            .collect(Collectors.toList()),
                    hasSize(0));

            ComponentModel component = new ComponentModel();
            component.setName("test");
            component.setParentId("testParent");
            component.setProviderType(KeyProvider.class.getName());
            component.setProviderId("aes-generated");
            component.setConfig(new MultivaluedHashMap<>(Map.of("key1", List.of("value1", "value2"))));

            realm.addComponentModel(component);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            realm.removeComponents("testParent");

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getComponentsStream("testParent").collect(Collectors.toList()), hasSize(0));
            assertThat(
                    realm.getComponentsStream("testParent", KeyProvider.class.getName())
                            .collect(Collectors.toList()),
                    hasSize(0));

            return null;
        });
    }

    @TestOnServer
    public void testActionTokens(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            realm.setActionTokenGeneratedByUserLifespan(42);
            realm.setActionTokenGeneratedByAdminLifespan(43);
            realm.setActionTokenGeneratedByUserLifespan("myTokenType", 100);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getActionTokenGeneratedByUserLifespan(), is(42));
            assertThat(realm.getActionTokenGeneratedByAdminLifespan(), is(43));
            assertThat(realm.getUserActionTokenLifespans().get("myTokenType"), is(100));
            assertThat(realm.getActionTokenGeneratedByUserLifespan("myTokenType"), is(100));

            return null;
        });
    }

    @TestOnServer
    public void testRequiredCredentials(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThrows(RuntimeException.class, () -> realm.addRequiredCredential("unknown"));
            realm.addRequiredCredential(RequiredCredentialModel.KERBEROS.getType());

            assertThrows(
                    ModelDuplicateException.class,
                    () -> realm.addRequiredCredential(RequiredCredentialModel.KERBEROS.getType()));

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            List<RequiredCredentialModel> kerberosCredentials = realm.getRequiredCredentialsStream()
                    .filter(credential -> credential.getType().equals(RequiredCredentialModel.KERBEROS.getType()))
                    .collect(Collectors.toList());
            assertThat(kerberosCredentials, hasSize(1));

            RequiredCredentialModel requiredCredentialModel = kerberosCredentials.get(0);
            assertThat(requiredCredentialModel.getType(), is(RequiredCredentialModel.KERBEROS.getType()));
            assertThat(requiredCredentialModel.getFormLabel(), is(RequiredCredentialModel.KERBEROS.getFormLabel()));
            assertThat(requiredCredentialModel.isInput(), is(RequiredCredentialModel.KERBEROS.isInput()));
            assertThat(requiredCredentialModel.isSecret(), is(RequiredCredentialModel.KERBEROS.isSecret()));

            RequiredCredentialModel.KERBEROS.setInput(true);
            RequiredCredentialModel.KERBEROS.setSecret(true);
            RequiredCredentialModel.KERBEROS.setFormLabel("changed");
            realm.updateRequiredCredentials(Set.of(RequiredCredentialModel.KERBEROS.getType()));

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            List<RequiredCredentialModel> kerberosCredentials = realm.getRequiredCredentialsStream()
                    .filter(credential -> credential.getType().equals(RequiredCredentialModel.KERBEROS.getType()))
                    .collect(Collectors.toList());
            assertThat(kerberosCredentials, hasSize(1));

            RequiredCredentialModel requiredCredentialModel = kerberosCredentials.get(0);
            assertThat(requiredCredentialModel.getType(), is(RequiredCredentialModel.KERBEROS.getType()));
            assertThat(requiredCredentialModel.getFormLabel(), is("changed"));
            assertThat(requiredCredentialModel.isInput(), is(true));
            assertThat(requiredCredentialModel.isSecret(), is(true));

            return null;
        });
    }

    @TestOnServer
    public void testUpdateRequiredCredentialsUnknownType(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            RuntimeException e =
                    assertThrows(RuntimeException.class, () -> realm.updateRequiredCredentials(Set.of("unknown")));
            assertThat(e.getMessage(), containsString("Unknown credential type unknown"));
            return null;
        });
    }

    @TestOnServer
    public void testMasterAdminClient(KeycloakSession testSession) {

        String masterRealmId = inCommittedTransaction(testSession, (Function<KeycloakSession, String>)
                s -> s.realms().getRealmByName("master").getId());
        String previousMasterAdminClientId = withRealm(testSession, masterRealmId, (s, realm) -> {
            ClientModel previousMasterAdminClient = realm.getMasterAdminClient();
            ClientModel client = s.clients().addClient(realm, "adminClient");
            realm.setMasterAdminClient(client);

            return previousMasterAdminClient == null ? null : previousMasterAdminClient.getId();
        });

        withRealm(testSession, masterRealmId, (s, realm) -> {
            ClientModel masterAdminClient = realm.getMasterAdminClient();
            assertThat(masterAdminClient.getClientId(), is("adminClient"));

            return null;
        });

        withRealm(testSession, masterRealmId, (s, realm) -> {
            ClientModel adminClient = realm.getClientByClientId("adminClient");
            realm.setMasterAdminClient(
                    previousMasterAdminClientId == null ? null : realm.getClientById(previousMasterAdminClientId));
            s.clients().removeClient(realm, adminClient.getId());
            return null;
        });
    }

    @TestOnServer
    public void testDefaultGroup(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            GroupModel group = s.groups().createGroup(realm, "myGroup");
            realm.addDefaultGroup(group);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getDefaultGroupsStream().collect(Collectors.toList()), hasSize(1));
            assertThat(
                    realm.getDefaultGroupsStream()
                            .map(GroupModel::getName)
                            .findFirst()
                            .orElse(null),
                    is("myGroup"));

            realm.removeDefaultGroup(
                    realm.getDefaultGroupsStream().collect(Collectors.toList()).get(0));
            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(realm.getDefaultGroupsStream().collect(Collectors.toList()), hasSize(0));
            return null;
        });
    }

    @TestOnServer
    public void testDefaultClientScope(KeycloakSession testSession) {

        List<String> clientScopeIds = new ArrayList<>();
        withRealm(testSession, REALM_NAME, (s, realm) -> {
            ClientScopeModel clientScope = s.clientScopes().addClientScope(realm, "myClientScope");
            clientScopeIds.add(clientScope.getId());
            realm.addDefaultClientScope(clientScope, true);

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(
                    realm.getDefaultClientScopesStream(true)
                            .map(ClientScopeModel::getName)
                            .collect(Collectors.toList()),
                    hasItem("myClientScope"));

            realm.removeDefaultClientScope(s.clientScopes().getClientScopeById(realm, clientScopeIds.get(0)));
            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(
                    realm.getDefaultClientScopesStream(true)
                            .map(ClientScopeModel::getName)
                            .collect(Collectors.toList()),
                    not(hasItem("myClientScope")));

            return null;
        });
    }

    @TestOnServer
    public void testProperties(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            realm.setPasswordPolicy(PasswordPolicy.parse(s, PasswordPolicy.PASSWORD_HISTORY_ID));
            realm.setAccountTheme("myTheme");
            realm.setAdminTheme("myTheme");
            realm.setEmailTheme("myTheme");
            realm.setEventsExpiration(42L);
            realm.setDefaultLocale("en-US");

            return null;
        });

        withRealm(testSession, REALM_NAME, (s, realm) -> {
            assertThat(
                    realm.getPasswordPolicy().toString(),
                    is(PasswordPolicy.parse(s, PasswordPolicy.PASSWORD_HISTORY_ID)
                            .toString()));
            assertThat(realm.getAccountTheme(), is("myTheme"));
            assertThat(realm.getAdminTheme(), is("myTheme"));
            assertThat(realm.getEmailTheme(), is("myTheme"));
            assertThat(realm.getEventsExpiration(), is(42L));
            assertThat(realm.getDefaultLocale(), is("en-US"));

            return null;
        });
    }
}
