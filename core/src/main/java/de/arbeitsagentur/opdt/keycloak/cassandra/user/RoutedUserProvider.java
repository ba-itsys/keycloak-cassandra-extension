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
package de.arbeitsagentur.opdt.keycloak.cassandra.user;

import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.Area;
import de.arbeitsagentur.opdt.keycloak.cassandra.ConditionalAreaRouter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.ClientModel;
import org.keycloak.models.ClientScopeModel;
import org.keycloak.models.CredentialValidationOutput;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IssuedVerifiableCredentialModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserConsentModel;
import org.keycloak.models.UserCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserVerifiableCredentialModel;

public class RoutedUserProvider extends ConditionalAreaRouter<UserProvider> implements UserProvider {

    public RoutedUserProvider(KeycloakSession session, UserProvider cassandra, String cassandraFactoryId) {
        super(session, Area.USER, UserProvider.class, cassandra, cassandraFactoryId);
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        return select(realm).getUsersCount(realm);
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return select(realm).getUsersCount(realm, includeServiceAccount);
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return select(realm).getUsersCount(realm, search);
    }

    @Override
    public int getUsersCount(RealmModel realm, String search, Set<String> groupIds) {
        return select(realm).getUsersCount(realm, search, groupIds);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return select(realm).getUsersCount(realm, params);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params, Set<String> groupIds) {
        return select(realm).getUsersCount(realm, params, groupIds);
    }

