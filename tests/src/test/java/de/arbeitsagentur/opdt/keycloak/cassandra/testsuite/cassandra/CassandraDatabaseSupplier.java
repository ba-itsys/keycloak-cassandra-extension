package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra;

import org.keycloak.testframework.annotations.InjectTestDatabase;
import org.keycloak.testframework.database.DatabaseConfig;
import org.keycloak.testframework.database.DatabaseConfigBuilder;
import org.keycloak.testframework.database.DatabaseConfiguration;
import org.keycloak.testframework.database.TestDatabase;
import org.keycloak.testframework.injection.InstanceContext;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.injection.RequestedInstance;
import org.keycloak.testframework.injection.Supplier;
import org.keycloak.testframework.injection.SupplierHelpers;
import org.keycloak.testframework.injection.SupplierOrder;
import org.keycloak.testframework.server.KeycloakServerConfigBuilder;
import org.keycloak.testframework.server.KeycloakServerConfigInterceptor;

public class CassandraDatabaseSupplier
        implements Supplier<TestDatabase, InjectTestDatabase>,
                KeycloakServerConfigInterceptor<TestDatabase, InjectTestDatabase> {

    private static final String START_CONTAINER = "keycloak.test.cassandra.start-container";
    private static final String IMAGE = "keycloak.test.cassandra.image";
    private static final String CONTACT_POINTS = "keycloak.test.cassandra.contact-points";
    private static final String PORT = "keycloak.test.cassandra.port";
    private static final String LOCAL_DATACENTER = "keycloak.test.cassandra.local-datacenter";
    private static final String USERNAME = "keycloak.test.cassandra.username";
    private static final String PASSWORD = "keycloak.test.cassandra.password";
    private static final String REPLICATION_FACTOR = "keycloak.test.cassandra.replication-factor";

    @Override
    public String getAlias() {
        return CassandraTestDatabase.NAME;
    }

    @Override
    public TestDatabase getValue(InstanceContext<TestDatabase, InjectTestDatabase> instanceContext) {
        DatabaseConfigBuilder builder =
                DatabaseConfigBuilder.create().preventReuse(instanceContext.getLifeCycle() != LifeCycle.GLOBAL);

        DatabaseConfig config =
                SupplierHelpers.getInstance(instanceContext.getAnnotation().config());
        DatabaseConfiguration databaseConfiguration = config.configure(builder).build();

        TestDatabase database = new CassandraTestDatabase(
                Boolean.parseBoolean(System.getProperty(START_CONTAINER, "true")),
                System.getProperty(IMAGE, "cassandra:5.0.3"),
                System.getProperty(CONTACT_POINTS, "localhost"),
                Integer.parseInt(System.getProperty(PORT, "9042")),
                System.getProperty(LOCAL_DATACENTER, "datacenter1"),
                System.getProperty(USERNAME, "cassandra"),
                System.getProperty(PASSWORD, "cassandra"),
                Integer.parseInt(System.getProperty(REPLICATION_FACTOR, "1")));
        database.start(databaseConfiguration);
        return database;
    }

    @Override
    public boolean compatible(
            InstanceContext<TestDatabase, InjectTestDatabase> current,
            RequestedInstance<TestDatabase, InjectTestDatabase> requested) {
        return current.getAnnotation().config().equals(requested.getAnnotation().config());
    }

    @Override
    public LifeCycle getDefaultLifecycle() {
        return LifeCycle.GLOBAL;
    }

    @Override
    public void close(InstanceContext<TestDatabase, InjectTestDatabase> instanceContext) {
        instanceContext.getValue().stop();
    }

    @Override
    public KeycloakServerConfigBuilder intercept(
            KeycloakServerConfigBuilder serverConfig,
            InstanceContext<TestDatabase, InjectTestDatabase> instanceContext) {
        return serverConfig.options(instanceContext.getValue().serverConfig());
    }

    @Override
    public int order() {
        return SupplierOrder.BEFORE_KEYCLOAK_SERVER;
    }
}
