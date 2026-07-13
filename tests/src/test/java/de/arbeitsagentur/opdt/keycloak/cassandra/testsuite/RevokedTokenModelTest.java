/*
 * Copyright 2026 IT-Systemhaus der Bundesagentur fuer Arbeit
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

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.UUID;
import org.junit.Assert;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RevokedTokenProvider;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class RevokedTokenModelTest extends CassandraModelTest {

    @TestOnServer
    public void testPutAndContains(KeycloakSession testSession) {
        String tokenId = UUID.randomUUID().toString();

        inCommittedTransaction(testSession, session -> {
            RevokedTokenProvider provider = session.revokedTokens();
            Assert.assertFalse(provider.contains(tokenId));
            Assert.assertTrue(provider.put(tokenId, 60L));
        });

        inCommittedTransaction(testSession, session -> {
            RevokedTokenProvider provider = session.revokedTokens();
            Assert.assertTrue(provider.contains(tokenId));
            Assert.assertFalse(provider.put(tokenId, 60L));
        });
    }

    @TestOnServer
    public void testExpiry(KeycloakSession testSession) {
        String tokenId = UUID.randomUUID().toString();

        inCommittedTransaction(testSession, session -> {
            session.revokedTokens().put(tokenId, 3L);
        });
        inCommittedTransaction(testSession, session -> {
            Assert.assertTrue(session.revokedTokens().contains(tokenId));
        });

        sleep(5000);

        inCommittedTransaction(testSession, session -> {
            Assert.assertFalse(session.revokedTokens().contains(tokenId));
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
}
