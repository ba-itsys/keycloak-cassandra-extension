package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class CassandraStorageModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "cassandra-storage";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD)
    ManagedRealm realm;

    @TestOnServer
    public void testRealmsAreStoredInCassandra(KeycloakSession testSession) {
        runJobInTransaction(testSession.getKeycloakSessionFactory(), session -> {
            CassandraConnectionProvider connectionProvider = session.getProvider(CassandraConnectionProvider.class);
            CqlSession cqlSession = connectionProvider.getCqlSession();
            RealmModel realm = session.realms().getRealmByName(REALM_NAME);
            if (realm == null) {
                throw new AssertionError("Injected realm " + REALM_NAME + " was not created");
            }
            Row row = cqlSession
                    .execute("SELECT id, name FROM realms WHERE id = ?", realm.getId())
                    .one();
            if (row == null || !realm.getName().equals(row.getString("name"))) {
                throw new AssertionError("Injected realm " + REALM_NAME + " was not stored in Cassandra");
            }
        });
    }
}
