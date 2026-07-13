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

import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.entities.DeploymentState;

@Dao
public interface DeploymentStateDao extends BaseDao {
    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    DeploymentState findById(String id);

    @Insert(ifNotExists = true)
    @StatementAttributes(executionProfileName = "write")
    DeploymentState insertIfNotExists(DeploymentState deploymentState);
}
