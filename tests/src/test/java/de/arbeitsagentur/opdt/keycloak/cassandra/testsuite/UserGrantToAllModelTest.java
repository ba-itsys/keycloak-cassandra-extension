package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserGrantToAllModelTest extends CassandraModelTest {

    @InjectRealm(
            ref = GrantToAllRealmConfig.REALM_NAME,
            lifecycle = LifeCycle.METHOD,
            config = GrantToAllRealmConfig.class)
    ManagedRealm realm1;

    @InjectRealm(ref = OtherRealmConfig.REALM_NAME, lifecycle = LifeCycle.METHOD, config = OtherRealmConfig.class)
    ManagedRealm realm2;

    @TestOnServer
    public void testGrantToAll(KeycloakSession testSession) {
        withRealm(testSession, GrantToAllRealmConfig.REALM_NAME, (currentSession, realm) -> {
            realm.addRole("role1");
            realm.addRole("defaultRole");
            RoleModel defaultRole = realm.getRole("defaultRole");
            realm.setDefaultRole(defaultRole);
            currentSession.users().addUser(realm, "user1");
            currentSession.users().addUser(realm, "user2");

            RealmModel otherRealm = currentSession.realms().getRealmByName(OtherRealmConfig.REALM_NAME);
            currentSession.users().addUser(otherRealm, "user1");
            return null;
        });

        withRealm(testSession, GrantToAllRealmConfig.REALM_NAME, (currentSession, realm) -> {
            RoleModel role1 = realm.getRole("role1");
            currentSession.users().grantToAllUsers(realm, role1);
            return null;
        });

        withRealm(testSession, GrantToAllRealmConfig.REALM_NAME, (currentSession, realm) -> {
            RoleModel role1 = realm.getRole("role1");
            RoleModel defaultRole = realm.getRole("defaultRole");
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");
            assertTrue(user1.hasRole(role1));
            assertTrue(user1.hasRole(defaultRole));
            assertTrue(user2.hasRole(role1));
            assertTrue(user2.hasRole(defaultRole));

            RealmModel otherRealm = currentSession.realms().getRealmByName(OtherRealmConfig.REALM_NAME);
            UserModel realm2User1 = currentSession.users().getUserByUsername(otherRealm, "user1");
            assertFalse(realm2User1.hasRole(role1));
            assertFalse(realm2User1.hasRole(defaultRole));

            user1.deleteRoleMapping(role1);
            user1.deleteRoleMapping(defaultRole);
            return null;
        });

        withRealm(testSession, GrantToAllRealmConfig.REALM_NAME, (currentSession, realm) -> {
            RoleModel role1 = realm.getRole("role1");
            RoleModel defaultRole = realm.getRole("defaultRole");
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            assertFalse(user1.hasRole(role1));
            assertTrue(user1.hasRole(defaultRole));

            return null;
        });
    }

    public static class GrantToAllRealmConfig implements RealmConfig {
        private static final String REALM_NAME = "grant-to-all-realm-1";

        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.name(REALM_NAME);
        }
    }

    public static class OtherRealmConfig implements RealmConfig {
        private static final String REALM_NAME = "grant-to-all-realm-2";

        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            return realm.name(REALM_NAME);
        }
    }
}
