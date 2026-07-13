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

import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.FederatedIdentity;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.IssuedVerifiableCredential;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.UserConsent;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.UserVerifiableCredential;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public interface UserRepository {
    Stream<User> findAllUsers();

    User findUserById(String realmId, String id);

    User findUserByEmail(String realmId, String email);

    User findUserByUsername(String realmId, String username);

    User findUserByUsernameCaseInsensitive(String realmId, String username);

    User findUserByServiceAccountLink(String realmId, String serviceAccountLink);

    Stream<User> findUsersByFederationLink(String realmId, String federationLink);

    Stream<User> findUsersByIndexedAttribute(String realmId, String attributeName, String attributeValue);

    void deleteUsernameSearchIndex(String realmId, User user);

    void deleteEmailSearchIndex(String realmId, User user);

    void deleteFederationLinkSearchIndex(String realmId, User user);

    void deleteServiceAccountLinkSearchIndex(String realmId, User user);

    void deleteAttributeSearchIndex(String realmId, User user, String attrName);

    void insertOrUpdate(User user);

    boolean deleteUser(String realmId, String userId);

    void makeUserServiceAccount(User user, String realmId);

    FederatedIdentity findFederatedIdentity(String userId, String identityProvider);

    FederatedIdentity findFederatedIdentityByBrokerUserId(String brokerUserId, String identityProvider);

    List<FederatedIdentity> findFederatedIdentities(String userId);

    void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity);

    boolean deleteFederatedIdentity(String userId, String identityProvider);

    Set<String> findUserIdsByRealmId(String realmId, int first, int max);

    long countUsersByRealmId(String realmId, boolean includeServiceAccounts);

    void createOrUpdateUserConsent(UserConsent consent);

    boolean deleteUserConsent(String realmId, String userId, String clientId);

    boolean deleteUserConsentsByUserId(String realmId, String userId);

    boolean deleteFederatedIdentitiesByUserId(String userId);

    UserConsent findUserConsent(String realmId, String userId, String clientId);

    List<UserConsent> findUserConsentsByUserId(String realmId, String userId);

    List<UserConsent> findUserConsentsByRealmId(String realmId);

    void insertOrUpdateVerifiableCredential(UserVerifiableCredential verifiableCredential);

    UserVerifiableCredential findVerifiableCredential(String userId, String clientScopeId);

    UserVerifiableCredential findVerifiableCredentialById(String id);

    List<UserVerifiableCredential> findVerifiableCredentialsByUser(String userId);

    boolean deleteVerifiableCredential(String userId, String clientScopeId, String id);

    void insertOrUpdateIssuedVerifiableCredential(IssuedVerifiableCredential issuedVerifiableCredential, Integer ttl);

    List<IssuedVerifiableCredential> findIssuedVerifiableCredentialsByUser(String userId);

    boolean deleteIssuedVerifiableCredentialById(String id);

    boolean deleteIssuedVerifiableCredential(String userId, String id);

    void deleteAllVerifiableCredentialsByUser(String userId);
}
