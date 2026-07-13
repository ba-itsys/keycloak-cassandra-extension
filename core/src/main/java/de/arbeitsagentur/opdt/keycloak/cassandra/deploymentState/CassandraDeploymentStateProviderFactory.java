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
package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState;

import static de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.isAreaEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.Area;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.common.Version;
import org.keycloak.migration.MigrationModel;
import org.keycloak.models.DeploymentStateProvider;
import org.keycloak.models.DeploymentStateProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

@JBossLog
@AutoService(DeploymentStateProviderFactory.class)
public class CassandraDeploymentStateProviderFactory
        implements DeploymentStateProviderFactory, EnvironmentDependentProviderFactory, ServerInfoAwareProviderFactory {

    public static final String PROVIDER_ID = "jpa";

    private static final String RESOURCES_VERSION_SEED = "resourcesVersionSeed";
    private static final String DEPLOYMENT_STATE_ID = "deployment-state";

    private volatile String configuredSeed;

    private static final DeploymentStateProvider INSTANCE = new DeploymentStateProvider() {

        private final MigrationModel INSTANCE = new MigrationModel() {
            @Override
            public String getStoredVersion() {
                return null;
            }

            @Override
            public String getResourcesTag() {
                // No relational MIGRATION_MODEL table: reflect the seed-derived tag this factory
                // publishes to Version.RESOURCES_VERSION. Non-throwing so it degrades gracefully if
                // ever selected under a CR-migration driver (e.g. paired with another datastore).
                return Version.RESOURCES_VERSION;
            }

            @Override
            public void setStoredVersion(String version) {
                // No MIGRATION_MODEL table to persist to; the resources tag is seed-derived instead.
            }
        };

        @Override
        public MigrationModel getMigrationModel() {
            return INSTANCE;
        }

        @Override
        public void close() {}
    };

    @Override
    public DeploymentStateProvider create(KeycloakSession session) {
        return createProviderCached(session, DeploymentStateProvider.class, () -> INSTANCE);
    }

    @Override
    public void init(Config.Scope config) {
        configuredSeed = config.get(RESOURCES_VERSION_SEED);
        if (configuredSeed != null) {
            setResourcesVersion(configuredSeed);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        String seed = configuredSeed;
        if (seed == null) {
            AtomicReference<String> generated = new AtomicReference<>();
            KeycloakModelUtils.runJobInTransaction(factory, session -> {
                CassandraConnectionProvider connection =
                        createProviderCached(session, CassandraConnectionProvider.class);
                generated.set(connection.getRepository().getOrCreateResourcesVersionSeed(DEPLOYMENT_STATE_ID));
            });
            seed = generated.get();
        }
        setResourcesVersion(seed);

        String resolvedSeed = seed;
        factory.register(event -> {
            if (event instanceof PostMigrationEvent) {
                setResourcesVersion(resolvedSeed);
            }
        });
    }

    private void setResourcesVersion(String seed) {
        try {
            Version.RESOURCES_VERSION = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest((seed + Version.VERSION).getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 5);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void close() {}

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        // Deployment-state (the resources-version owner) follows the realm area: active in full
        // cassandra mode, dormant when cassandra serves no realms (pure JPA, or paired with another
        // datastore that owns realms and its own deployment-state).
        return isAreaEnabled(Area.REALM);
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        return Map.of("implementation", "cassandra (cassandra-extension)");
    }
}
