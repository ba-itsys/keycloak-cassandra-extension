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
package de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities;

import com.datastax.oss.driver.api.mapper.annotations.ClusteringColumn;
import com.datastax.oss.driver.api.mapper.annotations.CqlName;
import com.datastax.oss.driver.api.mapper.annotations.Entity;
import com.datastax.oss.driver.api.mapper.annotations.PartitionKey;
import java.util.List;
import java.util.Map;
import lombok.*;

@EqualsAndHashCode(of = {"userId", "clientScopeId"})
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@CqlName("user_verifiable_credentials")
public class UserVerifiableCredential {
    @PartitionKey
    private String userId;

    @ClusteringColumn
    private String clientScopeId;

    private String id;
    private String revision;
    private Map<String, List<String>> userAttributes;
    private Long createdDate;
    private Long updatedDate;
}
