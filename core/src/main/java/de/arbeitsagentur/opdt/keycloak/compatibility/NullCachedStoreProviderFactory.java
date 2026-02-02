/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.arbeitsagentur.opdt.keycloak.compatibility;

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.authorization.model.*;
import org.keycloak.authorization.store.*;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.cache.authorization.CachedStoreFactoryProvider;
import org.keycloak.models.cache.authorization.CachedStoreProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.keycloak.representations.idm.authorization.AbstractPolicyRepresentation;

@JBossLog
@AutoService(CachedStoreProviderFactory.class)
public class NullCachedStoreProviderFactory
        implements CachedStoreProviderFactory, EnvironmentDependentProviderFactory, ServerInfoAwareProviderFactory {
    @Override
    public CachedStoreFactoryProvider create(KeycloakSession session) {
        return createProviderCached(session, CachedStoreFactoryProvider.class, () -> new CachedStoreFactoryProvider() {
            @Override
            public ResourceStore getResourceStore() {
                return new ResourceStore() {

                    @Override
                    public Resource create(ResourceServer resourceServer, String id, String name, String owner) {
                        return null;
                    }

                    @Override
                    public void delete(String id) {}

                    @Override
                    public Resource findById(ResourceServer resourceServer, String id) {
                        return null;
                    }

                    @Override
                    public void findByOwner(
                            ResourceServer resourceServer, String ownerId, Consumer<Resource> consumer) {}

                    @Override
                    public List<Resource> findByResourceServer(ResourceServer resourceServer) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Resource> find(
                            ResourceServer resourceServer,
                            Map<Resource.FilterOption, String[]> attributes,
                            Integer firstResult,
                            Integer maxResults) {
                        return Collections.emptyList();
                    }

                    @Override
                    public void findByScopes(
                            ResourceServer resourceServer, Set<Scope> scopes, Consumer<Resource> consumer) {}

                    @Override
                    public Resource findByName(ResourceServer resourceServer, String name, String ownerId) {
                        return null;
                    }

                    @Override
                    public void findByType(ResourceServer resourceServer, String type, Consumer<Resource> consumer) {}

                    @Override
                    public void findByType(
                            ResourceServer resourceServer, String type, String owner, Consumer<Resource> consumer) {}

                    @Override
                    public void findByTypeInstance(
                            ResourceServer resourceServer, String type, Consumer<Resource> consumer) {}
                };
            }

            @Override
            public ResourceServerStore getResourceServerStore() {
                return new ResourceServerStore() {

                    @Override
                    public ResourceServer create(ClientModel client) {
                        return null;
                    }

                    @Override
                    public void delete(ClientModel client) {}

                    @Override
                    public ResourceServer findById(String id) {
                        return null;
                    }

                    @Override
                    public ResourceServer findByClient(ClientModel client) {
                        return null;
                    }
                };
            }

            @Override
            public ScopeStore getScopeStore() {
                return new ScopeStore() {

                    @Override
                    public Scope create(ResourceServer resourceServer, String id, String name) {
                        return null;
                    }

                    @Override
                    public void delete(String id) {}

                    @Override
                    public Scope findById(ResourceServer resourceServer, String id) {
                        return null;
                    }

                    @Override
                    public Scope findByName(ResourceServer resourceServer, String name) {
                        return null;
                    }

                    @Override
                    public List<Scope> findByResourceServer(ResourceServer resourceServer) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Scope> findByResourceServer(
                            ResourceServer resourceServer,
                            Map<Scope.FilterOption, String[]> attributes,
                            Integer firstResult,
                            Integer maxResults) {
                        return Collections.emptyList();
                    }
                };
            }

            @Override
            public PolicyStore getPolicyStore() {
                return new PolicyStore() {

                    @Override
                    public Policy create(ResourceServer resourceServer, AbstractPolicyRepresentation representation) {
                        return null;
                    }

                    @Override
                    public void delete(String id) {}

                    @Override
                    public Policy findById(ResourceServer resourceServer, String id) {
                        return null;
                    }

                    @Override
                    public Policy findByName(ResourceServer resourceServer, String name) {
                        return null;
                    }

                    @Override
                    public List<Policy> findByResourceServer(ResourceServer resourceServer) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Policy> find(
                            ResourceServer resourceServer,
                            Map<Policy.FilterOption, String[]> attributes,
                            Integer firstResult,
                            Integer maxResults) {
                        return Collections.emptyList();
                    }

                    @Override
                    public void findByResource(
                            ResourceServer resourceServer, Resource resource, Consumer<Policy> consumer) {}

                    @Override
                    public void findByResourceType(
                            ResourceServer resourceServer, String type, Consumer<Policy> policyConsumer) {}

                    @Override
                    public List<Policy> findByScopes(ResourceServer resourceServer, List<Scope> scopes) {
                        return Collections.emptyList();
                    }

                    @Override
                    public void findByScopes(
                            ResourceServer resourceServer,
                            Resource resource,
                            List<Scope> scopes,
                            Consumer<Policy> consumer) {}

                    @Override
                    public List<Policy> findByType(ResourceServer resourceServer, String type) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Policy> findDependentPolicies(ResourceServer resourceServer, String id) {
                        return Collections.emptyList();
                    }

                    @Override
                    public Stream<Policy> findDependentPolicies(
                            ResourceServer resourceServer,
                            String id,
                            String resourceId,
                            String scopeId,
                            String resourceType,
                            String owner) {
                        return Stream.empty();
                    }

                    @Override
                    public Stream<Policy> findDependentPolicies(
                            ResourceServer resourceServer,
                            String id,
                            String resourceId,
                            String scopeId,
                            String resourceType,
                            List<String> owners) {
                        return Stream.empty();
                    }
                };
            }

            @Override
            public PermissionTicketStore getPermissionTicketStore() {
                return new PermissionTicketStore() {

                    @Override
                    public long count(
                            ResourceServer resourceServer, Map<PermissionTicket.FilterOption, String> attributes) {
                        return 0;
                    }

                    @Override
                    public PermissionTicket create(
                            ResourceServer resourceServer, Resource resource, Scope scope, String requester) {
                        return null;
                    }

                    @Override
                    public void delete(String id) {}

                    @Override
                    public PermissionTicket findById(ResourceServer resourceServer, String id) {
                        return null;
                    }

                    @Override
                    public List<PermissionTicket> findByResource(ResourceServer resourceServer, Resource resource) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PermissionTicket> findByScope(ResourceServer resourceServer, Scope scope) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PermissionTicket> find(
                            ResourceServer resourceServer,
                            Map<PermissionTicket.FilterOption, String> attributes,
                            Integer firstResult,
                            Integer maxResults) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PermissionTicket> findGranted(ResourceServer resourceServer, String userId) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<PermissionTicket> findGranted(
                            ResourceServer resourceServer, String resourceName, String userId) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Resource> findGrantedResources(
                            String requester, String name, Integer firstResult, Integer maxResults) {
                        return Collections.emptyList();
                    }

                    @Override
                    public List<Resource> findGrantedOwnerResources(
                            String owner, Integer firstResult, Integer maxResults) {
                        return Collections.emptyList();
                    }
                };
            }

            @Override
            public void setReadOnly(boolean readOnly) {}

            @Override
            public boolean isReadOnly() {
                return true;
            }

            @Override
            public void close() {}
        });
    }

    @Override
    public void init(Config.Scope config) {
        log.info("Authorization-Cache deactivated...");
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }

    @Override
    public String getId() {
        return "default";
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return isCassandraProfileEnabled() || isCassandraCacheProfileEnabled();
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        return Map.of("implementation", "deactivated (cassandra-extension)");
    }
}
