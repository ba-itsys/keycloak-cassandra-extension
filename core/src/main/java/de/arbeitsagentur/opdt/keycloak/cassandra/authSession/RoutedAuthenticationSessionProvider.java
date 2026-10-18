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
package de.arbeitsagentur.opdt.keycloak.cassandra.authSession;

import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.Area;
import de.arbeitsagentur.opdt.keycloak.cassandra.ConditionalAreaRouter;
import java.util.Map;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionCompoundId;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.sessions.RootAuthenticationSessionModel;

public class RoutedAuthenticationSessionProvider extends ConditionalAreaRouter<AuthenticationSessionProvider>
        implements AuthenticationSessionProvider {

    public RoutedAuthenticationSessionProvider(
            KeycloakSession session, AuthenticationSessionProvider cassandra, String cassandraFactoryId) {
        super(session, Area.AUTH_SESSION, AuthenticationSessionProvider.class, cassandra, cassandraFactoryId);
    }

    @Override
    public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm) {
        return select(realm).createRootAuthenticationSession(realm);
    }

    @Override
    public RootAuthenticationSessionModel createRootAuthenticationSession(RealmModel realm, String id) {
        return select(realm).createRootAuthenticationSession(realm, id);
    }

    @Override
    public RootAuthenticationSessionModel getRootAuthenticationSession(
            RealmModel realm, String authenticationSessionId) {
        return select(realm).getRootAuthenticationSession(realm, authenticationSessionId);
    }

    @Override
    public void removeRootAuthenticationSession(
            RealmModel realm, RootAuthenticationSessionModel authenticationSession) {
        select(realm).removeRootAuthenticationSession(realm, authenticationSession);
    }

    @Override
    public void removeAllExpired() {
        cassandra().removeAllExpired();
        fallback().removeAllExpired();
    }

    @Override
    public void removeExpired(RealmModel realm) {
        select(realm).removeExpired(realm);
    }

    @Override
    public void removeRootAuthenticationSessionsByAuthenticatedUser(
            RealmModel realm, UserModel user, String rootAuthenticationSessionIdToKeep) {
        select(realm)
                .removeRootAuthenticationSessionsByAuthenticatedUser(realm, user, rootAuthenticationSessionIdToKeep);
    }

    @Override
    public void onRealmRemoved(RealmModel realm) {
        select(realm).onRealmRemoved(realm);
    }

    @Override
    public void onClientRemoved(RealmModel realm, ClientModel client) {
        select(realm).onClientRemoved(realm, client);
    }

    @Override
    public void updateNonlocalSessionAuthNotes(
            AuthenticationSessionCompoundId compoundId, Map<String, String> authNotesFragment) {
        select(null).updateNonlocalSessionAuthNotes(compoundId, authNotesFragment);
    }

    @Override
    public void migrate(String modelVersion) {
        select(null).migrate(modelVersion);
    }
}
