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

import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import de.arbeitsagentur.opdt.keycloak.cassandra.StreamExtensions;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class CassandraUserRepository extends TransactionalRepository<User, UserDao> implements UserRepository {
    private static final String USERNAME = "username";
    private static final String USERNAME_CASE_INSENSITIVE = "usernameCaseInsensitive";
    private static final String EMAIL = "email";
    private static final String SERVICE_ACCOUNT_LINK = "serviceAccountLink";
    private static final String FEDERATION_LINK = "federationLink";

    public CassandraUserRepository(UserDao dao) {
        super(dao);
    }

    @Override
    public Stream<User> findAllUsers() {
        return StreamSupport.stream(dao.findAll().spliterator(), false);
    }

    @Override
    public User findUserById(String realmId, String id) {
        return dao.findById(realmId, id);
    }

    @Override
    public User findUserByEmail(String realmId, String email) {
        if (email == null) {
            return null;
        }

        List<User> users = findConsistentUsers(realmId, EMAIL, email, user -> email.equals(user.getEmail())).stream()
                .collect(Collectors.toList());

        if (users.size() > 1) {
            log.warn("Found multiple users with email: " + email);
            return null;
        }

        if (users.isEmpty()) {
            return null;
        }

        return users.getFirst();
    }

    @Override
    public User findUserByUsername(String realmId, String username) {
        if (username == null) {
            return null;
        }

        List<User> users =
                findConsistentUsers(realmId, USERNAME, username, user -> username.equals(user.getUsername())).stream()
                        .collect(Collectors.toList());

        if (users.size() > 1) {
            log.warn("Found multiple users with username: " + username);
            return null;
        }

        if (users.isEmpty()) {
            return null;
        }

        return users.getFirst();
    }

    @Override
    public User findUserByUsernameCaseInsensitive(String realmId, String username) {
        if (username == null) {
            return null;
        }

        List<User> users = findConsistentUsers(
                        realmId,
                        USERNAME_CASE_INSENSITIVE,
                        username,
                        user -> username.equals(user.getUsernameCaseInsensitive()))
                .stream()
                .collect(Collectors.toList());

        if (users.size() > 1) {
            log.warn("Found multiple users with username (case insensitive): " + username);
            return null;
        }

        if (users.isEmpty()) {
            return null;
        }

        return users.getFirst();
    }

    @Override
    public User findUserByServiceAccountLink(String realmId, String serviceAccountLink) {
        if (serviceAccountLink == null) {
            return null;
        }

        UserSearchIndex user = dao.findUsers(realmId, SERVICE_ACCOUNT_LINK, serviceAccountLink).all().stream()
                .findFirst()
                .orElse(null);

        if (user == null) {
            return null;
        }

        return findUserById(realmId, user.getUserId());
    }

    @Override
    public Stream<User> findUsersByFederationLink(String realmId, String federationLink) {
        if (federationLink == null) {
            return null;
        }

        List<String> userIds = dao.findUsers(realmId, FEDERATION_LINK, federationLink).all().stream()
                .map(UserSearchIndex::getUserId)
                .collect(Collectors.toList());

        return StreamSupport.stream(dao.findByIds(realmId, userIds).spliterator(), false);
    }

    @Override
    public Stream<User> findUsersByIndexedAttribute(String realmId, String attributeName, String attributeValue) {
        if (attributeName == null
                || attributeValue == null
                || !attributeName.startsWith(AttributeTypes.INDEXED_ATTRIBUTE_PREFIX)) {
            return Stream.empty();
        }

        List<String> userIds = dao.findUsers(realmId, attributeName, attributeValue).all().stream()
                .map(UserSearchIndex::getUserId)
                .collect(Collectors.toList());

        return StreamSupport.stream(dao.findByIds(realmId, userIds).spliterator(), false);
    }

    @Override
    public void deleteUsernameSearchIndex(String realmId, User user) {
        if (user.getUsername() != null) {
            dao.deleteIndex(realmId, USERNAME, user.getUsername(), user.getId());
        }

        if (user.getUsernameCaseInsensitive() != null) {
            dao.deleteIndex(realmId, USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user.getId());
        }
    }

    @Override
    public void deleteEmailSearchIndex(String realmId, User user) {
        if (user.getEmail() != null) {
            dao.deleteIndex(realmId, EMAIL, user.getEmail(), user.getId());
        }
    }

    @Override
    public void deleteFederationLinkSearchIndex(String realmId, User user) {
        if (user.getFederationLink() != null) {
            dao.deleteIndex(realmId, FEDERATION_LINK, user.getFederationLink(), user.getId());
        }
    }

    @Override
    public void deleteServiceAccountLinkSearchIndex(String realmId, User user) {
        if (user.getServiceAccountClientLink() != null) {
            dao.deleteIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId());
        }
    }

    @Override
    public void deleteAttributeSearchIndex(String realmId, User user, String attrName) {
        if (attrName != null && attrName.startsWith(AttributeTypes.INDEXED_ATTRIBUTE_PREFIX)) {
            user.getAttribute(attrName).forEach(value -> dao.deleteIndex(realmId, attrName, value, user.getId()));
        }
    }

    @Override
    public void insertOrUpdate(User user) {
        super.insertOrUpdate(user);

        dao.insert(new RealmToUserMapping(user.getRealmId(), user.isServiceAccount(), user.getId()));

        if (user.getUsername() != null) {
            dao.insertOrUpdate(new UserSearchIndex(user.getRealmId(), USERNAME, user.getUsername(), user.getId()));
        }

        if (user.getUsernameCaseInsensitive() != null) {
            dao.insertOrUpdate(new UserSearchIndex(
                    user.getRealmId(), USERNAME_CASE_INSENSITIVE, user.getUsernameCaseInsensitive(), user.getId()));
        }

        if (user.getEmail() != null && user.getHasEmailChanged()) {
            dao.insertOrUpdate(new UserSearchIndex(user.getRealmId(), EMAIL, user.getEmail(), user.getId()));
        }

        if (user.getServiceAccountClientLink() != null && user.getHasServiceAccountClientLinkChanged()) {
            dao.insertOrUpdate(new UserSearchIndex(
                    user.getRealmId(), SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId()));
        }

        if (user.getFederationLink() != null && user.getHasFederationLinkChanged()) {
            dao.insertOrUpdate(
                    new UserSearchIndex(user.getRealmId(), FEDERATION_LINK, user.getFederationLink(), user.getId()));
        }

        for (Map.Entry<String, List<String>> entry : user.getIndexedAttributes().entrySet()) {
            entry.getValue()
                    .forEach(value -> dao.insertOrUpdate(
                            new UserSearchIndex(user.getRealmId(), entry.getKey(), value, user.getId())));
        }
    }

    @Override
    public boolean deleteUser(String realmId, String userId) {
        User user = findUserById(realmId, userId);

        if (user == null) {
            return false;
        }

        dao.delete(user);
        dao.deleteRealmToUserMapping(realmId, user.isServiceAccount(), user.getId());

        deleteUsernameSearchIndex(realmId, user);
        deleteEmailSearchIndex(realmId, user);
        deleteServiceAccountLinkSearchIndex(realmId, user);
        deleteFederationLinkSearchIndex(realmId, user);

        for (Map.Entry<String, List<String>> entry : user.getIndexedAttributes().entrySet()) {
            entry.getValue().forEach(value -> dao.deleteIndex(realmId, entry.getKey(), value, userId));
        }

        return true;
    }

    private List<User> findConsistentUsers(
            String realmId, String indexName, String indexValue, Predicate<User> consistencyCheck) {
        return dao.findUsers(realmId, indexName, indexValue).all().stream()
                .map(idx -> findConsistentUser(realmId, idx, consistencyCheck))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private User findConsistentUser(String realmId, UserSearchIndex idx, Predicate<User> consistencyCheck) {
        User user = findUserById(realmId, idx.getUserId());

        if (user == null || !consistencyCheck.test(user)) {
            log.warnf(
                    "Deleting stale user search index entry realmId=%s name=%s value=%s userId=%s",
                    idx.getRealmId(), idx.getName(), idx.getValue(), idx.getUserId());
            dao.deleteIndex(idx.getRealmId(), idx.getName(), idx.getValue(), idx.getUserId());
            return null;
        }

        return user;
    }

    @Override
    public void makeUserServiceAccount(User user, String realmId) {
        user.setServiceAccount(true);
        super.insertOrUpdate(user);

        dao.deleteRealmToUserMapping(realmId, false, user.getId());
        dao.insert(new RealmToUserMapping(realmId, user.isServiceAccount(), user.getId()));

        dao.insertOrUpdate(
                new UserSearchIndex(realmId, SERVICE_ACCOUNT_LINK, user.getServiceAccountClientLink(), user.getId()));
    }

    @Override
    public FederatedIdentity findFederatedIdentity(String userId, String identityProvider) {
        return dao.findFederatedIdentity(userId, identityProvider);
    }

    @Override
    public FederatedIdentity findFederatedIdentityByBrokerUserId(String brokerUserId, String identityProvider) {
        FederatedIdentityToUserMapping identityToUserMapping =
                dao.findFederatedIdentityByBrokerUserId(brokerUserId, identityProvider);

        if (identityToUserMapping == null) {
            return null;
        }

        return findFederatedIdentity(identityToUserMapping.getUserId(), identityProvider);
    }

    @Override
    public List<FederatedIdentity> findFederatedIdentities(String userId) {
        return dao.findFederatedIdentities(userId).all();
    }

    @Override
    public void createOrUpdateFederatedIdentity(FederatedIdentity federatedIdentity) {
        dao.update(federatedIdentity);
        FederatedIdentityToUserMapping identityToUserMapping = new FederatedIdentityToUserMapping(
                federatedIdentity.getBrokerUserId(),
                federatedIdentity.getIdentityProvider(),
                federatedIdentity.getUserId());
        dao.update(identityToUserMapping);
    }

    @Override
    public boolean deleteFederatedIdentity(String userId, String identityProvider) {
        FederatedIdentity federatedIdentity = findFederatedIdentity(userId, identityProvider);

        if (federatedIdentity == null) {
            return false;
        }

        FederatedIdentityToUserMapping identityToUserMapping =
                dao.findFederatedIdentityByBrokerUserId(federatedIdentity.getBrokerUserId(), identityProvider);

        dao.delete(federatedIdentity);
        dao.delete(identityToUserMapping);
        return true;
    }

    @Override
    public Set<String> findUserIdsByRealmId(String realmId, int first, int max) {
        return StreamExtensions.paginated(dao.findUsersByRealmId(realmId), first, max)
                .map(RealmToUserMapping::getUserId)
                .collect(Collectors.toSet());
    }

    @Override
    public long countUsersByRealmId(String realmId, boolean includeServiceAccounts) {
        // Avoid count()-queries for Amazon Keyspaces support
        if (includeServiceAccounts) {
            return dao.findUsersByRealmId(realmId).all().size();
        } else {
            return dao.findNonServiceAccountUsersByRealmId(realmId).all().size();
        }
    }

    @Override
    public void createOrUpdateUserConsent(UserConsent consent) {
        dao.insertOrUpdate(consent);
    }

    @Override
    public boolean deleteUserConsent(String realmId, String userId, String clientId) {
        return dao.deleteUserConsent(realmId, userId, clientId);
    }

    @Override
    public boolean deleteUserConsentsByUserId(String realmId, String userId) {
        return dao.deleteUserConsentsByUserId(realmId, userId);
    }

    @Override
    public boolean deleteFederatedIdentitiesByUserId(String userId) {
        for (FederatedIdentity identity : dao.findFederatedIdentities(userId)) {
            dao.deleteFederatedIdentityToUserMapping(identity.getBrokerUserId(), identity.getIdentityProvider());
        }

        return dao.deleteFederatedIdentitiesByUserId(userId);
    }

    @Override
    public UserConsent findUserConsent(String realmId, String userId, String clientId) {
        return dao.findUserConsent(realmId, userId, clientId);
    }

    @Override
    public List<UserConsent> findUserConsentsByUserId(String realmId, String userId) {
        return dao.findUserConsentsByUserId(realmId, userId).all();
    }

    @Override
    public List<UserConsent> findUserConsentsByRealmId(String realmId) {
        return dao.findUserConsentsByRealmId(realmId).all();
    }
}
