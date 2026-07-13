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
package de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence;

import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities.DeploymentState;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.SecretGenerator;

@RequiredArgsConstructor
public class CassandraDeploymentStateRepository implements DeploymentStateRepository {
    private static final int SEED_LENGTH = 10;

    private final DeploymentStateDao dao;

    @Override
    public String getOrCreateResourcesVersionSeed(String id) {
        DeploymentState existing = dao.findById(id);
        if (existing != null) {
            return existing.getResourcesVersionSeed();
        }

        DeploymentState candidate = DeploymentState.builder()
                .id(id)
                .resourcesVersionSeed(SecretGenerator.getInstance().randomString(SEED_LENGTH))
                .build();
        DeploymentState winner = dao.insertIfNotExists(candidate);
        return winner != null ? winner.getResourcesVersionSeed() : candidate.getResourcesVersionSeed();
    }
}
