/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.hamcrest.MatcherAssert.assertThat;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.common.util.Time;
import org.keycloak.models.DefaultActionTokenKey;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RealmConfigBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class SingleUseObjectModelTest extends CassandraModelTest {

    private static final String USER_ID = "single-use-user";
    private static final String REALM_NAME = "single-use";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = SingleUseRealmConfig.class)
    ManagedRealm managedRealm;

    @TestOnServer
    public void testActionTokens(KeycloakSession testSession) {

        DefaultActionTokenKey key = withRealm(testSession, REALM_NAME, (session, realm) -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            int time = Time.currentTime();
            DefaultActionTokenKey actionTokenKey =
                    new DefaultActionTokenKey(USER_ID, UUID.randomUUID().toString(), time + 3, null);
            Map<String, String> notes = new HashMap<>();
            notes.put("foo", "bar");
            singleUseObjectProvider.put(actionTokenKey.serializeKey(), actionTokenKey.getExp() - time, notes);
            return actionTokenKey;
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            Map<String, String> notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNotNull(notes);
            Assert.assertEquals("bar", notes.get("foo"));

            notes = singleUseObjectProvider.remove(key.serializeKey());
            Assert.assertNotNull(notes);
            Assert.assertEquals("bar", notes.get("foo"));
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            Map<String, String> notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNull(notes);

            notes = new HashMap<>();
            notes.put("foo", "bar");
            singleUseObjectProvider.put(key.serializeKey(), key.getExp() - Time.currentTime(), notes);
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseObjectProvider = session.singleUseObjects();
            Map<String, String> notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNotNull(notes);
            Assert.assertEquals("bar", notes.get("foo"));

            sleep(5000);

            notes = singleUseObjectProvider.get(key.serializeKey());
            Assert.assertNull(notes);
        });
    }

    @TestOnServer
    public void testSingleUseStore(KeycloakSession testSession) {

        String key = UUID.randomUUID().toString();
        Map<String, String> notes = new HashMap<>();
        notes.put("foo", "bar");

        Map<String, String> notes2 = new HashMap<>();
        notes2.put("baf", "meow");

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Assert.assertFalse(singleUseStore.replace(key, notes2));

            singleUseStore.put(key, 3, notes);
            singleUseStore.put(key, 3, notes2);
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> actualNotes = singleUseStore.get(key);
            Assert.assertEquals(notes2, actualNotes);

            Assert.assertTrue(singleUseStore.replace(key, notes2));
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> actualNotes = singleUseStore.get(key);
            Assert.assertEquals(notes2, actualNotes);

            Assert.assertFalse(singleUseStore.putIfAbsent(key, 3));

            Assert.assertEquals(notes2, singleUseStore.remove(key));
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Assert.assertTrue(singleUseStore.putIfAbsent(key, 3));
        });

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> actualNotes = singleUseStore.get(key);
            assertThat(actualNotes, Matchers.anEmptyMap());

            sleep(5000);

            Assert.assertNull(singleUseStore.get(key));
        });
    }

    @TestOnServer
    public void testNullValueInNotes(KeycloakSession testSession) {

        inCommittedTransaction(testSession, session -> {
            SingleUseObjectProvider singleUseStore = session.singleUseObjects();
            Map<String, String> nullNotes = new HashMap<>();
            nullNotes.put("key1", null);
            singleUseStore.put("key", 5, nullNotes);

            Assert.assertNull(singleUseStore.get("key").get("key1"));
        });
    }

    private static void sleep(int waitTimeMs) {
        try {
            Thread.sleep(waitTimeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public static class SingleUseRealmConfig implements RealmConfig {
        @Override
        public RealmConfigBuilder configure(RealmConfigBuilder realm) {
            realm.addUser("user").id(USER_ID);
            return realm;
        }
    }
}
