package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra;

import org.keycloak.common.Profile;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;

public class CassandraTransientUsersKeycloakServerConfig extends CassandraKeycloakServerConfig {

    @Override
    public KeycloakServerConfigBuilder configure(KeycloakServerConfigBuilder config) {
        return super.configure(config).features(Profile.Feature.TRANSIENT_USERS);
    }
}
