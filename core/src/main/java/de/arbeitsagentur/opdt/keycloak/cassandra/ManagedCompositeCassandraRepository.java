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
package de.arbeitsagentur.opdt.keycloak.cassandra;

import static de.arbeitsagentur.opdt.keycloak.cassandra.cache.KeycloakSessionCache.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.AuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.entities.RootAuthenticationSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.InvalidateCache;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.L1Cached;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.entities.Client;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.ClientScopeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopes;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.DeploymentStateRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.GroupRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.ClientInitialAccess;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.entities.Realm;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Roles;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.entities.SingleUseObject;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.FederatedIdentity;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.IssuedVerifiableCredential;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.UserConsent;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.UserVerifiableCredential;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSession;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.UserSessionToAttributeMapping;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Setter;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.models.RealmModel;

@Setter
public class ManagedCompositeCassandraRepository implements CompositeRepository {
    private UserRepository userRepository;

    private RoleRepository roleRepository;

    private GroupRepository groupRepository;

    private RealmRepository realmRepository;

    private UserSessionRepository userSessionRepository;

    private AuthSessionRepository authSessionRepository;

    private LoginFailureRepository loginFailureRepository;

    private SingleUseObjectRepository singleUseObjectRepository;

    private DeploymentStateRepository deploymentStateRepository;

    private ClientRepository clientRepository;

    private ClientScopeRepository clientScopeRepository;

    public Stream<User> findAllUsers() {
        return this.userRepository.findAllUsers();
    }

    @L1Cached(cacheName = USER_CACHE)
    public User findUserById(String realmId, String id) {
        return this.userRepository.findUserById(realmId, id);
    }

    @L1Cached(cacheName = USER_CACHE)
    public User findUserByEmail(String realmId, String email) {
        return this.userRepository.findUserByEmail(realmId, email);
    }

    @L1Cached(cacheName = USER_CACHE)
    public User findUserByUsername(String realmId, String username) {
        return this.userRepository.findUserByUsername(realmId, username);
    }

    @L1Cached(cacheName = USER_CACHE)
    public User findUserByUsernameCaseInsensitive(String realmId, String username) {
        return this.userRepository.findUserByUsernameCaseInsensitive(realmId, username);
    }

    @L1Cached(cacheName = USER_CACHE)
    public User findUserByServiceAccountLink(String realmId, String serviceAccountLink) {
        return this.userRepository.findUserByServiceAccountLink(realmId, serviceAccountLink);
    }

    public Stream<User> findUsersByFederationLink(String realmId, String federationLink) {
        return this.userRepository.findUsersByFederationLink(realmId, federationLink);
    }

