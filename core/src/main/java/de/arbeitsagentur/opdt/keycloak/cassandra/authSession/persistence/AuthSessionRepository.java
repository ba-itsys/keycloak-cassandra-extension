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
import java.util.List;

public interface AuthSessionRepository {
    void insertOrUpdate(RootAuthenticationSession session);

    void insertOrUpdate(AuthenticationSession session, RootAuthenticationSession parent);

    void deleteRootAuthSession(String sessionId);

    void deleteRootAuthSession(RootAuthenticationSession session);

    void deleteAuthSession(AuthenticationSession session);

    void deleteAuthSessions(String parentSessionId);

    List<AuthenticationSession> findAuthSessionsByParentSessionId(String parentSessionId);

    RootAuthenticationSession findRootAuthSessionById(String id);

    List<String> findRootAuthSessionIdsByUser(String realmId, String userId);

    void deleteRootAuthSessionUserIndex(String realmId, String userId, String rootSessionId);
}
