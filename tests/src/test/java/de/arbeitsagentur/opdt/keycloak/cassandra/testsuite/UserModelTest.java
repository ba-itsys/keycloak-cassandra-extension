/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserAdapter.REALM_ATTR_USERNAME_CASE_SENSITIVE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserAdapter;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hamcrest.Matchers;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.keycloak.common.util.Time;
import org.keycloak.models.*;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.services.managers.ClientManager;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

/**
 * Ported from:
 *
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class UserModelTest extends CassandraModelTest {

    private static final Logger log = Logger.getLogger(UserModelTest.class);

    protected static final int NUM_GROUPS = 100;
    private static final String ORIGINAL_REALM_NAME = "original";
    private static final String OTHER_REALM_NAME = "other";

    @InjectRealm(ref = ORIGINAL_REALM_NAME, lifecycle = LifeCycle.METHOD, config = OriginalRealmConfig.class)
    ManagedRealm originalRealm;

    @InjectRealm(ref = OTHER_REALM_NAME, lifecycle = LifeCycle.METHOD)
    ManagedRealm otherRealm;

    private static List<String> groupIds() {
        return IntStream.range(0, NUM_GROUPS)
                .mapToObj(OriginalRealmConfig::groupId)
                .collect(Collectors.toList());
    }

    @TestOnServer
    public void staleUserUpdate(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");

            return null;
        });

        boolean staleExceptionOccured = false;
        try {
            withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
                UserModel user = session.users().getUserByUsername(realm, "user");
                assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));

                user.setAttribute(CassandraUserAdapter.ENTITY_VERSION, Arrays.asList("1"));

                return null;
            });
        } catch (Exception e) {
            staleExceptionOccured = true;
        }

        assertTrue(staleExceptionOccured);

        staleExceptionOccured = false;
        try {
            withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
                UserModel user = session.users().getUserByUsername(realm, "user");
                assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));

                user.setAttribute(CassandraUserAdapter.ENTITY_VERSION, Arrays.asList("3"));

                return null;
            });
        } catch (Exception e) {
            staleExceptionOccured = true;
        }

        assertTrue(staleExceptionOccured);
    }

    @TestOnServer
    public void testWorkingUserUpdate(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));

            user.setUsername("updateduser");
            user.setSingleAttribute("test", "bla");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "updateduser");

            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("3"));
            assertThat(user.getFirstAttribute("test"), is("bla"));

            assertNull(session.users().getUserByUsername(realm, null));

            return null;
        });
    }

    @TestOnServer
    public void testOverrideCreationTimestamp(KeycloakSession testSession) {

        long newCreationTimestamp = Time.currentTimeMillis() - 42;
        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setCreatedTimestamp(newCreationTimestamp);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getCreatedTimestamp(), is(newCreationTimestamp));

            user.setCreatedTimestamp(Time.currentTimeMillis() + 42000);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(
                    user.getCreatedTimestamp(),
                    is(newCreationTimestamp)); // nothing changed because date in future cannot be set

            return null;
        });
    }

    @TestOnServer
    public void testUsernameEqualsMailNoConfilctNoThrow(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setUsername("newmail@bob");
            assertEquals("newmail@bob", bob.getUsername());

            return null;
        });
    }

    @TestOnServer
    public void testUsernameEqualsMailNoThrowIfUniquenessAcrossBothNotEnforced(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, false);
            realm.setDuplicateEmailsAllowed(false);
            bob.setUsername(alice.getEmail());
            assertEquals(bob.getUsername(), alice.getEmail());

            return null;
        });
    }

    @TestOnServer
    public void testUsernameEqualsMailNoThrowIfDuplicateMailsAllowed(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(true);
            bob.setUsername(alice.getEmail());
            assertEquals(bob.getUsername(), alice.getEmail());

            return null;
        });
    }

    @TestOnServer
    public void testUsernameEqualsMailNoThrowForOwnMail(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setUsername(bob.getEmail());
            assertEquals(bob.getUsername(), bob.getEmail());

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @TestOnServer
    public void testUsernameEqualsMailThrowIfChecksEnabledAndUsedMail(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            assertThrows(ModelDuplicateException.class, () -> bob.setUsername(alice.getEmail()));

            return null;
        });
    }

    @TestOnServer
    public void testMailEqualsUsernameNoConfilctNoThrow(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "info@bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setEmail("newmail@bob");
            assertEquals("newmail@bob", bob.getEmail());

            return null;
        });
    }

    @TestOnServer
    public void testMailEqualsUsernameNoThrowIfUniquenessAcrossBothNotEnforced(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("othermail@alice"); // else the duplicate-mail check would interfere
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, false);
            realm.setDuplicateEmailsAllowed(false);
            bob.setEmail(alice.getUsername());
            assertEquals(bob.getEmail(), alice.getUsername());

            return null;
        });
    }

    @TestOnServer
    public void testMailEqualsUsernameNoThrowIfDuplicateMailsAllowed(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("info@alice");
            UserModel bob = session.users().addUser(realm, "info@bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(true);
            bob.setEmail(alice.getUsername());
            assertEquals(bob.getEmail(), alice.getUsername());

            return null;
        });
    }

    @TestOnServer
    public void testMailEqualsUsernameNoThrowForOwnUsername(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel bob = session.users().addUser(realm, "info@bob");
            bob.setEmail("othermail@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            bob.setEmail(bob.getUsername());
            assertEquals(bob.getUsername(), bob.getEmail());

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @TestOnServer
    public void testMailEqualsUsernameThrowIfChecksEnabledAndExistingUsername(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "info@alice");
            alice.setEmail("othermail@alice"); // else the duplicate-mail check would interfere
            UserModel bob = session.users().addUser(realm, "bob");
            bob.setEmail("info@bob");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            assertThrows(ModelDuplicateException.class, () -> bob.setEmail(alice.getUsername()));

            return null;
        });
    }

    @TestOnServer
    public void testSetEntityVersion(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("1"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("1"));

            user.setSingleAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY, "42");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("1"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("1"));

            user.setSingleAttribute(CassandraUserAdapter.ENTITY_VERSION, "2");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("2"));

            user.setSingleAttribute(CassandraUserAdapter.ENTITY_VERSION, "1");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION), is("2"));
            assertThat(user.getFirstAttribute(CassandraUserAdapter.ENTITY_VERSION_READONLY), is("2"));

            return null;
        });
    }

    @TestOnServer
    public void testPersistUser(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFirstName("first-name");
            user.setLastName("last-name");
            user.setEmail("email");
            assertNotNull(user.getCreatedTimestamp());
            // test that timestamp is current with 10s tollerance
            assertTrue((System.currentTimeMillis() - user.getCreatedTimestamp()) < 10000);

            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
            user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);

            RealmModel searchRealm = session.realms().getRealm(realm.getId());
            UserModel persisted = session.users().getUserByUsername(searchRealm, "user");

            assertUserModel(user, persisted);

            searchRealm = session.realms().getRealm(realm.getId());
            UserModel persisted2 = session.users().getUserById(searchRealm, user.getId());
            assertUserModel(user, persisted2);

            Map<String, String> attributes = new HashMap<>();
            attributes.put(UserModel.EMAIL, "email");
            List<UserModel> search =
                    session.users().searchForUserStream(realm, attributes).collect(Collectors.toList());
            Assert.assertThat(search, hasSize(1));
            Assert.assertThat(search.get(0).getUsername(), equalTo("user"));

            return null;
        });
    }

    @TestOnServer
    public void testWebOriginSet(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            ClientModel client = realm.addClient("user");

            Assert.assertThat(client.getWebOrigins(), empty());

            client.addWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.addWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(2));

            client.removeWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.removeWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), empty());

            client = realm.addClient("oauthclient2");

            Assert.assertThat(client.getWebOrigins(), empty());

            client.addWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.addWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(2));

            client.removeWebOrigin("origin-2");
            Assert.assertThat(client.getWebOrigins(), hasSize(1));

            client.removeWebOrigin("origin-1");
            Assert.assertThat(client.getWebOrigins(), empty());

            return null;
        });
    }

    @TestOnServer
    public void testUserRequiredActions(KeycloakSession testSession) throws Exception {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            List<String> requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, empty());

            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
            String id = realm.getId();

            realm = session.realms().getRealm(id);
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(1));
            Assert.assertThat(requiredActions, contains(UserModel.RequiredAction.CONFIGURE_TOTP.name()));

            user.addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(1));
            Assert.assertThat(requiredActions, contains(UserModel.RequiredAction.CONFIGURE_TOTP.name()));

            user.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL.name());
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(2));
            Assert.assertThat(
                    requiredActions,
                    containsInAnyOrder(
                            UserModel.RequiredAction.CONFIGURE_TOTP.name(),
                            UserModel.RequiredAction.VERIFY_EMAIL.name()));

            user.removeRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP.name());
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, hasSize(1));
            Assert.assertThat(requiredActions, contains(UserModel.RequiredAction.VERIFY_EMAIL.name()));

            user.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL.name());
            user = session.users().getUserByUsername(realm, "user");

            requiredActions = user.getRequiredActionsStream().collect(Collectors.toList());
            Assert.assertThat(requiredActions, empty());

            return null;
        });
    }

    @TestOnServer
    public void testUserMultipleAttributes(KeycloakSession testSession) throws Exception {

        AtomicReference<List<String>> attrValsAtomic = new AtomicReference<>();

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            session.users().addUser(realm, "user-noattrs");

            user.setSingleAttribute("key1", "value1");

            List<String> attrVals = new ArrayList<>(Arrays.asList("val21", "val22"));
            attrValsAtomic.set(attrVals);

            user.setAttribute("key2", attrVals);

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            // Test read attributes
            UserModel user = session.users().getUserByUsername(realm, "user");

            List<String> attrVals = user.getAttributeStream("key1").collect(Collectors.toList());
            Assert.assertThat(attrVals, hasSize(1));
            Assert.assertThat(attrVals, contains("value1"));
            Assert.assertThat(user.getFirstAttribute("key1"), equalTo("value1"));

            attrVals = user.getAttributeStream("key2").collect(Collectors.toList());
            Assert.assertThat(attrVals, hasSize(2));
            Assert.assertThat(attrVals, containsInAnyOrder("val21", "val22"));

            attrVals = user.getAttributeStream("key3").collect(Collectors.toList());
            Assert.assertThat(attrVals, empty());
            Assert.assertThat(user.getFirstAttribute("key3"), nullValue());

            Map<String, List<String>> allAttrVals = user.getAttributes();
            Assert.assertThat(allAttrVals.keySet(), hasSize(7));
            Assert.assertThat(
                    allAttrVals.keySet(),
                    containsInAnyOrder(
                            CassandraUserAdapter.ENTITY_VERSION_READONLY,
                            UserModel.USERNAME,
                            UserModel.FIRST_NAME,
                            UserModel.LAST_NAME,
                            UserModel.EMAIL,
                            "key1",
                            "key2"));
            Assert.assertThat(
                    allAttrVals.get("key1"),
                    equalTo(user.getAttributeStream("key1").collect(Collectors.toList())));
            Assert.assertThat(
                    allAttrVals.get("key2"),
                    equalTo(user.getAttributeStream("key2").collect(Collectors.toList())));

            // Test remove and rewrite attribute
            user.removeAttribute("key1");
            user.setSingleAttribute("key2", "val23");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            Assert.assertThat(user.getFirstAttribute("key1"), nullValue());

            List<String> attrVals = user.getAttributeStream("key2").collect(Collectors.toList());

            Assert.assertThat(attrVals, hasSize(1));
            Assert.assertThat(attrVals.get(0), equalTo("val23"));

            return null;
        });
    }

    @TestOnServer
    public void testUpdateUserAttribute(KeycloakSession testSession) throws Exception {

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().addUser(realm, "user");

            user.setSingleAttribute("key1", "value1");
            user.setSingleAttribute(UserModel.USERNAME, "userUpdated");
            user.setSingleAttribute(UserModel.FIRST_NAME, "fn");
            user.setSingleAttribute(UserModel.LAST_NAME, "ln");
            user.setSingleAttribute(UserModel.EMAIL, "e@f.de");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user = currentSession.users().getUserByUsername(realm, "userUpdated");

            assertThat(user.getFirstName(), is("fn"));
            assertThat(user.getLastName(), is("ln"));
            assertThat(user.getEmail(), is("e@f.de"));

            // Update attribute
            List<String> attrVals = new ArrayList<>(Arrays.asList("val2"));
            user.setAttribute("key1", attrVals);
            Map<String, List<String>> allAttrVals = user.getAttributes();

            // Ensure same transaction is able to see updated value
            Assert.assertThat(allAttrVals.keySet(), hasSize(6));
            Assert.assertThat(
                    allAttrVals.keySet(),
                    containsInAnyOrder(
                            "key1",
                            CassandraUserAdapter.ENTITY_VERSION_READONLY,
                            UserModel.FIRST_NAME,
                            UserModel.LAST_NAME,
                            UserModel.EMAIL,
                            UserModel.USERNAME));
            Assert.assertThat(allAttrVals.get("key1"), contains("val2"));
            return null;
        });
    }

    // KEYCLOAK-3608
    @TestOnServer
    public void testUpdateUserSingleAttribute(KeycloakSession testSession) {

        AtomicReference<Map<String, List<String>>> expectedAtomic = new AtomicReference<>();

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            Map<String, List<String>> expected = new HashMap<>();
            expected.put("key1", Collections.singletonList("value3"));
            expected.put("key2", Collections.singletonList("value2"));
            expected.put(UserModel.FIRST_NAME, Collections.singletonList(null));
            expected.put(UserModel.LAST_NAME, Collections.singletonList(null));
            expected.put(UserModel.EMAIL, Collections.singletonList(null));
            expected.put(UserModel.USERNAME, Collections.singletonList("user"));
            expected.put(CassandraUserAdapter.ENTITY_VERSION_READONLY, Collections.singletonList("1"));

            UserModel user = currentSession.users().addUser(realm, "user");

            user.setSingleAttribute("key1", "value1");
            user.setSingleAttribute("key2", "value2");
            user.setSingleAttribute("key3", null); // KEYCLOAK-7014

            // Overwrite the first attribute
            user.setSingleAttribute("key1", "value3");

            Assert.assertThat(user.getAttributes(), equalTo(expected));

            expectedAtomic.set(expected);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            Map<String, List<String>> expected = expectedAtomic.get();
            expected.put(CassandraUserAdapter.ENTITY_VERSION_READONLY, Collections.singletonList("2"));

            Assert.assertThat(
                    currentSession.users().getUserByUsername(realm, "user").getAttributes(), equalTo(expected));
            return null;
        });
    }

    @TestOnServer
    public void testSearchByString(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            currentSession.users().addUser(realm, "user1");
            currentSession.users().addUser(realm, "user2");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");

            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, "user1", 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user1));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, (String) null, null, null)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            List<UserModel> users =
                    currentSession.users().searchForUserStream(realm, "", 0, 7).collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));
            return null;
        });
    }

    @TestOnServer
    public void testSearchByParams(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().addUser(realm, "user1");
            user1.setSingleAttribute(UserModel.IDP_USER_ID, null);

            UserModel user2 = currentSession.users().addUser(realm, "user2");
            user2.setEmail("user2@example.com");
            user2.setSingleAttribute(UserModel.IDP_USER_ID, "fakeIDPValue");
            user2.setSingleAttribute(UserModel.IDP_ALIAS, "fakeIDPAlias");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            realm.setAttribute("keycloak.username-search.case-sensitive", "false");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "true");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            realm.setAttribute("keycloak.username-search.case-sensitive", "false");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "false");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(0));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            realm.setAttribute("keycloak.username-search.case-sensitive", "true");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "false");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            realm.setAttribute("keycloak.username-search.case-sensitive", "true");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "UsEr2");
            params.put(UserModel.EXACT, "true");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(0));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.EMAIL, "user2@example.com");
            params.put(UserModel.EXACT, "true");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.IDP_USER_ID, "fakeIDPValue");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            Map<String, String> params = new HashMap<>();
            params.put(UserModel.IDP_ALIAS, "fakeIDPAlias");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            Map<String, String> params = new HashMap<>();
            params.put(UserModel.USERNAME, "user1");
            params.put(UserModel.INCLUDE_SERVICE_ACCOUNT, "true");
            params.put(UserModel.IDP_ALIAS, "fakeIDPAlias");
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserStream(realm, params, 0, 7)
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(0));
            return null;
        });
    }

    @TestOnServer
    public void testSearchByUserAttribute(KeycloakSession testSession) throws Exception {

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().addUser(realm, "user1");
            UserModel user2 = currentSession.users().addUser(realm, "user2");
            UserModel user3 = currentSession.users().addUser(realm, "user3");
            RealmModel otherRealm = currentSession.realms().getRealmByName("other");
            UserModel otherRealmUser = currentSession.users().addUser(otherRealm, "user1");

            user1.setSingleAttribute("key1", "value1");
            user1.setSingleAttribute("key2", "value21");

            user2.setSingleAttribute("key1", "value1");
            user2.setSingleAttribute("key2", "value22");

            user3.setSingleAttribute("key2", "value21");

            otherRealmUser.setSingleAttribute("key2", "value21");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");
            UserModel user3 = currentSession.users().getUserByUsername(realm, "user3");

            List<UserModel> users = currentSession
                    .users()
                    .searchForUserByUserAttributeStream(realm, "key1", "value1")
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));

            users = currentSession
                    .users()
                    .searchForUserByUserAttributeStream(realm, "key2", "value21")
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user3));

            users = currentSession
                    .users()
                    .searchForUserByUserAttributeStream(realm, "key2", "value22")
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));

            users = currentSession
                    .users()
                    .searchForUserByUserAttributeStream(realm, "key3", "value3")
                    .collect(Collectors.toList());
            Assert.assertThat(users, empty());
            return null;
        });
    }

    @TestOnServer
    public void testServiceAccountLinkRollback(KeycloakSession testSession) {

        try {
            withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
                UserModel user1 = currentSession.users().addUser(realm, "user1");
                ClientModel client = realm.addClient("foo");
                user1.setServiceAccountClientLink(client.getId());

                throw new RuntimeException("Rollback");
            });
        } catch (Exception e) {
            withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
                UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
                assertNull(user1);
                ClientModel client = realm.getClientByClientId("foo");
                assertNull(client);
                return null;
            });
        }
    }

    @TestOnServer
    public void testServiceAccountLink(KeycloakSession testSession) throws Exception {

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            ClientModel client = realm.addClient("foo");

            UserModel user1 = currentSession.users().addUser(realm, "user1");
            user1.setFirstName("John");
            user1.setLastName("Doe");
            user1.setSingleAttribute("indexed.fullName", "John Doe");

            UserModel user2 = currentSession.users().addUser(realm, "user2");
            user2.setFirstName("John");
            user2.setLastName("Doe");
            user2.setSingleAttribute("indexed.fullName", "John Doe");

            // Search
            Assert.assertThat(currentSession.users().getServiceAccount(client), nullValue());
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserByUserAttributeStream(realm, "indexed.fullName", "John Doe")
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));

            // Link service account
            user1.setServiceAccountClientLink(client.getId());

            UserModel searched = currentSession.users().getServiceAccount(client);
            Assert.assertThat(searched, equalTo(user1));
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            UserModel user2 = currentSession.users().getUserByUsername(realm, "user2");

            // Search and assert service account user not found
            ClientModel client = realm.getClientByClientId("foo");
            UserModel searched = currentSession.users().getServiceAccount(client);
            Assert.assertThat(searched, equalTo(user1));
            List<UserModel> users = currentSession
                    .users()
                    .searchForUserByUserAttributeStream(realm, "indexed.fullName", "John Doe")
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));

            users = currentSession
                    .users()
                    .searchForUserStream(
                            realm,
                            Collections.singletonMap(UserModel.INCLUDE_SERVICE_ACCOUNT, Boolean.FALSE.toString()))
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(1));
            Assert.assertThat(users, contains(user2));

            users = currentSession
                    .users()
                    .searchForUserStream(realm, Collections.emptyMap())
                    .collect(Collectors.toList());
            Assert.assertThat(users, hasSize(2));
            Assert.assertThat(users, containsInAnyOrder(user1, user2));

            Assert.assertThat(currentSession.users().getUsersCount(realm, true), equalTo(2));
            Assert.assertThat(currentSession.users().getUsersCount(realm, false), equalTo(1));

            // Remove client
            RealmManager realmMgr = new RealmManager(currentSession);
            ClientManager clientMgr = new ClientManager(realmMgr);

            clientMgr.removeClient(realm, client);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            // Assert service account removed as well
            Assert.assertThat(currentSession.users().getUserByUsername(realm, "user1"), nullValue());
            return null;
        });
    }

    @TestOnServer
    public void testUserNotBefore(KeycloakSession testSession) throws Exception {

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().addUser(realm, "user1");
            currentSession.users().setNotBeforeForUser(realm, user1, 10);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            int notBefore = currentSession.users().getNotBeforeOfUser(realm, user1);
            Assert.assertThat(notBefore, equalTo(10));

            // Try to update
            currentSession.users().setNotBeforeForUser(realm, user1, 20);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (currentSession, realm) -> {
            UserModel user1 = currentSession.users().getUserByUsername(realm, "user1");
            int notBefore = currentSession.users().getNotBeforeOfUser(realm, user1);
            Assert.assertThat(notBefore, equalTo(20));
            return null;
        });
    }

    private static void assertUserModel(UserModel expected, UserModel actual) {
        Assert.assertThat(actual.getUsername(), equalTo(expected.getUsername()));
        Assert.assertThat(actual.getCreatedTimestamp(), equalTo(expected.getCreatedTimestamp()));
        Assert.assertThat(actual.getFirstName(), equalTo(expected.getFirstName()));
        Assert.assertThat(actual.getLastName(), equalTo(expected.getLastName()));
        Assert.assertThat(
                actual.getRequiredActionsStream().collect(Collectors.toSet()),
                containsInAnyOrder(expected.getRequiredActionsStream().toArray()));
    }

    private static Void addRemoveUser(KeycloakSessionFactory factory, String realmName, int i) {
        runJobInTransaction(factory, session -> {
            RealmModel realm = session.realms().getRealmByName(realmName);
            session.getContext().setRealm(realm);
            UserModel user = session.users().addUser(realm, "user-" + i);

            IntStream.range(0, NUM_GROUPS / 20).forEach(gIndex -> {
                user.joinGroup(session.groups().getGroupById(realm, groupIds().get((i + gIndex) % NUM_GROUPS)));
            });

            UserModel obtainedUser = session.users().getUserById(realm, user.getId());

            assertThat(obtainedUser, Matchers.notNullValue());
            assertThat(obtainedUser.getUsername(), is("user-" + i));

            Set<String> userGroupIds =
                    obtainedUser.getGroupsStream().map(GroupModel::getName).collect(Collectors.toSet());
            assertThat(userGroupIds, hasSize(NUM_GROUPS / 20));
            assertThat(userGroupIds, hasItem("group-" + i));
            assertThat(userGroupIds, hasItem("group-" + (i - 1 + (NUM_GROUPS / 20)) % NUM_GROUPS));

            obtainedUser.leaveGroup(
                    session.groups().getGroupById(realm, groupIds().get(i)));
            userGroupIds =
                    obtainedUser.getGroupsStream().map(GroupModel::getName).collect(Collectors.toSet());
            assertThat(userGroupIds, hasSize((NUM_GROUPS / 20) - 1));

            assertTrue(session.users().removeUser(realm, user));
            assertFalse(session.users().removeUser(realm, user));
            assertNull(session.users().getUserByUsername(realm, user.getUsername()));
        });
        return null;
    }

    @TestOnServer
    public void testAddRemoveUser(KeycloakSession testSession) {

        addRemoveUser(testSession.getKeycloakSessionFactory(), ORIGINAL_REALM_NAME, 1);
    }

    @TestOnServer
    public void testAddRemoveUserConcurrent(KeycloakSession testSession) {

        KeycloakSessionFactory factory = testSession.getKeycloakSessionFactory();
        IntStream.range(0, 100).parallel().forEach(i -> addRemoveUser(factory, ORIGINAL_REALM_NAME, i));
    }

    @TestOnServer
    public void testAddRemoveUserCaseInsensitive(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            realm.setAttribute(REALM_ATTR_USERNAME_CASE_SENSITIVE, true);

            UserModel user1 = session.users().addUser(realm, "user-1");
            UserModel user2 = session.users().addUser(realm, "uSeR-1");
            UserModel obtainedUser1 = session.users().getUserById(realm, user1.getId());
            UserModel obtainedUser2 = session.users().getUserById(realm, user2.getId());

            assertThat(obtainedUser1, Matchers.notNullValue());
            assertThat(obtainedUser1.getUsername(), is("user-1"));

            assertThat(obtainedUser2, Matchers.notNullValue());
            assertThat(obtainedUser2.getUsername(), is("uSeR-1"));

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "user-1");
            session.users().removeUser(realm, user1);
            List<UserModel> foundUsers = session.users()
                    .searchForUserStream(realm, Map.of(UserModel.USERNAME, "user-1"))
                    .collect(Collectors.toList());

            assertThat(foundUsers, hasSize(1));
            assertThat(foundUsers.get(0).getUsername(), is("uSeR-1"));

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @TestOnServer
    public void testAddUserExistingMailAsUsernameWithoutConflict(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            session.users().addUser(realm, "info@bob");
            assertNotNull(session.users().getUserByUsername(realm, "info@bob"));

            return null;
        });
    }

    @SuppressWarnings("java:S5778")
    @TestOnServer
    public void testAddUserExistingMailAsUsernameThrows(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(false);
            assertThrows(ModelDuplicateException.class, () -> session.users().addUser(realm, "info@alice"));

            return null;
        });
    }

    @TestOnServer
    public void testAddUserExistingMailAsUsernameNoThrowIfCheckDisabled(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, false);
            realm.setDuplicateEmailsAllowed(false);
            session.users().addUser(realm, "info@alice");
            assertNotNull(session.users().getUserByUsername(realm, "info@alice"));

            return null;
        });
    }

    @TestOnServer
    public void testAddUserExistingMailAsUsernameNoThrowIfDuplicatesAllowed(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel alice = session.users().addUser(realm, "alice");
            alice.setEmail("info@alice");

            realm.setAttribute(
                    CassandraUserAdapter.REALM_ATTRIBUTE_ENABLE_CHECK_FOR_DUPLICATES_ACROSS_USERNAME_AND_EMAIL, true);
            realm.setDuplicateEmailsAllowed(true);
            session.users().addUser(realm, "info@alice");
            assertNotNull(session.users().getUserByUsername(realm, "info@alice"));

            return null;
        });
    }

    @TestOnServer
    public void testAddRemoveUsersInTheSameGroupConcurrent(KeycloakSession testSession) {

        final ConcurrentSkipListSet<String> userIds = new ConcurrentSkipListSet<>();
        String groupId = groupIds().get(0);
        KeycloakSessionFactory factory = testSession.getKeycloakSessionFactory();

        // Create users and let them join first group
        IntStream.range(0, 100)
                .parallel()
                .forEach(index -> runJobInTransaction(factory, session -> {
                    final RealmModel realm = session.realms().getRealmByName(ORIGINAL_REALM_NAME);
                    final UserModel user = session.users().addUser(realm, "user-" + index);
                    user.joinGroup(session.groups().getGroupById(realm, groupId));
                    userIds.add(user.getId());
                }));

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(session.users().getGroupMembersStream(realm, group).count(), is(100L));
            assertThat(
                    session.users()
                            .getGroupMembersStream(realm, group, null, null)
                            .count(),
                    is(100L));
            assertThat(
                    session.users().getGroupMembersStream(realm, group, 10, -1).count(), is(90L));
            assertThat(
                    session.users().getGroupMembersStream(realm, group, -1, 90).count(), is(90L));
            assertThat(
                    session.users().getGroupMembersStream(realm, group, 10, 150).count(), is(90L));
            return null;
        });

        userIds.stream()
                .parallel()
                .forEach(userId -> runJobInTransaction(factory, session -> {
                    final RealmModel realm = session.realms().getRealmByName(ORIGINAL_REALM_NAME);
                    final UserModel user = session.users().getUserById(realm, userId);
                    log.debugf("Remove user %s: %s", userId, session.users().removeUser(realm, user));
                }));

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            final GroupModel group = session.groups().getGroupById(realm, groupId);
            assertThat(
                    session.users().getGroupMembersStream(realm, group).collect(Collectors.toList()), Matchers.empty());
            return null;
        });
    }

    @TestOnServer
    public void testResolveNameConflict(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            session.users().addUser(realm, "test1@example.com");
            session.users().addUser(realm, "test2@example.com");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "test1@example.com");
            UserModel user2 = session.users().getUserByUsername(realm, "test2@example.com");

            user2.setUsername("test2_migrated@example.com");
            user1.setUsername("test2@example.com");
            user1.setEmail("test2@example.com");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user1 = session.users().getUserByUsername(realm, "test2@example.com");
            UserModel user2 = session.users().getUserByUsername(realm, "test2_migrated@example.com");

            assertNotNull(user1);
            assertNotNull(user2);

            session.users().removeUser(realm, user1);
            session.users().removeUser(realm, user2);

            return null;
        });
    }

    @TestOnServer
    public void testGetUserByUsernameIgnoresAndCleansStaleSearchIndexEntries(KeycloakSession testSession) {

        final String oldUsername = "a@example.com";
        final String newUsername = "a@example.com#archived";

        String userId = withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, oldUsername);
            user.setUsername(newUsername);
            return user.getId();
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            CassandraConnectionProvider connectionProvider = session.getProvider(CassandraConnectionProvider.class);
            CqlSession cqlSession = connectionProvider.getCqlSession();

            cqlSession.execute(SimpleStatement.newInstance(
                    "INSERT INTO user_search_index (realm_id, name, value, user_id) VALUES (?, ?, ?, ?)",
                    realm.getId(),
                    UserModel.USERNAME,
                    oldUsername,
                    userId));
            cqlSession.execute(SimpleStatement.newInstance(
                    "INSERT INTO user_search_index (realm_id, name, value, user_id) VALUES (?, ?, ?, ?)",
                    realm.getId(),
                    "usernameCaseInsensitive",
                    oldUsername.toLowerCase(Locale.ROOT),
                    userId));

            return null;
        });

        assertEquals(
                2L,
                countUserSearchIndexEntries(
                        testSession, ORIGINAL_REALM_NAME, UserModel.USERNAME, oldUsername, newUsername));
        assertEquals(
                2L,
                countUserSearchIndexEntries(
                        testSession,
                        ORIGINAL_REALM_NAME,
                        "usernameCaseInsensitive",
                        oldUsername.toLowerCase(Locale.ROOT),
                        newUsername.toLowerCase(Locale.ROOT)));

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, oldUsername);
            assertNull(user);

            UserModel renamedUser = session.users().getUserByUsername(realm, newUsername);
            assertNotNull(renamedUser);
            assertEquals(newUsername, renamedUser.getUsername());
            return null;
        });

        assertEquals(
                1L,
                countUserSearchIndexEntries(
                        testSession,
                        ORIGINAL_REALM_NAME,
                        "usernameCaseInsensitive",
                        oldUsername.toLowerCase(Locale.ROOT),
                        newUsername.toLowerCase(Locale.ROOT)));

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            realm.setAttribute(REALM_ATTR_USERNAME_CASE_SENSITIVE, "true");
            UserModel user = session.users().getUserByUsername(realm, oldUsername);
            assertNull(user);
            return null;
        });

        assertEquals(
                1L,
                countUserSearchIndexEntries(
                        testSession, ORIGINAL_REALM_NAME, UserModel.USERNAME, oldUsername, newUsername));
    }

    @TestOnServer
    public void thatEmailChangeWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setEmail("email");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setEmail("anotheremail");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getEmail(), is("anotheremail"));
            return null;
        });
    }

    @TestOnServer
    public void thatUpdatingNullEmailWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setEmail(null);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setEmail("email");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getEmail(), is("email"));
            return null;
        });
    }

    @TestOnServer
    public void thatUnchangedEmailUpdateWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setEmail("email");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setEmail("email");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getEmail(), is("email"));
            return null;
        });
    }

    @TestOnServer
    public void thatServiceAccountClientLinkChangeWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setServiceAccountClientLink("link");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setServiceAccountClientLink("anotherlink");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getServiceAccountClientLink(), is("anotherlink"));
            return null;
        });
    }

    @TestOnServer
    public void thatUpdatingNullServiceAccountClientLinkWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setServiceAccountClientLink(null);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setServiceAccountClientLink("link");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getServiceAccountClientLink(), is("link"));
            return null;
        });
    }

    @TestOnServer
    public void thatUnchangedServiceAccountClientLinkUpdateWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setServiceAccountClientLink("link");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setServiceAccountClientLink("link");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getServiceAccountClientLink(), is("link"));
            return null;
        });
    }

    @TestOnServer
    public void thatFederationLinkChangeWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFederationLink("federationLink");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setFederationLink("anotherlink");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFederationLink(), is("anotherlink"));
            return null;
        });
    }

    @TestOnServer
    public void thatUpdatingFederationLinkWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFederationLink(null);
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setFederationLink("link");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFederationLink(), is("link"));
            return null;
        });
    }

    @TestOnServer
    public void thatUnchangedFederationLinkUpdateWorksAsExpected(KeycloakSession testSession) {

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "user");
            user.setFederationLink("federationLink");

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            user.setFederationLink("federationLink");
            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserByUsername(realm, "user");
            assertThat(user.getFederationLink(), is("federationLink"));
            return null;
        });
    }

    @TestOnServer
    public void testDeleteUserWithMultipleFederatedIdentities(KeycloakSession testSession) {

        String userId = withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().addUser(realm, "testUser");

            // Add multiple federated identities
            FederatedIdentityModel identity1 =
                    new FederatedIdentityModel("provider1", "brokerUserId1", "brokerUsername1");
            FederatedIdentityModel identity2 =
                    new FederatedIdentityModel("provider2", "brokerUserId2", "brokerUsername2");
            FederatedIdentityModel identity3 =
                    new FederatedIdentityModel("provider3", "brokerUserId3", "brokerUsername3");

            session.users().addFederatedIdentity(realm, user, identity1);
            session.users().addFederatedIdentity(realm, user, identity2);
            session.users().addFederatedIdentity(realm, user, identity3);

            return user.getId();
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            UserModel user = session.users().getUserById(realm, userId);
            assertThat(user, notNullValue());

            // Verify federated identities are present
            List<FederatedIdentityModel> federatedIdentities =
                    session.users().getFederatedIdentitiesStream(realm, user).collect(Collectors.toList());
            assertThat(federatedIdentities, hasSize(3));

            // Delete the user
            assertTrue(session.users().removeUser(realm, user));

            return null;
        });

        withRealm(testSession, ORIGINAL_REALM_NAME, (session, realm) -> {
            // Verify user is deleted
            UserModel deletedUser = session.users().getUserById(realm, userId);
            assertThat(deletedUser, nullValue());

            // Verify federated identities are deleted
            assertThat(session.users().getUserById(realm, userId), nullValue());

            // Verify federated identity to user mappings are deleted
            assertThat(
                    session.users()
                            .getUserByFederatedIdentity(
                                    realm, new FederatedIdentityModel("provider1", "brokerUserId1", null)),
                    nullValue());
            assertThat(
                    session.users()
                            .getUserByFederatedIdentity(
                                    realm, new FederatedIdentityModel("provider2", "brokerUserId2", null)),
                    nullValue());
            assertThat(
                    session.users()
                            .getUserByFederatedIdentity(
                                    realm, new FederatedIdentityModel("provider3", "brokerUserId3", null)),
                    nullValue());

            return null;
        });
    }

    private static long countUserSearchIndexEntries(
            KeycloakSession testSession, String realmName, String name, String... values) {
        return inCommittedTransaction(testSession, session -> {
            CassandraConnectionProvider connectionProvider = session.getProvider(CassandraConnectionProvider.class);
            CqlSession cqlSession = connectionProvider.getCqlSession();
            String realmId = session.realms().getRealmByName(realmName).getId();
            return Arrays.stream(values)
                    .mapToLong(value -> cqlSession
                            .execute(SimpleStatement.newInstance(
                                    "SELECT user_id FROM user_search_index WHERE realm_id = ? AND name = ? AND value = ?",
                                    realmId,
                                    name,
                                    value))
                            .all()
                            .size())
                    .sum();
        });
    }

    public static class OriginalRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            GroupRepresentation[] groups = IntStream.range(0, NUM_GROUPS)
                    .mapToObj(i -> group(groupId(i), "group-" + i))
                    .toArray(GroupRepresentation[]::new);
            realm.update(rep -> rep.setGroups(Arrays.asList(groups)));
            return realm;
        }

        private static GroupRepresentation group(String id, String name) {
            GroupRepresentation group = new GroupRepresentation();
            group.setId(id);
            group.setName(name);
            return group;
        }

        private static String groupId(int index) {
            return "group-" + index + "-id";
        }
    }
}
