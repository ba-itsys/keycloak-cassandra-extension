package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.session;

import static org.junit.Assert.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.CassandraModelTest;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class RefreshTokenRotationTest extends CassandraModelTest {
    private static final String REALM_NAME = "refresh-token-rotation";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = RefreshTokenRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testRefreshTokenRotationMultiTab(KeycloakSession testSession) {
        try {
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                realm.setSsoSessionIdleTimeout(1800);
                realm.setSsoSessionMaxLifespan(36000);
                realm.setClientSessionIdleTimeout(1000);

                realm.setAttribute("refreshTokenReuseInterval", 100);
                realm.setRefreshTokenMaxReuse(0);
                return null;
            });

            String uSId = withRealm(testSession, REALM_NAME, (session, realm) -> {
                UserSessionModel userSession = session.sessions()
                        .createUserSession(
                                realm,
                                session.users().getUserByUsername(realm, "user1"),
                                "user1",
                                "127.0.0.1",
                                "form",
                                true,
                                null,
                                null);

                ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
                session.sessions().createClientSession(realm, testClient, userSession);

                return userSession.getId();
            });

            // Refresh Tab 1
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

                ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
                AuthenticatedClientSessionModel clientSession =
                        userSession.getAuthenticatedClientSessionByClient(testClient.getId());

                assertEquals(0, clientSession.getRefreshTokenUseCount("id1"));

                clientSession.setRefreshTokenUseCount("id1", 1);

                return null;
            });

            // Reuse inside given interval
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

                ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
                AuthenticatedClientSessionModel clientSession =
                        userSession.getAuthenticatedClientSessionByClient(testClient.getId());

                assertEquals(0, clientSession.getRefreshTokenUseCount("id1"));

                clientSession.setRefreshTokenUseCount("id1", 1);

                return null;
            });

            // Reuse outside given interval
            Time.setOffset(101);
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

                ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
                AuthenticatedClientSessionModel clientSession =
                        userSession.getAuthenticatedClientSessionByClient(testClient.getId());

                assertEquals(1, clientSession.getRefreshTokenUseCount("id1"));

                return null;
            });

            // Refresh Tab 2
            withRealm(testSession, REALM_NAME, (session, realm) -> {
                UserSessionModel userSession = session.sessions().getUserSession(realm, uSId);

                ClientModel testClient = session.clients().getClientByClientId(realm, "testClient");
                AuthenticatedClientSessionModel clientSession =
                        userSession.getAuthenticatedClientSessionByClient(testClient.getId());

                assertEquals(0, clientSession.getRefreshTokenUseCount("id2"));

                clientSession.setRefreshTokenUseCount("id2", 1);

                return null;
            });
        } finally {
            Time.setOffset(0);
        }
    }

    public static class RefreshTokenRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            realm.addUser("user1").email("user1@localhost");
            realm.addClient("testClient");
            return realm;
        }
    }
}