    @Override
    public int getUsersCount(RealmModel realm, Set<String> groupIds) {
        return select(realm).getUsersCount(realm, groupIds);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group) {
        return select(realm).getGroupMembersStream(realm, group);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(
            RealmModel realm, GroupModel group, String search, Boolean exact, Integer first, Integer max) {
        return select(realm).getGroupMembersStream(realm, group, search, exact, first, max);
    }

    @Override
    public Stream<UserModel> getRoleMembersStream(RealmModel realm, RoleModel role) {
        return select(realm).getRoleMembersStream(realm, role);
    }

    @Override
    public Stream<UserModel> getRoleMembersStream(
            RealmModel realm, RoleModel role, Integer firstResult, Integer maxResults) {
        return select(realm).getRoleMembersStream(realm, role, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, String search) {
        return select(realm).searchForUserStream(realm, search);
    }

    @Override
    public Stream<UserModel> searchForUserStream(
            RealmModel realm, String search, Integer firstResult, Integer maxResults) {
        return select(realm).searchForUserStream(realm, search, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params) {
        return select(realm).searchForUserStream(realm, params);
    }

    @Override
    public CredentialValidationOutput getUserByCredential(RealmModel realm, CredentialInput input) {
        return select(realm).getUserByCredential(realm, input);
    }

    @Override
    public boolean removeIssuedVerifiableCredential(String credentialId) {
        return select(null).removeIssuedVerifiableCredential(credentialId);
    }

    @Override
    public boolean removeIssuedVerifiableCredential(String userId, String credentialId) {
        return select(null).removeIssuedVerifiableCredential(userId, credentialId);
    }

    @Override
    public boolean removeVerifiableCredential(String userId, String clientScopeId) {
        return select(null).removeVerifiableCredential(userId, clientScopeId);
    }

    @Override
    public Stream<IssuedVerifiableCredentialModel> getIssuedVerifiableCredentialsStreamByUser(String userId) {
        return select(null).getIssuedVerifiableCredentialsStreamByUser(userId);
    }

    @Override
    public Stream<UserVerifiableCredentialModel> getVerifiableCredentialsByUser(String userId) {
        return select(null).getVerifiableCredentialsByUser(userId);
    }

    @Override
    public IssuedVerifiableCredentialModel addIssuedVerifiableCredential(IssuedVerifiableCredentialModel issuedVc) {
        return select(null).addIssuedVerifiableCredential(issuedVc);
    }

    @Override
    public UserCredentialManager getUserCredentialManager(UserModel user) {
        return select(null).getUserCredentialManager(user);
    }

    @Override
    public UserModel getServiceAccount(ClientModel client) {
        return select(null).getServiceAccount(client);
    }

    @Override
    public UserVerifiableCredentialModel addVerifiableCredential(
            String userId, UserVerifiableCredentialModel credentialModel) {
        return select(null).addVerifiableCredential(userId, credentialModel);
    }

    @Override
    public UserVerifiableCredentialModel getVerifiableCredentialByClientScope(String userId, String clientScopeId) {
        return select(null).getVerifiableCredentialByClientScope(userId, clientScopeId);
    }

    @Override
    public UserVerifiableCredentialModel getVerifiableCredentialById(String id) {
        return select(null).getVerifiableCredentialById(id);
    }

    @Override
    public UserVerifiableCredentialModel updateVerifiableCredential(String userId, String clientScopeId) {
        return select(null).updateVerifiableCredential(userId, clientScopeId);
    }

    @Override
    public void preRemove(ClientScopeModel clientScope) {
        select(null).preRemove(clientScope);
    }

    @Override
    public void preRemove(ProtocolMapperModel protocolMapper) {
        select(null).preRemove(protocolMapper);
    }

    @Override
    public void removeExpiredIssuedVerifiableCredentials() {
        select(null).removeExpiredIssuedVerifiableCredentials();
    }

    @Override
    public boolean removeFederatedIdentity(RealmModel realm, UserModel user, String socialProvider) {
        return select(realm).removeFederatedIdentity(realm, user, socialProvider);
    }

    @Override
    public boolean revokeConsentForClient(RealmModel realm, String userId, String clientInternalId) {
        return select(realm).revokeConsentForClient(realm, userId, clientInternalId);
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        return select(realm).removeUser(realm, user);
    }

    @Override
    public int getNotBeforeOfUser(RealmModel realm, UserModel user) {
        return select(realm).getNotBeforeOfUser(realm, user);
    }

    @Override
    public Stream<FederatedIdentityModel> getFederatedIdentitiesStream(RealmModel realm, UserModel user) {
        return select(realm).getFederatedIdentitiesStream(realm, user);
    }

    @Override
    public Stream<UserConsentModel> getConsentsStream(RealmModel realm, String userId) {
        return select(realm).getConsentsStream(realm, userId);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(
            RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        return select(realm).getGroupMembersStream(realm, group, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        return select(realm).searchForUserByUserAttributeStream(realm, attrName, attrValue);
    }

    @Override
    public Stream<UserModel> searchForUserStream(
            RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        return select(realm).searchForUserStream(realm, params, firstResult, maxResults);
    }

    @Override
    public FederatedIdentityModel getFederatedIdentity(RealmModel realm, UserModel user, String socialProvider) {
        return select(realm).getFederatedIdentity(realm, user, socialProvider);
    }

    @Override
    public UserConsentModel getConsentByClient(RealmModel realm, String userId, String clientInternalId) {
        return select(realm).getConsentByClient(realm, userId, clientInternalId);
    }

    @Override
    public UserModel addUser(
            RealmModel realm, String id, String username, boolean addDefaultRoles, boolean addDefaultRequiredActions) {
        return select(realm).addUser(realm, id, username, addDefaultRoles, addDefaultRequiredActions);
    }

    @Override
    public UserModel getUserByFederatedIdentity(RealmModel realm, FederatedIdentityModel socialLink) {
        return select(realm).getUserByFederatedIdentity(realm, socialLink);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        return select(realm).getUserByEmail(realm, email);
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        return select(realm).getUserById(realm, id);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        return select(realm).getUserByUsername(realm, username);
    }

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        return select(realm).addUser(realm, username);
    }

    @Override
    public void addConsent(RealmModel realm, String userId, UserConsentModel consent) {
        select(realm).addConsent(realm, userId, consent);
    }

    @Override
    public void addFederatedIdentity(RealmModel realm, UserModel user, FederatedIdentityModel socialLink) {
        select(realm).addFederatedIdentity(realm, user, socialLink);
    }

    @Override
    public void preRemove(RealmModel realm) {
        select(realm).preRemove(realm);
    }

    @Override
    public void preRemove(RealmModel realm, ComponentModel component) {
        select(realm).preRemove(realm, component);
    }

    @Override
    public void preRemove(RealmModel realm, ClientModel client) {
        select(realm).preRemove(realm, client);
    }

    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
        select(realm).preRemove(realm, group);
    }

    @Override
    public void preRemove(RealmModel realm, IdentityProviderModel provider) {
        select(realm).preRemove(realm, provider);
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        select(realm).preRemove(realm, role);
    }

    @Override
    public void removeImportedUsers(RealmModel realm, String storageProviderId) {
        select(realm).removeImportedUsers(realm, storageProviderId);
    }

    @Override
    public void setNotBeforeForUser(RealmModel realm, UserModel user, int notBefore) {
        select(realm).setNotBeforeForUser(realm, user, notBefore);
    }

    @Override
    public void unlinkUsers(RealmModel realm, String storageProviderId) {
        select(realm).unlinkUsers(realm, storageProviderId);
    }

    @Override
    public void updateConsent(RealmModel realm, String userId, UserConsentModel consent) {
        select(realm).updateConsent(realm, userId, consent);
    }

    @Override
    public void updateFederatedIdentity(
            RealmModel realm, UserModel federatedUser, FederatedIdentityModel federatedIdentityModel) {
        select(realm).updateFederatedIdentity(realm, federatedUser, federatedIdentityModel);
    }

    @Override
    public void grantToAllUsers(RealmModel realm, RoleModel role) {
        select(realm).grantToAllUsers(realm, role);
    }
}
