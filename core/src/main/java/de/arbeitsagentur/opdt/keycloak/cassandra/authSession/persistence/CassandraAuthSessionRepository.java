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

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSessionByUser;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.keycloak.common.util.Time;

@RequiredArgsConstructor
public class CassandraAuthSessionRepository implements AuthSessionRepository {
    private final AuthSessionDao dao;

    @Override
    public void insertOrUpdate(RootAuthenticationSession session) {
        if (session.getExpiration() == null) {
            dao.insertOrUpdate(session);
        } else {
            int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                    TimeAdapter.fromMilliSecondsToSeconds(session.getExpiration() - Time.currentTimeMillis()));
            dao.insertOrUpdate(session, ttl);
        }

        // Update TTL
        findAuthSessionsByParentSessionId(session.getId()).forEach(s -> insertOrUpdate(s, session));
    }

    @Override
    public void insertOrUpdate(AuthenticationSession session, RootAuthenticationSession parent) {
        RootAuthenticationSessionByUser userIndex = session.getUserId() == null
                ? null
                : new RootAuthenticationSessionByUser(parent.getRealmId(), session.getUserId(), parent.getId());
        if (parent.getExpiration() == null) {
            dao.insertOrUpdate(session);
            if (userIndex != null) {
                dao.insert(userIndex);
            }
            return;
        }

        int ttl = TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(
                TimeAdapter.fromMilliSecondsToSeconds(parent.getExpiration() - Time.currentTimeMillis()));
        dao.insertOrUpdate(session, ttl);
        if (userIndex != null) {
            dao.insert(userIndex, ttl);
        }
    }

    @Override
    public void deleteRootAuthSession(String sessionId) {
        dao.deleteRootAuthSession(sessionId);
        deleteAuthSessions(sessionId);
    }

    @Override
    public void deleteRootAuthSession(RootAuthenticationSession session) {
        dao.delete(session);
        deleteAuthSessions(session.getId());
    }

    @Override
    public void deleteAuthSession(AuthenticationSession session) {
        dao.delete(session);
    }

    @Override
    public void deleteAuthSessions(String parentSessionId) {
        dao.deleteAuthSessions(parentSessionId);
    }

    @Override
    public List<AuthenticationSession> findAuthSessionsByParentSessionId(String parentSessionId) {
        return dao.findByParentSessionId(parentSessionId).all();
    }

    @Override
    public RootAuthenticationSession findRootAuthSessionById(String id) {
        return dao.findById(id);
    }

    @Override
    public List<String> findRootAuthSessionIdsByUser(String realmId, String userId) {
        return dao.findRootAuthSessionsByUser(realmId, userId).all().stream()
                .map(RootAuthenticationSessionByUser::getRootSessionId)
                .toList();
    }

    @Override
    public void deleteRootAuthSessionUserIndex(String realmId, String userId, String rootSessionId) {
        dao.delete(new RootAuthenticationSessionByUser(realmId, userId, rootSessionId));
    }
}
