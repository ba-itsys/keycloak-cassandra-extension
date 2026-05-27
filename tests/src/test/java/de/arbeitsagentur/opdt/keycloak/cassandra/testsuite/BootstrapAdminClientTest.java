package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientAdapter.CLIENT_ID;
import static de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientAdapter.FULL_SCOPE_ALLOWED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.datastax.oss.driver.api.core.cql.Row;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraTestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.testframework.annotations.InjectAdminClient;
import org.keycloak.testframework.annotations.InjectTestDatabase;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
class BootstrapAdminClientTest {

    @InjectAdminClient(mode = InjectAdminClient.Mode.BOOTSTRAP, ref = "bootstrap-client")
    Keycloak adminClient;

    @InjectTestDatabase
    CassandraTestDatabase cassandra;

    @Test
    void bootstrapAdminClientUsesTransientServiceAccountSession() throws Exception {
        AccessToken token = TokenVerifier.create(adminClient.tokenManager().getAccessTokenString(), AccessToken.class)
                .getToken();

        assertThat(token.getSubject(), notNullValue());
        assertThat(token.getSessionId(), nullValue());
        assertThat(
                adminClient.realms().findAll().stream()
                        .map(RealmRepresentation::getRealm)
                        .toList(),
                hasItem("master"));

        try (var session = cassandra.openSession()) {
            Row tempAdminClient = session.execute("SELECT id, attributes FROM clients").all().stream()
                    .filter(row ->
                            attributes(row).getOrDefault(CLIENT_ID, List.of()).contains("temp-admin"))
                    .findFirst()
                    .orElse(null);
            assertThat(tempAdminClient, notNullValue());
            assertThat(attributes(tempAdminClient).getOrDefault(FULL_SCOPE_ALLOWED, List.of()), contains("true"));

            Row serviceAccount =
                    session.execute("SELECT service_account_client_link, realm_roles FROM users").all().stream()
                            .filter(row -> tempAdminClient
                                    .getString("id")
                                    .equals(row.getString("service_account_client_link")))
                            .findFirst()
                            .orElse(null);
            assertThat(serviceAccount, notNullValue());
            assertThat(serviceAccount.getSet("realm_roles", String.class), is(not(empty())));

            assertThat(session.execute("SELECT id FROM user_sessions").one(), nullValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> attributes(Row row) {
        return (Map<String, List<String>>) (Map<?, ?>) row.getMap("attributes", String.class, List.class);
    }
}
