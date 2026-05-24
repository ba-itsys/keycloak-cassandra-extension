package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra;

import org.keycloak.common.Profile;
import org.keycloak.testframework.server.KeycloakServerConfig;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

public class CassandraKeycloakServerConfig implements KeycloakServerConfig {

    @Override
    public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
        return config.dependency("de.arbeitsagentur.opdt", "keycloak-cassandra-extension")
                .featuresDisabled(Profile.Feature.AUTHORIZATION, Profile.Feature.ORGANIZATION)
                .option(
                        "feature-admin-fine-grained-authz",
                        "disabled") // multi version feature cannot be disabled by featuresDisabled()
                .spiOption("realm", "jpa", "enabled", "false");
    }
}
