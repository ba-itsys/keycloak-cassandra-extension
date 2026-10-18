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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderQuery;
import org.keycloak.models.IdentityProviderStorageProvider.FetchMode;
import org.keycloak.models.KeycloakSession;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class IdentityProviderStorageModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "idp-storage";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD)
    ManagedRealm managedRealm;

    @TestOnServer
    public void getForLoginAppliesLoginFilter(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.identityProviders().create(idp("visible"));
            IdentityProviderModel disabled = idp("disabled");
            disabled.setEnabled(false);
            session.identityProviders().create(disabled);
            IdentityProviderModel hidden = idp("hidden");
            hidden.setHideOnLogin(true);
            session.identityProviders().create(hidden);
            IdentityProviderModel linkOnly = idp("link-only");
            linkOnly.setLinkOnly(true);
            session.identityProviders().create(linkOnly);
            IdentityProviderModel orgLinked = idp("org-linked");
            orgLinked.setOrganizationIds(Set.of("org-1"));
            session.identityProviders().create(orgLinked);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(
                    aliases(session.identityProviders().getForLogin(FetchMode.REALM_ONLY, null)), contains("visible"));
            assertThat(
                    aliases(session.identityProviders().getForLogin(FetchMode.ORG_ONLY, null)), contains("org-linked"));
            assertThat(aliases(session.identityProviders().getForLogin(FetchMode.ORG_ONLY, "org-2")), empty());
            return null;
        });
    }

    @TestOnServer
    public void getByOrganizationSupportsMultipleOrganizations(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            IdentityProviderModel shared = idp("shared");
            shared.setOrganizationIds(Set.of("org-1", "org-2"));
            session.identityProviders().create(shared);
            IdentityProviderModel single = idp("single");
            single.setOrganizationIds(Set.of("org-1"));
            session.identityProviders().create(single);
            session.identityProviders().create(idp("unlinked"));
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(
                    aliases(session.identityProviders().getByOrganization("org-1", null, null)),
                    contains("shared", "single"));
            assertThat(aliases(session.identityProviders().getByOrganization("org-2", null, null)), contains("shared"));
            assertThat(aliases(session.identityProviders().getByOrganization(null, null, null)), contains("unlinked"));
            return null;
        });
    }

    @TestOnServer
    public void getAllStreamFiltersByTypeSearchAndPagination(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.identityProviders().create(idp("alpha"));
            IdentityProviderModel beta = idp("beta");
            beta.setDisplayName("Corporate Login");
            session.identityProviders().create(beta);
            session.identityProviders().create(idp("alphabet"));
            IdentityProviderModel unknownType = idp("alpha-unknown");
            unknownType.setProviderId("not-a-registered-factory");
            session.identityProviders().create(unknownType);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            assertThat(
                    aliases(session.identityProviders().getAllStream(IdentityProviderQuery.userAuthentication())),
                    contains("alpha", "alphabet", "beta"));
            assertThat(
                    aliases(session.identityProviders().getAllStream(IdentityProviderQuery.any())),
                    contains("alpha", "alpha-unknown", "alphabet", "beta"));
            assertThat(aliases(search(session, "ALPHA")), contains("alpha", "alphabet"));
            assertThat(aliases(search(session, "\"alpha\"")), contains("alpha"));
            assertThat(aliases(search(session, "bet")), contains("beta"));
            assertThat(aliases(search(session, "*bet")), contains("alphabet", "beta"));
            assertThat(aliases(search(session, "corporate")), contains("beta"));
            assertThat(
                    aliases(session.identityProviders()
                            .getAllStream(
                                    IdentityProviderQuery.userAuthentication()
                                            .with(IdentityProviderModel.ALIAS_NOT_IN, "alpha,beta"),
                                    null,
                                    null)),
                    contains("alphabet"));
            assertThat(
                    aliases(session.identityProviders().getAllStream(IdentityProviderQuery.userAuthentication(), 1, 1)),
                    contains("alphabet"));
            return null;
        });
    }

    @TestOnServer
    public void updatePersistsHideOnLoginAndOrganizations(KeycloakSession testSession) {
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.identityProviders().create(idp("updatable"));
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            IdentityProviderModel model = session.identityProviders().getByAlias("updatable");
            model.setHideOnLogin(true);
            model.setOrganizationIds(Set.of("org-1"));
            session.identityProviders().update(model);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            IdentityProviderModel model = session.identityProviders().getByAlias("updatable");
            assertTrue(model.isHideOnLogin());
            assertEquals(Set.of("org-1"), model.getOrganizationIds());
            return null;
        });
    }

    private static Stream<IdentityProviderModel> search(KeycloakSession session, String search) {
        return session.identityProviders()
                .getAllStream(
                        IdentityProviderQuery.userAuthentication().with(IdentityProviderModel.SEARCH, search),
                        null,
                        null);
    }

    private static List<String> aliases(Stream<IdentityProviderModel> idps) {
        return idps.map(IdentityProviderModel::getAlias).toList();
    }

    private static IdentityProviderModel idp(String alias) {
        IdentityProviderModel model = new IdentityProviderModel();
        model.setAlias(alias);
        model.setProviderId("oidc");
        model.setEnabled(true);
        return model;
    }
}
