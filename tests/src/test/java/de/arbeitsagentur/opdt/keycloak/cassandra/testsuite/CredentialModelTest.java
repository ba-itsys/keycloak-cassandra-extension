package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.junit.Assert.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class CredentialModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "credential-model";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = CredentialRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testCredentialCRUD(KeycloakSession testSession) {

        AtomicReference<String> passwordId = new AtomicReference<>();
        AtomicReference<String> otp1Id = new AtomicReference<>();
        AtomicReference<String> otp2Id = new AtomicReference<>();

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertEquals(1, list.size());
            passwordId.set(list.get(0).getId());

            // Create 2 OTP credentials (password was already created)
            CredentialModel otp1 = OTPCredentialModel.createFromPolicy(realm, "secret1");
            CredentialModel otp2 = OTPCredentialModel.createFromPolicy(realm, "secret2");
            otp1 = user.credentialManager().createStoredCredential(otp1);
            otp2 = user.credentialManager().createStoredCredential(otp2);
            otp1Id.set(otp1.getId());
            otp2Id.set(otp2.getId());

            return null;
        });

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: password, otp1, otp2
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertOrder(list, passwordId.get(), otp1Id.get(), otp2Id.get());

            // Assert can't move password when newPreviousCredential not found
            assertFalse(user.credentialManager().moveStoredCredentialTo(passwordId.get(), "not-known"));

            // Assert can't move credential when not found
            assertFalse(user.credentialManager().moveStoredCredentialTo("not-known", otp2Id.get()));

            // Move otp2 up 1 position
            assertTrue(user.credentialManager().moveStoredCredentialTo(otp2Id.get(), passwordId.get()));

            return null;
        });

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: password, otp2, otp1
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertOrder(list, passwordId.get(), otp2Id.get(), otp1Id.get());

            // Move otp2 to the top
            assertTrue(user.credentialManager().moveStoredCredentialTo(otp2Id.get(), null));

            return null;
        });

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, password, otp1
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertOrder(list, otp2Id.get(), passwordId.get(), otp1Id.get());

            // Move password down
            assertTrue(user.credentialManager().moveStoredCredentialTo(passwordId.get(), otp1Id.get()));

            return null;
        });

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, otp1, password
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertOrder(list, otp2Id.get(), otp1Id.get(), passwordId.get());

            // Remove otp2 down two positions
            assertTrue(user.credentialManager().moveStoredCredentialTo(otp2Id.get(), passwordId.get()));

            return null;
        });

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, otp1, password
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertOrder(list, otp1Id.get(), passwordId.get(), otp2Id.get());

            // Remove password
            assertTrue(user.credentialManager().removeStoredCredentialById(passwordId.get()));

            return null;
        });

        withRealm(testSession, REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "test-user@localhost");

            // Assert priorities: otp2, password
            List<CredentialModel> list =
                    user.credentialManager().getStoredCredentialsStream().collect(Collectors.toList());
            assertOrder(list, otp1Id.get(), otp2Id.get());

            return null;
        });
    }

    private void assertOrder(List<CredentialModel> creds, String... expectedIds) {
        assertEquals(expectedIds.length, creds.size());

        if (creds.size() == 0) return;

        for (int i = 0; i < expectedIds.length; i++) {
            assertEquals(creds.get(i).getId(), expectedIds[i]);
        }
    }

    public static class CredentialRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.users(UserBuilder.create().username("test-user@localhost").password("password"));
            return realm;
        }
    }
}
