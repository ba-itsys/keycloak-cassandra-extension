/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
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
package de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;
import java.util.List;

@Dao
public interface UserDao extends TransactionalDao<User> {
    @Update
    @StatementAttributes(executionProfileName = "write")
    void update(FederatedIdentity federatedIdentity);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void update(FederatedIdentityToUserMapping identityToUserMapping);

    @Insert
    @StatementAttributes(executionProfileName = "write")
    void insert(RealmToUserMapping realmToUserMapping);

    @Select
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<User> findAll();

    @Select(customWhereClause = "realm_id = :realmId AND id = :id")
    @StatementAttributes(executionProfileName = "read")
    User findById(String realmId, String id);

    @Select(customWhereClause = "realm_id = :realmId AND id IN :ids")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<User> findByIds(String realmId, List<String> ids);

    @Select(customWhereClause = "user_id = :userId AND identity_provider = :identityProvider")
    @StatementAttributes(executionProfileName = "read")
    FederatedIdentity findFederatedIdentity(String userId, String identityProvider);

    @Select(customWhereClause = "broker_user_id = :brokerUserId AND identity_provider = :identityProvider")
    @StatementAttributes(executionProfileName = "read")
    FederatedIdentityToUserMapping findFederatedIdentityByBrokerUserId(String brokerUserId, String identityProvider);

    @Select(customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<FederatedIdentity> findFederatedIdentities(String userId);

    @Select(customWhereClause = "realm_id = :realmId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<RealmToUserMapping> findUsersByRealmId(String realmId);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    boolean delete(FederatedIdentity federatedIdentity);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    boolean delete(FederatedIdentityToUserMapping identityToUserMapping);

    @Delete(entityClass = RealmToUserMapping.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteRealmToUserMapping(String realmId, boolean serviceAccount, String userId);

    @Select(customWhereClause = "realm_id = :realmId AND service_account = false")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<RealmToUserMapping> findNonServiceAccountUsersByRealmId(String realmId);

    // Search
    @Insert
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(UserSearchIndex searchIndex);

    @Select(customWhereClause = "realm_id = :realmId AND name = :name AND value = :value")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<UserSearchIndex> findUsers(String realmId, String name, String value);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(UserSearchIndex searchIndex);

    @Delete(entityClass = UserSearchIndex.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteIndex(String realmId, String name, String value, String userId);

    @Insert
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(UserConsent userConsent);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(UserConsent userConsent);

    @Delete(entityClass = UserConsent.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteUserConsent(String realmId, String userId, String clientId);

    @Delete(entityClass = UserConsent.class, customWhereClause = "realm_id = :realmId AND user_id = :userId")
    @StatementAttributes(executionProfileName = "write")
    boolean deleteUserConsentsByUserId(String realmId, String userId);

    @Delete(entityClass = FederatedIdentity.class, customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "write")
    boolean deleteFederatedIdentitiesByUserId(String userId);

    @Delete(entityClass = FederatedIdentityToUserMapping.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteFederatedIdentityToUserMapping(String brokerUserId, String identityProvider);

    @Delete(entityClass = FederatedIdentity.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteFederatedIdentity(String userId, String identityProvider);

    @Select(customWhereClause = "realm_id = :realmId AND user_id = :userId AND client_id = :clientId")
    @StatementAttributes(executionProfileName = "read")
    UserConsent findUserConsent(String realmId, String userId, String clientId);

    @Select(customWhereClause = "realm_id = :realmId AND user_id = :userId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<UserConsent> findUserConsentsByUserId(String realmId, String userId);

    @Select(customWhereClause = "realm_id = :realmId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<UserConsent> findUserConsentsByRealmId(String realmId);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(UserVerifiableCredential verifiableCredential);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(UserVerifiableCredentialById pointer);

    @Select(customWhereClause = "user_id = :userId AND client_scope_id = :clientScopeId")
    @StatementAttributes(executionProfileName = "read")
    UserVerifiableCredential findVerifiableCredential(String userId, String clientScopeId);

    @Select(customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<UserVerifiableCredential> findVerifiableCredentialsByUser(String userId);

    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    UserVerifiableCredentialById findVerifiableCredentialPointer(String id);

    @Delete(entityClass = UserVerifiableCredential.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteVerifiableCredential(String userId, String clientScopeId);

    @Delete(entityClass = UserVerifiableCredentialById.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteVerifiableCredentialPointer(String id);

    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(IssuedVerifiableCredential issuedVerifiableCredential, int ttl);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(IssuedVerifiableCredential issuedVerifiableCredential);

    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(IssuedVerifiableCredentialById pointer, int ttl);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(IssuedVerifiableCredentialById pointer);

    @Select(customWhereClause = "user_id = :userId AND id = :id")
    @StatementAttributes(executionProfileName = "read")
    IssuedVerifiableCredential findIssuedVerifiableCredential(String userId, String id);

    @Select(customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<IssuedVerifiableCredential> findIssuedVerifiableCredentialsByUser(String userId);

    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    IssuedVerifiableCredentialById findIssuedVerifiableCredentialPointer(String id);

    @Delete(entityClass = IssuedVerifiableCredential.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteIssuedVerifiableCredential(String userId, String id);

    @Delete(entityClass = IssuedVerifiableCredentialById.class)
    @StatementAttributes(executionProfileName = "write")
    boolean deleteIssuedVerifiableCredentialPointer(String id);

    @Delete(entityClass = UserVerifiableCredential.class, customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "write")
    boolean deleteVerifiableCredentialsByUser(String userId);

    @Delete(entityClass = IssuedVerifiableCredential.class, customWhereClause = "user_id = :userId")
    @StatementAttributes(executionProfileName = "write")
    boolean deleteIssuedVerifiableCredentialsByUser(String userId);
}
