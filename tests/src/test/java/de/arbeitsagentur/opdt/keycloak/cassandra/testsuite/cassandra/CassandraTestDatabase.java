package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.DefaultCassandraConnectionProviderFactory;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.keycloak.testframework.database.DatabaseConfiguration;
import org.keycloak.testframework.database.TestDatabase;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class CassandraTestDatabase implements TestDatabase {

    public static final String NAME = "cassandra";

    private static final int CQL_PORT = 9042;

    private final boolean startContainer;
    private final String image;
    private final String configuredContactPoints;
    private final int configuredPort;
    private final String localDatacenter;
    private final String username;
    private final String password;
    private final int replicationFactor;

    private GenericContainer<?> container;
    private String contactPoints;
    private int port;
    private String keyspace;

    public CassandraTestDatabase(
            boolean startContainer,
            String image,
            String contactPoints,
            int port,
            String localDatacenter,
            String username,
            String password,
            int replicationFactor) {
        this.startContainer = startContainer;
        this.image = image;
        this.configuredContactPoints = contactPoints;
        this.configuredPort = port;
        this.localDatacenter = localDatacenter;
        this.username = username;
        this.password = password;
        this.replicationFactor = replicationFactor;
    }

    @Override
    public void start(DatabaseConfiguration databaseConfiguration) {
        keyspace = databaseConfiguration.getDatabase() != null ? databaseConfiguration.getDatabase() : "keycloak";

        if (startContainer) {
            container = new GenericContainer<>(DockerImageName.parse(image))
                    .withExposedPorts(CQL_PORT)
                    .withEnv("CASSANDRA_DC", localDatacenter)
                    .waitingFor(new LogMessageWaitStrategy()
                            .withRegEx(".*Starting listening for CQL clients.*")
                            .withStartupTimeout(Duration.of(2, ChronoUnit.MINUTES)));
            container.start();
            contactPoints = container.getHost();
            port = container.getMappedPort(CQL_PORT);
        } else {
            contactPoints = configuredContactPoints;
            port = configuredPort;
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }

    @Override
    public Map<String, String> serverConfig() {
        String prefix = "spi-cassandra-connection--" + DefaultCassandraConnectionProviderFactory.PROVIDER_ID + "--";
        Map<String, String> options = new LinkedHashMap<>();
        options.put(prefix + "contact-points", contactPoints);
        options.put(prefix + "port", Integer.toString(port));
        options.put(prefix + "local-datacenter", localDatacenter);
        options.put(prefix + "keyspace", keyspace);
        options.put(prefix + "username", username);
        options.put(prefix + "password", password);
        options.put(prefix + "replication-factor", Integer.toString(replicationFactor));
        return options;
    }

    public CqlSession openSession() {
        return CqlSession.builder()
                .addContactPoint(InetSocketAddress.createUnresolved(contactPoints, port))
                .withLocalDatacenter(localDatacenter)
                .withAuthCredentials(username, password)
                .withKeyspace(keyspace)
                .build();
    }
}
