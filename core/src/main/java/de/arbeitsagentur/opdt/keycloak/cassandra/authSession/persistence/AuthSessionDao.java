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
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence;

import com.datastax.oss.driver.api.core.PagingIterable;
import com.datastax.oss.driver.api.mapper.annotations.*;
import de.arbeitsagentur.opdt.keycloak.cassandra.BaseDao;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSessionByUser;

@Dao
public interface AuthSessionDao extends BaseDao {
    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(RootAuthenticationSession session, int ttl);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(RootAuthenticationSession session);

    @Update
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(AuthenticationSession session);

    @Update(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insertOrUpdate(AuthenticationSession session, int ttl);

    @Delete(entityClass = RootAuthenticationSession.class)
    @StatementAttributes(executionProfileName = "write")
    void deleteRootAuthSession(String id);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(RootAuthenticationSession session);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(AuthenticationSession session);

    @Delete(entityClass = AuthenticationSession.class, customWhereClause = "parent_session_id = :parentSessionId")
    @StatementAttributes(executionProfileName = "write")
    void deleteAuthSessions(String parentSessionId);

    @Select(customWhereClause = "parent_session_id = :parentSessionId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<AuthenticationSession> findByParentSessionId(String parentSessionId);

    @Insert(ttl = ":ttl")
    @StatementAttributes(executionProfileName = "write")
    void insert(RootAuthenticationSessionByUser index, int ttl);

    @Insert
    @StatementAttributes(executionProfileName = "write")
    void insert(RootAuthenticationSessionByUser index);

    @Select(customWhereClause = "realm_id = :realmId AND user_id = :userId")
    @StatementAttributes(executionProfileName = "read")
    PagingIterable<RootAuthenticationSessionByUser> findRootAuthSessionsByUser(String realmId, String userId);

    @Delete
    @StatementAttributes(executionProfileName = "write")
    void delete(RootAuthenticationSessionByUser index);

    @Select(customWhereClause = "id = :id")
    @StatementAttributes(executionProfileName = "read")
    RootAuthenticationSession findById(String id);
}
