package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra;

import org.keycloak.common.Profile;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

public class CassandraKeycloakServerConfig implements KeycloakServerConfig {

    @Override
    public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
        return config.dependency("de.arbeitsagentur.opdt", "keycloak-cassandra-extension")
                .features(Profile.Feature.STATELESS)
                // Select the cassandra datastore (replaces the former KC_COMMUNITY_DATASTORE_CASSANDRA_ENABLED
                // env var); areas default to all. The realm/authorization cache disables are supplied
                // automatically by CassandraConfigDefaultsSourceFactory, so a green run exercises it.
                .option("spi-datastore--provider", "cassandra")
                .featuresDisabled(Profile.Feature.AUTHORIZATION, Profile.Feature.ORGANIZATION)
                .option(
                        "feature-admin-fine-grained-authz",
                        "disabled") // multi version feature cannot be disabled by featuresDisabled()
                .spiOption("realm", "jpa", "enabled", "false");
    }
}
