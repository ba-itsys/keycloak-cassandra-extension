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
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import lombok.extern.jbosslog.JBossLog;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.util.concurrent.BlockingManager;
import org.keycloak.Config;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.connections.infinispan.InfinispanConnectionProviderFactory;
import org.keycloak.connections.infinispan.NodeInfo;
import org.keycloak.connections.infinispan.TopologyInfo;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

@JBossLog
@AutoService(InfinispanConnectionProviderFactory.class)
public class NullInfinispanConnectionProviderFactory
        implements InfinispanConnectionProviderFactory,
                EnvironmentDependentProviderFactory,
                ServerInfoAwareProviderFactory {
    @Override
    public boolean isSupported(Config.Scope config) {
        return isCassandraProfileEnabled() || isCassandraCacheProfileEnabled();
    }

    @Override
    public InfinispanConnectionProvider create(KeycloakSession session) {
        return createProviderCached(
                session, InfinispanConnectionProvider.class, () -> new InfinispanConnectionProvider() {
                    @Override
                    public <K, V> Cache<K, V> getCache(String s) {
                        return null;
                    }

                    @Override
                    public <K, V> Cache<K, V> getCache(String s, boolean b) {
                        return null;
                    }

                    @Override
                    public <K, V> RemoteCache<K, V> getRemoteCache(String s) {
                        return null;
                    }

                    @Override
                    public TopologyInfo getTopologyInfo() {
                        return null;
                    }

                    @Override
                    public CompletionStage<Void> migrateToProtoStream() {
                        return null;
                    }

                    @Override
                    public ScheduledExecutorService getScheduledExecutor() {
                        return null;
                    }

                    @Override
                    public BlockingManager getBlockingManager() {
                        return null;
                    }

                    @Override
                    public NodeInfo getNodeInfo() {
                        return null;
                    }

                    @Override
                    public void close() {}
                });
    }

    @Override
    public void init(Config.Scope config) {
        log.info("Infinispan deactivated...");
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
    public Map<String, String> getOperationalInfo() {
        return Map.of("implementation", "deactivated (cassandra-extension)");
    }
}
