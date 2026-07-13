/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.authSession;

import static de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.isAreaEnabled;
import static de.arbeitsagentur.opdt.keycloak.common.ProviderHelpers.createProviderCached;
import static org.keycloak.userprofile.DeclarativeUserProfileProviderFactory.PROVIDER_PRIORITY;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.Area;
import de.arbeitsagentur.opdt.keycloak.cassandra.connection.CassandraConnectionProvider;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.sessions.AuthenticationSessionProviderFactory;

@AutoService(AuthenticationSessionProviderFactory.class)
public class CassandraAuthSessionProviderFactory
        implements AuthenticationSessionProviderFactory<CassandraAuthSessionProvider>,
                EnvironmentDependentProviderFactory {
    public static final String AUTH_SESSIONS_LIMIT = "authSessionsLimit";

    public static final int DEFAULT_AUTH_SESSIONS_LIMIT = 300;

    private int authSessionsLimit = 0;

    @Override
    public CassandraAuthSessionProvider create(KeycloakSession session) {
        CassandraConnectionProvider cassandraConnectionProvider =
                createProviderCached(session, CassandraConnectionProvider.class);
        return new CassandraAuthSessionProvider(
                session, cassandraConnectionProvider.getRepository(), authSessionsLimit);
    }

    @Override
    public void init(Config.Scope config) {
        int configInt = config.getInt(AUTH_SESSIONS_LIMIT, DEFAULT_AUTH_SESSIONS_LIMIT);
        // use default if provided value is not a positive number
        authSessionsLimit = (configInt <= 0) ? DEFAULT_AUTH_SESSIONS_LIMIT : configInt;
    }

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
        return isAreaEnabled(Area.AUTH_SESSION);
    }
}