    public Stream<User> findUsersByIndexedAttribute(String realmId, String attributeName, String attributeValue) {
        return this.userRepository.findUsersByIndexedAttribute(realmId, attributeName, attributeValue);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void deleteUsernameSearchIndex(String realmId, User user) {
        this.userRepository.deleteUsernameSearchIndex(realmId, user);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void deleteEmailSearchIndex(String realmId, User user) {
        this.userRepository.deleteEmailSearchIndex(realmId, user);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void deleteFederationLinkSearchIndex(String realmId, User user) {
        this.userRepository.deleteFederationLinkSearchIndex(realmId, user);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void deleteServiceAccountLinkSearchIndex(String realmId, User user) {
        this.userRepository.deleteServiceAccountLinkSearchIndex(realmId, user);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void deleteAttributeSearchIndex(String realmId, User user, String attrName) {
        this.userRepository.deleteAttributeSearchIndex(realmId, user, attrName);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void insertOrUpdate(User user) {
        this.userRepository.insertOrUpdate(user);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public boolean deleteUser(String realmId, String userId) {
        return this.userRepository.deleteUser(realmId, userId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void makeUserServiceAccount(User user, String realmId) {
        this.userRepository.makeUserServiceAccount(user, realmId);
    }

    @L1Cached(cacheName = USER_CACHE)
    public FederatedIdentity findFederatedIdentity(String userId, String identityProvider) {
        return this.userRepository.findFederatedIdentity(userId, identityProvider);
    }

    @L1Cached(cacheName = USER_CACHE)
    public FederatedIdentity findFederatedIdentityByBrokerUserId(String brokerUserId, String identityProvider) {
        return this.userRepository.findFederatedIdentityByBrokerUserId(brokerUserId, identityProvider);
    }

    @L1Cached(cacheName = USER_CACHE)
    public List<FederatedIdentity> findFederatedIdentities(String userId) {
        return this.userRepository.findFederatedIdentities(userId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity) {
        this.userRepository.createOrUpdateFederatedIdentity(federatedIdentity);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public boolean deleteFederatedIdentity(String userId, String identityProvider) {
        return this.userRepository.deleteFederatedIdentity(userId, identityProvider);
    }

    @L1Cached(cacheName = USER_CACHE)
    public Set<String> findUserIdsByRealmId(String realmId, int first, int max) {
        return this.userRepository.findUserIdsByRealmId(realmId, first, max);
    }

    @L1Cached(cacheName = USER_CACHE)
    public long countUsersByRealmId(String realmId, boolean includeServiceAccounts) {
        return this.userRepository.countUsersByRealmId(realmId, includeServiceAccounts);
    }

    @L1Cached(cacheName = USER_CONSENT_CACHE)
    @InvalidateCache
    public void createOrUpdateUserConsent(UserConsent consent) {
        this.userRepository.createOrUpdateUserConsent(consent);
    }

    @L1Cached(cacheName = USER_CONSENT_CACHE)
    @InvalidateCache
    public boolean deleteUserConsent(String realmId, String userId, String clientId) {
        return this.userRepository.deleteUserConsent(realmId, userId, clientId);
    }

    @L1Cached(cacheName = USER_CONSENT_CACHE)
    @InvalidateCache
    public boolean deleteUserConsentsByUserId(String realmId, String userId) {
        return this.userRepository.deleteUserConsentsByUserId(realmId, userId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    @Override
    public boolean deleteFederatedIdentitiesByUserId(String userId) {
        return userRepository.deleteFederatedIdentitiesByUserId(userId);
    }

    @L1Cached(cacheName = USER_CONSENT_CACHE)
    public UserConsent findUserConsent(String realmId, String userId, String clientId) {
        return this.userRepository.findUserConsent(realmId, userId, clientId);
    }

    @L1Cached(cacheName = USER_CONSENT_CACHE)
    public List<UserConsent> findUserConsentsByUserId(String realmId, String userId) {
        return this.userRepository.findUserConsentsByUserId(realmId, userId);
    }

    @L1Cached(cacheName = USER_CONSENT_CACHE)
    public List<UserConsent> findUserConsentsByRealmId(String realmId) {
        return this.userRepository.findUserConsentsByRealmId(realmId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void insertOrUpdateVerifiableCredential(UserVerifiableCredential verifiableCredential) {
        this.userRepository.insertOrUpdateVerifiableCredential(verifiableCredential);
    }

    @L1Cached(cacheName = USER_CACHE)
    public UserVerifiableCredential findVerifiableCredential(String userId, String clientScopeId) {
        return this.userRepository.findVerifiableCredential(userId, clientScopeId);
    }

    @L1Cached(cacheName = USER_CACHE)
    public UserVerifiableCredential findVerifiableCredentialById(String id) {
        return this.userRepository.findVerifiableCredentialById(id);
    }

    @L1Cached(cacheName = USER_CACHE)
    public List<UserVerifiableCredential> findVerifiableCredentialsByUser(String userId) {
        return this.userRepository.findVerifiableCredentialsByUser(userId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public boolean deleteVerifiableCredential(String userId, String clientScopeId, String id) {
        return this.userRepository.deleteVerifiableCredential(userId, clientScopeId, id);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void insertOrUpdateIssuedVerifiableCredential(
            IssuedVerifiableCredential issuedVerifiableCredential, Integer ttl) {
        this.userRepository.insertOrUpdateIssuedVerifiableCredential(issuedVerifiableCredential, ttl);
    }

    @L1Cached(cacheName = USER_CACHE)
    public List<IssuedVerifiableCredential> findIssuedVerifiableCredentialsByUser(String userId) {
        return this.userRepository.findIssuedVerifiableCredentialsByUser(userId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public boolean deleteIssuedVerifiableCredentialById(String id) {
        return this.userRepository.deleteIssuedVerifiableCredentialById(id);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public boolean deleteIssuedVerifiableCredential(String userId, String id) {
        return this.userRepository.deleteIssuedVerifiableCredential(userId, id);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void deleteAllVerifiableCredentialsByUser(String userId) {
        this.userRepository.deleteAllVerifiableCredentialsByUser(userId);
    }

    @L1Cached(cacheName = USER_CACHE)
    @InvalidateCache
    public void insertOrUpdate(Roles role) {
        this.roleRepository.insertOrUpdate(role);
    }

    @L1Cached(cacheName = ROLE_CACHE)
    public Roles getRolesByRealmId(String realmId) {
        return this.roleRepository.getRolesByRealmId(realmId);
    }

    @L1Cached(cacheName = ROLE_CACHE)
    public void deleteRealmRoles(String realmId) {
        this.roleRepository.deleteRealmRoles(realmId);
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void insertOrUpdate(Realm realm) {
        this.realmRepository.insertOrUpdate(realm);
    }

    @L1Cached(cacheName = REALM_CACHE)
    public Realm getRealmById(String id) {
        return this.realmRepository.getRealmById(id);
    }

    @L1Cached(cacheName = REALM_CACHE)
    public List<Realm> getAllRealms() {
        return this.realmRepository.getAllRealms();
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void createRealm(Realm realm) {
        this.realmRepository.createRealm(realm);
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void deleteRealm(Realm realm) {
        this.realmRepository.deleteRealm(realm);
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void deleteNameToRealm(String name) {
        this.realmRepository.deleteNameToRealm(name);
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void insertOrUpdate(ClientInitialAccess model) {
        this.realmRepository.insertOrUpdate(model);
    }

    @L1Cached(cacheName = REALM_CACHE)
    public List<ClientInitialAccess> getAllClientInitialAccessesByRealmId(String realmId) {
        return this.realmRepository.getAllClientInitialAccessesByRealmId(realmId);
    }

    @L1Cached(cacheName = REALM_CACHE)
    public List<ClientInitialAccess> getAllClientInitialAccesses() {
        return this.realmRepository.getAllClientInitialAccesses();
    }

    @L1Cached(cacheName = REALM_CACHE)
    public ClientInitialAccess getClientInitialAccess(String realmId, String id) {
        return this.realmRepository.getClientInitialAccess(realmId, id);
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void deleteClientInitialAccess(ClientInitialAccess access) {
        this.realmRepository.deleteClientInitialAccess(access);
    }

    @L1Cached(cacheName = REALM_CACHE)
    @InvalidateCache
    public void deleteClientInitialAccess(String realmId, String id) {
        this.realmRepository.deleteClientInitialAccess(realmId, id);
    }

    @L1Cached(cacheName = REALM_CACHE)
    public Realm findRealmByName(String name) {
        return this.realmRepository.findRealmByName(name);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    @InvalidateCache
    public void insert(RealmModel realmModel, UserSession session) {
        this.userSessionRepository.insert(realmModel, session);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    @InvalidateCache
    public void update(UserSession session) {
        this.userSessionRepository.update(session);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    @InvalidateCache
    public void addClientSession(RealmModel realm, UserSession session, AuthenticatedClientSessionValue clientSession) {
        this.userSessionRepository.addClientSession(realm, session, clientSession);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public UserSession findUserSessionById(String id) {
        return this.userSessionRepository.findUserSessionById(id);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public List<UserSession> findAll() {
        return this.userSessionRepository.findAll();
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public List<UserSession> findUserSessionsByBrokerSession(String brokerSessionId) {
        return this.userSessionRepository.findUserSessionsByBrokerSession(brokerSessionId);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public List<UserSession> findUserSessionsByUserId(String userId) {
        return this.userSessionRepository.findUserSessionsByUserId(userId);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public List<UserSession> findUserSessionsByClientId(String clientId) {
        return this.userSessionRepository.findUserSessionsByClientId(clientId);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public List<UserSession> findUserSessionsByBrokerUserId(String brokerUserId) {
        return this.userSessionRepository.findUserSessionsByBrokerUserId(brokerUserId);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    @InvalidateCache
    public void deleteUserSession(UserSession session) {
        this.userSessionRepository.deleteUserSession(session);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    @InvalidateCache
    public void deleteUserSession(String id) {
        this.userSessionRepository.deleteUserSession(id);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    @InvalidateCache
    public void deleteCorrespondingUserSession(UserSession session) {
        this.userSessionRepository.deleteCorrespondingUserSession(session);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public Set<String> findUserSessionIdsByAttribute(String name, String value, int firstResult, int maxResult) {
        return this.userSessionRepository.findUserSessionIdsByAttribute(name, value, firstResult, maxResult);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public List<UserSession> findUserSessionsByAttribute(String name, String value) {
        return this.userSessionRepository.findUserSessionsByAttribute(name, value);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public UserSession findFirstUserSessionByAttribute(String name, String value) {
        return this.userSessionRepository.findFirstUserSessionByAttribute(name, value);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public MultivaluedHashMap<String, String> findAllUserSessionAttributes(String userSessionId) {
        return this.userSessionRepository.findAllUserSessionAttributes(userSessionId);
    }

    @L1Cached(cacheName = USER_SESSION_CACHE)
    public UserSessionToAttributeMapping findUserSessionAttribute(String userSessionId, String attributeName) {
        return this.userSessionRepository.findUserSessionAttribute(userSessionId, attributeName);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    @InvalidateCache
    public void insertOrUpdate(RootAuthenticationSession session) {
        this.authSessionRepository.insertOrUpdate(session);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    @InvalidateCache
    public void insertOrUpdate(AuthenticationSession session, RootAuthenticationSession parent) {
        this.authSessionRepository.insertOrUpdate(session, parent);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    @InvalidateCache
    public void deleteRootAuthSession(String sessionId) {
        this.authSessionRepository.deleteRootAuthSession(sessionId);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    @InvalidateCache
    public void deleteRootAuthSession(RootAuthenticationSession session) {
        this.authSessionRepository.deleteRootAuthSession(session);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    @InvalidateCache
    public void deleteAuthSession(AuthenticationSession session) {
        this.authSessionRepository.deleteAuthSession(session);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    @InvalidateCache
    public void deleteAuthSessions(String parentSessionId) {
        this.authSessionRepository.deleteAuthSessions(parentSessionId);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    public List<AuthenticationSession> findAuthSessionsByParentSessionId(String parentSessionId) {
        return this.authSessionRepository.findAuthSessionsByParentSessionId(parentSessionId);
    }

    @L1Cached(cacheName = AUTH_SESSION_CACHE)
    public RootAuthenticationSession findRootAuthSessionById(String id) {
        return this.authSessionRepository.findRootAuthSessionById(id);
    }

    @L1Cached(cacheName = LOGIN_FAILURE_CACHE)
    @InvalidateCache
    public void insertOrUpdate(LoginFailure loginFailure) {
        this.loginFailureRepository.insertOrUpdate(loginFailure);
    }

    @L1Cached(cacheName = LOGIN_FAILURE_CACHE)
    public List<LoginFailure> findLoginFailuresByUserId(String userId) {
        return this.loginFailureRepository.findLoginFailuresByUserId(userId);
    }

    @L1Cached(cacheName = LOGIN_FAILURE_CACHE)
    @InvalidateCache
    public void deleteLoginFailure(LoginFailure loginFailure) {
        this.loginFailureRepository.deleteLoginFailure(loginFailure);
    }

    @L1Cached(cacheName = LOGIN_FAILURE_CACHE)
    @InvalidateCache
    public void deleteLoginFailureByUserId(String userId) {
        this.loginFailureRepository.deleteLoginFailureByUserId(userId);
    }

    @L1Cached(cacheName = LOGIN_FAILURE_CACHE)
    public List<LoginFailure> findAllLoginFailures() {
        return this.loginFailureRepository.findAllLoginFailures();
    }

    @L1Cached(cacheName = SUO_CACHE)
    public SingleUseObject findSingleUseObjectByKey(String key) {
        return this.singleUseObjectRepository.findSingleUseObjectByKey(key);
    }

    @L1Cached(cacheName = SUO_CACHE)
    @InvalidateCache
    public void insertOrUpdate(SingleUseObject singleUseObject, int ttl) {
        this.singleUseObjectRepository.insertOrUpdate(singleUseObject, ttl);
    }

    @L1Cached(cacheName = SUO_CACHE)
    @InvalidateCache
    public void insertOrUpdate(SingleUseObject singleUseObject) {
        this.singleUseObjectRepository.insertOrUpdate(singleUseObject);
    }

    @L1Cached(cacheName = SUO_CACHE)
    @InvalidateCache
    public boolean deleteSingleUseObjectByKey(String key) {
        return this.singleUseObjectRepository.deleteSingleUseObjectByKey(key);
    }

    public String getOrCreateResourcesVersionSeed(String id) {
        return this.deploymentStateRepository.getOrCreateResourcesVersionSeed(id);
    }

    @L1Cached(cacheName = CLIENT_CACHE)
    @InvalidateCache
    public void insertOrUpdate(Client client) {
        this.clientRepository.insertOrUpdate(client);
    }

    @L1Cached(cacheName = CLIENT_CACHE)
    @InvalidateCache
    public void delete(Client client) {
        this.clientRepository.delete(client);
    }

    @L1Cached(cacheName = CLIENT_CACHE)
    public Client getClientById(String realmId, String id) {
        return this.clientRepository.getClientById(realmId, id);
    }

    @L1Cached(cacheName = CLIENT_CACHE)
    public Client findByClientId(String realmId, String clientId) {
        return this.clientRepository.findByClientId(realmId, clientId);
    }

    @L1Cached(cacheName = CLIENT_CACHE)
    public long countClientsByRealm(String realmId) {
        return this.clientRepository.countClientsByRealm(realmId);
    }

    @L1Cached(cacheName = CLIENT_CACHE)
    public List<Client> findAllClientsWithRealmId(String realmId) {
        return this.clientRepository.findAllClientsWithRealmId(realmId);
    }

    @L1Cached(cacheName = CLIENT_SCOPE_CACHE)
    @InvalidateCache
    public void insertOrUpdate(ClientScopes clientScopes) {
        this.clientScopeRepository.insertOrUpdate(clientScopes);
    }

    @L1Cached(cacheName = CLIENT_SCOPE_CACHE)
    public ClientScopes getClientScopesByRealmId(String realmId) {
        return this.clientScopeRepository.getClientScopesByRealmId(realmId);
    }

    @L1Cached(cacheName = CLIENT_SCOPE_CACHE)
    @InvalidateCache
    public void removeClientScopes(String realmId) {
        this.clientScopeRepository.removeClientScopes(realmId);
    }

    @L1Cached(cacheName = GROUP_CACHE)
    @InvalidateCache
    public void insertOrUpdate(Groups groups) {
        this.groupRepository.insertOrUpdate(groups);
    }

    @L1Cached(cacheName = GROUP_CACHE)
    public Groups getGroupsByRealmId(String realmId) {
        return this.groupRepository.getGroupsByRealmId(realmId);
    }

    @L1Cached(cacheName = GROUP_CACHE)
    @InvalidateCache
    public void deleteRealmGroups(String realmId) {
        this.groupRepository.deleteRealmGroups(realmId);
    }
}
