/*
 * Copyright 2024 IT-Systemhaus der Bundesagentur fuer Arbeit
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package de.arbeitsagentur.opdt.keycloak.compatibility;

import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraCacheProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.CommunityProfiles.isCassandraProfileEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import java.util.Map;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.keycloak.sessions.StickySessionEncoderProvider;
import org.keycloak.sessions.StickySessionEncoderProviderFactory;

/**
 * Identical with "disabled"-provider from map storage days but without environment dependent
 * activation
 */
@AutoService(StickySessionEncoderProviderFactory.class)
public class DisabledStickySessionEncoderProvider
        implements StickySessionEncoderProviderFactory,
                StickySessionEncoderProvider,
                EnvironmentDependentProviderFactory,
                ServerInfoAwareProviderFactory {

    @Override
    public StickySessionEncoderProvider create(KeycloakSession session) {
        return createProviderCached(session, StickySessionEncoderProvider.class, () -> this);
    }

    @Override
    public String encodeSessionId(String sessionId) {
        return sessionId;
    }

    @Override
    public String encodeSessionId(String sessionId, String route) {
        return sessionId;
    }

    @Override
    public String decodeSessionId(String encodedSessionId) {
        return encodedSessionId;
    }

    @Override
    public String sessionIdRoute(String sessionId) {
        return null;
    }

    @Override
    public StickySessionEncoderProvider.SessionIdAndRoute decodeSessionIdAndRoute(String encodedSessionId) {
        return new SessionIdAndRoute(encodedSessionId, null);
    }

    @Override
    public boolean shouldAttachRoute() {
        return false;
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return "infinispan"; // use same name as infinispan provider to override it
    }

    @Override
    public int order() {
        return PROVIDER_PRIORITY + 1;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return isCassandraProfileEnabled() || isCassandraCacheProfileEnabled();
    }

    @Override
    public Map<String, String> getOperationalInfo() {
        return Map.of("implementation", "disabled (cassandra-extension)");
    }

    @Override
    public void setShouldAttachRoute(boolean b) {
        // do nothing
    }
}
