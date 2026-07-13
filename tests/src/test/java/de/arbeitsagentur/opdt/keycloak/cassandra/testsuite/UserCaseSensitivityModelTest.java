package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserAdapter.REALM_ATTR_USERNAME_CASE_SENSITIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserCaseSensitivityModelTest extends CassandraModelTest {

    @InjectRealm(
            ref = CaseSensitiveRealmConfig.REALM_NAME,
            lifecycle = LifeCycle.METHOD,
            config = CaseSensitiveRealmConfig.class)
    ManagedRealm caseSensitiveRealm;

    @InjectRealm(
            ref = CaseInsensitiveRealmConfig.REALM_NAME,
            lifecycle = LifeCycle.METHOD,
            config = CaseInsensitiveRealmConfig.class)
    ManagedRealm caseInsensitiveRealm;

    @TestOnServer
    public void testCaseSensitiveRealm(KeycloakSession testSession) {
        withRealm(testSession, CaseSensitiveRealmConfig.REALM_NAME, (session, realm) -> {
            session.users().addUser(realm, "user");
            session.users().addUser(realm, "USER");

            return null;
        });

        withRealm(testSession, CaseSensitiveRealmConfig.REALM_NAME, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "user");
            UserModel user2 = session.users().getUserByUsername(realm, "USER");

            assertThat(user1, not(nullValue()));
            assertThat(user2, not(nullValue()));

            assertThat(user1.getUsername(), equalTo("user"));
            assertThat(user2.getUsername(), equalTo("USER"));

            return null;
        });
    }

    @TestOnServer
    public void testCaseInsensitiveRealm(KeycloakSession testSession) {
        withRealm(testSession, CaseInsensitiveRealmConfig.REALM_NAME, (session, realm) -> {
            session.users().addUser(realm, "user");

            return null;
        });

        withRealm(testSession, CaseInsensitiveRealmConfig.REALM_NAME, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "user");
            assertThat(user1, not(nullValue()));

            UserModel user2 = session.users().getUserByUsername(realm, "UsER");
            assertThat(user2, not(nullValue()));

            UserProvider userProvider = session.users();
            assertThrows(ModelDuplicateException.class, () -> userProvider.addUser(realm, "USER"));

            return null;
        });
    }

    public static class CaseSensitiveRealmConfig implements RealmConfig {
        private static final String REALM_NAME = "case-sensitive-users";

        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.attribute(REALM_ATTR_USERNAME_CASE_SENSITIVE, "true");

            return realm.name(REALM_NAME);
        }
    }

    public static class CaseInsensitiveRealmConfig implements RealmConfig {
        private static final String REALM_NAME = "case-insensitive-users";

        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.attribute(REALM_ATTR_USERNAME_CASE_SENSITIVE, "false");

            return realm.name(REALM_NAME);
        }
    }
}
