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

import static org.hamcrest.MatcherAssert.assertThat;

import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.keycloak.common.Version;
import org.keycloak.models.KeycloakSession;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class DeploymentStateModelTest extends CassandraModelTest {

    @TestOnServer
    public void resourcesVersionIsSetAndSeedPersisted(KeycloakSession testSession) {
        assertThat(Version.RESOURCES_VERSION, Matchers.notNullValue());

        String firstSeed = inCommittedTransaction(testSession, session -> {
            return session.getProvider(CassandraConnectionProvider.class)
                    .getRepository()
                    .getOrCreateResourcesVersionSeed("deployment-state");
        });
        Assert.assertNotNull(firstSeed);

        String secondSeed = inCommittedTransaction(testSession, session -> {
            return session.getProvider(CassandraConnectionProvider.class)
                    .getRepository()
                    .getOrCreateResourcesVersionSeed("deployment-state");
        });
        assertThat(secondSeed, Matchers.is(firstSeed));
    }
}
