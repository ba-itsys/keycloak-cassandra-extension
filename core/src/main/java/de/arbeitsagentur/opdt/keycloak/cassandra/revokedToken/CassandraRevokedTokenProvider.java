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
package de.arbeitsagentur.opdt.keycloak.cassandra.revokedToken;

import lombok.RequiredArgsConstructor;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RevokedTokenProvider;
import org.keycloak.models.SingleUseObjectProvider;

@RequiredArgsConstructor
public class CassandraRevokedTokenProvider implements RevokedTokenProvider {
    private final KeycloakSession session;

    @Override
    public boolean put(String tokenId, long lifespanSeconds) {
        return session.singleUseObjects().putIfAbsent(tokenId + SingleUseObjectProvider.REVOKED_KEY, lifespanSeconds);
    }

    @Override
    public boolean contains(String tokenId) {
        return session.singleUseObjects().contains(tokenId + SingleUseObjectProvider.REVOKED_KEY);
    }

    @Override
    public void close() {}
}
