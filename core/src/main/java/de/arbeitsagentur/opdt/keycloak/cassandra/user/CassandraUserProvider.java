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
package de.arbeitsagentur.opdt.keycloak.cassandra.user;

import static de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserAdapter.isUsernameCaseSensitive;
import static org.keycloak.common.util.StackUtil.getShortStackTrace;

import de.arbeitsagentur.opdt.keycloak.cassandra.AttributeTypes;
import de.arbeitsagentur.opdt.keycloak.cassandra.transaction.TransactionalProvider;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.FederatedIdentity;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.User;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.UserConsent;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.common.constants.ServiceAccountConstants;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.utils.StringUtil;

@JBossLog
public class CassandraUserProvider extends TransactionalProvider<User, CassandraUserAdapter> implements UserProvider {

    private final UserRepository userRepository;

    public CassandraUserProvider(KeycloakSession session, UserRepository userRepository) {
        super(session);
        this.userRepository = userRepository;
    }

    @Override
    protected CassandraUserAdapter createNewModel(RealmModel realm, User entity) {
        return createNewModel(realm, entity, () -> {});
    }

    private CassandraUserAdapter createNewModelWithRollback(RealmModel realm, User entity) {
        return createNewModel(realm, entity, () -> {
            userRepository.deleteUser(realm.getId(), entity.getId());
            models.remove(entity.getId());
        });
    }

    private CassandraUserAdapter createNewModel(RealmModel realm, User entity, Runnable rollbackAction) {
        return new CassandraUserAdapter(session, entity, realm, userRepository) {
            @Override
            public boolean checkEmailUniqueness(RealmModel realm, String email) {
                return getUserByEmail(realm, email) != null;
            }

            @Override
            public boolean checkUsernameUniqueness(RealmModel realm, String username) {
                return getUserByUsername(realm, username) != null;
            }

            @Override
            public SubjectCredentialManager credentialManager() {
                return new CassandraCredentialManager(session, realm, userRepository, this, entity);
            }

            @Override
            public void rollback() {
                rollbackAction.run();
            }
        };
    }

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        return addUser(realm, null, username, true, true);
    }

    @Override
    public UserModel addUser(
            RealmModel realm, String id, String username, boolean addDefaultRoles, boolean addDefaultRequiredActions) {
        log.debugv("addUser realm={0} id={1} username={2}", realm, id, username);

        UserModel existingUser = getUserByUsername(realm, username);
        boolean isServiceAccount = username.startsWith(ServiceAccountConstants.SERVICE_ACCOUNT_USER_PREFIX);

        if (existingUser != null && !isServiceAccount) {
            throw new ModelDuplicateException(
                    "User with username '" + username + "' in realm " + realm.getName() + " already exists");
        }

        if (usernameEqualsExistingEmail(realm, username) && !isServiceAccount) {
            throw new ModelDuplicateException("User using username '"
                    + username
                    + "' as email-address already exists in realm "
                    + realm.getName());
        }

        if (id != null && userRepository.findUserById(realm.getId(), id) != null && !isServiceAccount) {
            throw new ModelDuplicateException("User exists: " + id);
        }

        User user = User.builder()
                .id(id == null ? KeycloakModelUtils.generateId() : id)
                .realmId(realm.getId())
                .createdTimestamp(Instant.now())
                .build();

        CassandraUserAdapter userModel =
                entityToAdapterFunc(realm, this::createNewModelWithRollback).apply(user);
        userModel.setUsername(username);

        if (addDefaultRoles) {
            if (realm.getDefaultRole() != null) {
                userModel.grantRole(realm.getDefaultRole());
            }

            // No need to check if user has group as it's new user
            realm.getDefaultGroupsStream().forEach(userModel::joinGroup);
        }

        if (addDefaultRequiredActions) {
            realm.getRequiredActionProvidersStream()
                    .filter(RequiredActionProviderModel::isEnabled)
                    .filter(RequiredActionProviderModel::isDefaultAction)
                    .map(RequiredActionProviderModel::getAlias)
                    .forEach(userModel::addRequiredAction);
        }

        if (existingUser == null) {
            userModel.commit(); // initial save
        } else {
            userModel = (CassandraUserAdapter) getUserById(realm, existingUser.getId());
        }

        if (username.startsWith(ServiceAccountConstants.SERVICE_ACCOUNT_USER_PREFIX)) {
            String clientId = username.replace(ServiceAccountConstants.SERVICE_ACCOUNT_USER_PREFIX, "");
            ClientModel client = session.clients().getClientByClientId(realm, clientId);

            if (client != null && client.getAttribute("initialServiceAccountRoles") != null) {
                List<String> initialServiceAccountRoles = Arrays.stream(
                                client.getAttribute("initialServiceAccountRoles")
                                        .split(","))
                        .map(String::trim)
                        .collect(Collectors.toList());

                for (String roleId : initialServiceAccountRoles) {
                    RoleModel role = session.roles().getRoleById(realm, roleId);

                    if (role != null
                            && "true"
                                    .equalsIgnoreCase(
                                            role.getFirstAttribute("unattendedServiceAccountAssignment.enabled"))) {
                        String allowedClientIdPattern =
                                role.getFirstAttribute("unattendedServiceAccountAssignment.clientIdPattern");
                        if (!StringUtil.isNullOrEmpty(allowedClientIdPattern)) {
                            if (!Pattern.matches(allowedClientIdPattern, clientId)) {
                                continue;
                            }
                        }

                        userModel.grantRole(role);
                    }
                }
            }
        }
        return userModel;
    }

    private boolean usernameEqualsExistingEmail(RealmModel realm, String username) {
        if (!CassandraUserAdapter.isCheckForDuplicatesAcrossUsernameAndEmailEnabled(realm)) {
            return false;
        }

        if (realm.isDuplicateEmailsAllowed()) {
            return false;
        }

        return getUserByEmail(realm, username) != null;
    }

    @Override
    public void setNotBeforeForUser(RealmModel realm, UserModel user, int notBefore) {
        UserModel newlyLoaded = getByIdOrThrow(realm, user);
        newlyLoaded.setSingleAttribute(CassandraUserAdapter.NOT_BEFORE, String.valueOf(notBefore));
    }

    @Override
    public int getNotBeforeOfUser(RealmModel realm, UserModel user) {
        UserModel newlyLoaded = getByIdOrThrow(realm, user);
        String notBeforeAttribute = newlyLoaded.getFirstAttribute(CassandraUserAdapter.NOT_BEFORE);
        if (notBeforeAttribute == null) {
            return 0;
        }
        return Integer.parseInt(notBeforeAttribute);
    }

    @Override
    public UserModel getServiceAccount(ClientModel client) {
        UserModel userModel = models.values().stream()
                .filter(model -> model.getRealm().equals(client.getRealm()))
                .filter(model -> model.getServiceAccountClientLink() != null)
                .filter(model -> model.getServiceAccountClientLink().equals(client.getId()))
                .findFirst()
                .orElse(null);

        if (userModel != null) {
            return userModel;
        } else {
            User user = userRepository.findUserByServiceAccountLink(
                    client.getRealm().getId(), client.getId());
            return entityToAdapterFunc(client.getRealm()).apply(user);
        }
    }

    @Override
    public void removeImportedUsers(RealmModel realm, String storageProviderId) {
        log.tracef("removeImportedUsers(%s, %s)%s", realm, storageProviderId, getShortStackTrace());
        userRepository
                .findUsersByFederationLink(realm.getId(), storageProviderId)
                .map(entityToAdapterFunc(realm))
                .forEach(u -> removeUser(realm, u));
    }

    @Override
    public void unlinkUsers(RealmModel realm, String storageProviderId) {
        log.tracef("unlinkUsers(%s, %s)%s", realm, storageProviderId, getShortStackTrace());
        userRepository
                .findUsersByFederationLink(realm.getId(), storageProviderId)
                .forEach(u -> userRepository.deleteFederationLinkSearchIndex(realm.getId(), u));
    }

    @Override
    public void addConsent(RealmModel realm, String userId, UserConsentModel consent) {
        log.debugv("addConsent({0}, {1}, {2})", realm, userId, consent);
        userRepository.createOrUpdateUserConsent(fromModel(realm, userId, consent));
    }

    @Override
    public UserConsentModel getConsentByClient(RealmModel realm, String userId, String clientInternalId) {
        log.debugv("getConsentByClient({0}, {1}, {2})", realm, userId, clientInternalId);
        UserConsent userConsent = userRepository.findUserConsent(realm.getId(), userId, clientInternalId);

        return toModel(realm, userConsent);
    }

    @Override
    public Stream<UserConsentModel> getConsentsStream(RealmModel realm, String userId) {
        log.debugv("getConsentByClientStream({0}, {1})", realm, userId);

        return userRepository.findUserConsentsByUserId(realm.getId(), userId).stream()
                .map(userConsent -> toModel(realm, userConsent));
    }

    @Override
    public void updateConsent(RealmModel realm, String userId, UserConsentModel consent) {
        log.debugv("updateConsent({0}, {1}, {2})", realm, userId, consent);

        UserConsent userConsent = userRepository.findUserConsent(
                realm.getId(), userId, consent.getClient().getId());

        userConsent.setGrantedClientScopesId(consent.getGrantedClientScopes().stream()
                .map(ClientScopeModel::getId)
                .collect(Collectors.toSet()));
        userConsent.setLastUpdatedTimestamp(Instant.now());

        userRepository.createOrUpdateUserConsent(userConsent);
    }

    @Override
    public boolean revokeConsentForClient(RealmModel realm, String userId, String clientInternalId) {
        log.debugv("revokeConsentForClient({0}, {1}, {2})", realm, userId, clientInternalId);

        return userRepository.deleteUserConsent(realm.getId(), userId, clientInternalId);
    }

    @Override
    public void addFederatedIdentity(RealmModel realm, UserModel user, FederatedIdentityModel socialLink) {
        updateFederatedIdentity(realm, user, socialLink);
    }

    private FederatedIdentity toFederatedIdentity(RealmModel realm, UserModel user, FederatedIdentityModel model) {
        return FederatedIdentity.builder()
                .userId(user.getId())
                .brokerUserId(model.getUserId())
                .brokerUserName(model.getUserName())
                .realmId(realm.getId())
                .identityProvider(model.getIdentityProvider())
                .token(model.getToken())
                .build();
    }

    private FederatedIdentityModel toModel(FederatedIdentity federatedIdentity) {
        if (federatedIdentity == null) {
            return null;
        }

        return new FederatedIdentityModel(
                federatedIdentity.getIdentityProvider(),
                federatedIdentity.getBrokerUserId(),
                federatedIdentity.getBrokerUserName(),
                federatedIdentity.getToken());
    }

    private UserConsentModel toModel(RealmModel realm, UserConsent userConsent) {
        if (userConsent == null) {
            return null;
        }

        ClientModel client = realm.getClientById(userConsent.getClientId());
        if (client == null) {
            throw new ModelException("Client with id " + userConsent.getClientId() + " is not available");
        }

        UserConsentModel model = new UserConsentModel(client);
        model.setCreatedDate(userConsent.getCreatedTimestamp().toEpochMilli());
        model.setLastUpdatedDate(userConsent.getLastUpdatedTimestamp().toEpochMilli());

        Set<String> grantedClientScopesIds = userConsent.getGrantedClientScopesId();

        if (grantedClientScopesIds != null && !grantedClientScopesIds.isEmpty()) {
            grantedClientScopesIds.stream()
                    .map(scopeId -> KeycloakModelUtils.findClientScopeById(realm, client, scopeId))
                    .filter(Objects::nonNull)
                    .forEach(model::addGrantedClientScope);
        }

        return model;
    }

    private UserConsent fromModel(RealmModel realm, String userId, UserConsentModel consentModel) {

        UserConsent userConsent = new UserConsent();
        userConsent.setRealmId(realm.getId());
        userConsent.setClientId(consentModel.getClient().getId());
        userConsent.setUserId(userId);

        consentModel.getGrantedClientScopes().stream()
                .map(ClientScopeModel::getId)
                .forEach(userConsent::addGrantedClientScopesId);

        return userConsent;
    }

    @Override
    public boolean removeFederatedIdentity(RealmModel realm, UserModel user, String socialProvider) {
        return userRepository.deleteFederatedIdentity(user.getId(), socialProvider);
    }

    @Override
    public void updateFederatedIdentity(
            RealmModel realm, UserModel federatedUser, FederatedIdentityModel federatedIdentityModel) {
        if (federatedUser == null || federatedUser.getId() == null) {
            return;
        }

        userRepository.createOrUpdateFederatedIdentity(
                toFederatedIdentity(realm, federatedUser, federatedIdentityModel));
    }

    @Override
    public Stream<FederatedIdentityModel> getFederatedIdentitiesStream(RealmModel realm, UserModel user) {
        return userRepository.findFederatedIdentities(user.getId()).stream().map(this::toModel);
    }

    @Override
    public FederatedIdentityModel getFederatedIdentity(RealmModel realm, UserModel user, String socialProvider) {
        FederatedIdentity federatedIdentity = userRepository.findFederatedIdentity(user.getId(), socialProvider);
        return toModel(federatedIdentity);
    }

    @Override
    public UserModel getUserByFederatedIdentity(RealmModel realm, FederatedIdentityModel socialLink) {
        FederatedIdentity federatedIdentity = userRepository.findFederatedIdentityByBrokerUserId(
                socialLink.getUserId(), socialLink.getIdentityProvider());

        if (federatedIdentity == null) {
            return null;
        }

        User userById = userRepository.findUserById(realm.getId(), federatedIdentity.getUserId());
        return entityToAdapterFunc(realm).apply(userById);
    }

    @Override
    public void grantToAllUsers(RealmModel realm, RoleModel role) {
        searchForUserStream(realm, "").forEach(u -> u.grantRole(role));
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        log.debugv("getUserById realm={0} id={1}", realm, id);
        CassandraUserAdapter existingUser = models.get(id);
        if (existingUser != null) {
            return existingUser;
        }

        User userById = userRepository.findUserById(realm.getId(), id);
        return entityToAdapterFunc(realm).apply(userById);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        log.debugv("getUserByUsername realm={0} username={1}", realm, username);
        UserModel modelUser = models.values().stream()
                .filter(model -> model.getRealm().equals(realm))
                .filter(model -> model.hasUsername(username))
                .map(model -> (UserModel) model)
                .findFirst()
                .orElse(null);

        if (modelUser == null) {
            User userFromDb = isUsernameCaseSensitive(realm)
                    ? userRepository.findUserByUsername(realm.getId(), username)
                    : userRepository.findUserByUsernameCaseInsensitive(
                            realm.getId(), KeycloakModelUtils.toLowerCaseSafe(username));

            return userFromDb == null || models.containsKey(userFromDb.getId())
                    ? null
                    : entityToAdapterFunc(realm).apply(userFromDb);
        } else {
            return modelUser;
        }
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        log.debugv("getUserByEmail realm={0} email={1}", realm, email);
        UserModel modelUser = models.values().stream()
                .filter(model -> model.getRealm().equals(realm))
                .filter(model -> model.getEmail() != null && model.getEmail().equals(email))
                .map(model -> (UserModel) model)
                .findFirst()
                .orElse(null);

        if (modelUser == null) {
            User userFromDb = userRepository.findUserByEmail(realm.getId(), email);

            return userFromDb == null || models.containsKey(userFromDb.getId())
                    ? null
                    : entityToAdapterFunc(realm).apply(userFromDb);
        } else {
            return modelUser;
        }
    }

    @Override
    public Stream<UserModel> searchForUserStream(
            RealmModel realm, String search, Integer firstResult, Integer maxResults) {
        log.tracef(
                "searchForUserStream(%s, %s, %d, %d)%s", realm, search, firstResult, maxResults, getShortStackTrace());
        Map<String, String> attributes = new HashMap<>();
        attributes.put(UserModel.SEARCH, search);
        return searchForUserStream(realm, attributes, firstResult, maxResults);
    }

    @Override
    public Stream<UserModel> searchForUserStream(
            RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        log.debugf(
                "searchForUserStream(%s, %s, %d, %d)%s", realm, params, firstResult, maxResults, getShortStackTrace());

        int first = firstResult == null || firstResult < 0 ? 0 : firstResult;
        int resultCount = maxResults == null || maxResults < 0 ? Integer.MAX_VALUE : maxResults;

        boolean isExactSearch = Boolean.parseBoolean(params.getOrDefault(UserModel.EXACT, "false"));

        if (params.containsKey(UserModel.USERNAME) && isExactSearch) {
            return Stream.ofNullable(getUserByUsername(realm, params.get(UserModel.USERNAME)));
        } else if (params.containsKey(UserModel.EMAIL) && isExactSearch) {
            return Stream.ofNullable(getUserByEmail(realm, params.get(UserModel.EMAIL)));
        }

        Stream<UserModel> userModelStream = userRepository
                .findAllUsers()
                .filter(u -> u.getRealmId().equals(realm.getId()))
                .map(entityToAdapterFunc(realm));

        List<Predicate<UserModel>> filtersList = params.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), UserModel.EXACT))
                .map(entry -> {
                    if (entry.getValue() == null) {
                        return (Predicate<UserModel>) (UserModel u) -> true;
                    }

                    BiFunction<String, String, Predicate<UserModel>> makeAttributeComparator =
                            (attributeName, attributeValue) -> isExactSearch
                                    ? (Predicate<UserModel>) user ->
                                            Objects.equals(user.getFirstAttribute(attributeName), attributeValue)
                                    : (Predicate<UserModel>) user -> user.getFirstAttribute(attributeName) != null
                                            && user.getFirstAttribute(attributeName)
                                                    .contains(attributeValue);

                    BiFunction<String, String, Predicate<UserModel>> makeAttributeComparatorIgnoreCase =
                            (attributeName, attributeValue) -> isExactSearch
                                    ? (Predicate<UserModel>) (user) -> user.getFirstAttribute(attributeName) != null
                                            && user.getFirstAttribute(attributeName)
                                                    .equalsIgnoreCase(attributeValue)
                                    : (Predicate<UserModel>) (user) -> user.getFirstAttribute(attributeName) != null
                                            && user.getFirstAttribute(attributeName)
                                                    .toLowerCase()
                                                    .contains(attributeValue.toLowerCase());

                    BiFunction<String, String, Predicate<UserModel>> makeUsernameComparator =
                            isUsernameCaseSensitive(realm)
                                    ? makeAttributeComparatorIgnoreCase
                                    : makeAttributeComparator;

                    return switch (entry.getKey()) {
                        case UserModel.SEARCH -> makeUsernameComparator
                                .apply(UserModel.USERNAME, entry.getValue())
                                .or(makeAttributeComparator.apply(UserModel.EMAIL, entry.getValue()))
                                .or(makeAttributeComparator.apply(UserModel.FIRST_NAME, entry.getValue()))
                                .or(makeAttributeComparator.apply(UserModel.LAST_NAME, entry.getValue()));
                        case UserModel.USERNAME -> makeUsernameComparator.apply(UserModel.USERNAME, entry.getValue());
                        case UserModel.IDP_ALIAS -> makeAttributeComparator.apply(
                                UserModel.IDP_ALIAS, entry.getValue());
                        case UserModel.IDP_USER_ID -> makeAttributeComparator.apply(
                                UserModel.IDP_USER_ID, entry.getValue());
                        case UserModel.INCLUDE_SERVICE_ACCOUNT -> (Predicate<UserModel>) (UserModel u) ->
                                Boolean.parseBoolean(entry.getValue()) || u.getServiceAccountClientLink() == null;
                        default -> makeAttributeComparator.apply(entry.getKey(), entry.getValue());
                    };
                })
                .collect(Collectors.toList());

        return userModelStream
                .filter(user -> filtersList.stream().allMatch(predicate -> predicate.test(user)))
                .skip(first)
                .limit(resultCount);
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(
            RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        log.debugf(
                "getGroupMembersStream realmId=%s groupName=%s firstResult=%d maxResults=%d",
                realm.getId(), group.getName(), firstResult, maxResults);

        return userRepository
                .findAllUsers()
                .filter(user -> user.getRealmId().equals(realm.getId()))
                .filter(user -> user.getGroupsMembership().contains(group.getId()))
                .skip(firstResult == null || firstResult < 0 ? 0 : firstResult)
                .limit(maxResults == null || maxResults < 0 ? Long.MAX_VALUE : maxResults)
                .map(entityToAdapterFunc(realm));
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        log.debugf("Search with attribute %s:%s", attrName, attrValue);

        if (attrName == null || attrValue == null) {
            return Stream.empty();
        }

        if (attrName.startsWith(AttributeTypes.INDEXED_ATTRIBUTE_PREFIX)) {
            return Stream.concat(
                            models.values().stream()
                                    .filter(model -> model.getRealm().equals(realm))
                                    .filter(model -> model.getAttributes()
                                            .getOrDefault(attrName, Collections.emptyList())
                                            .contains(attrValue))
                                    .filter(u -> u.getServiceAccountClientLink() == null),
                            userRepository
                                    .findUsersByIndexedAttribute(realm.getId(), attrName, attrValue)
                                    .map(entityToAdapterFunc(realm))
                                    .filter(u -> u.getServiceAccountClientLink() == null)
                                    .sorted(Comparator.comparing(UserModel::getUsername)))
                    .map(UserModel.class::cast)
                    .distinct();
        } else {
            return Stream.concat(
                            models.values().stream()
                                    .filter(model -> model.getRealm().equals(realm))
                                    .filter(model -> model.getAttributes()
                                            .getOrDefault(attrName, Collections.emptyList())
                                            .contains(attrValue))
                                    .filter(u -> u.getServiceAccountClientLink() == null),
                            userRepository
                                    .findAllUsers()
                                    .filter(u -> u.getRealmId().equals(realm.getId()))
                                    .filter(u -> u.getAttribute(attrName).contains(attrValue))
                                    .filter(u -> u.getServiceAccountClientLink() == null)
                                    .map(entityToAdapterFunc(realm))
                                    .sorted(Comparator.comparing(UserModel::getUsername)))
                    .map(UserModel.class::cast)
                    .distinct();
        }
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        log.debugv("getUsersCount realm={0} includeServiceAccount={1}", realm.getId(), includeServiceAccount);

        return (int) userRepository.countUsersByRealmId(realm.getId(), includeServiceAccount);
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        userRepository.deleteUserConsentsByUserId(realm.getId(), user.getId());
        userRepository.deleteFederatedIdentitiesByUserId(user.getId());
        models.remove(user.getId());
        return ((CassandraUserAdapter) user).delete();
    }

    @Override
    public void preRemove(RealmModel realm) {
        log.tracef("preRemove[RealmModel](%s)%s", realm, getShortStackTrace());
        searchForUserStream(realm, "").forEach(u -> removeUser(realm, u));
        models.clear();
    }

    @Override
    public void preRemove(RealmModel realm, IdentityProviderModel provider) {
        String providerAlias = provider.getAlias();
        log.tracef(
                "preRemove[RealmModel realm, IdentityProviderModel provider](%s, %s)%s",
                realm, providerAlias, getShortStackTrace());

        userRepository.findUsersByFederationLink(realm.getId(), providerAlias).forEach(u -> {
            userRepository.deleteFederatedIdentity(realm.getId(), providerAlias);
        });
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        // NOOP (delete/ignore ad-hoc when read)
    }

    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
        // NOOP (delete/ignore ad-hoc when read)
    }

    @Override
    public void preRemove(RealmModel realm, ClientModel client) {
        String clientId = client.getId();
        String realmId = realm.getId();
        log.debugv("preRemove[ClientModel]({0}, {1})", realmId, clientId);

        List<UserConsent> userConsents = userRepository.findUserConsentsByRealmId(realmId);
        if (userConsents != null && !userConsents.isEmpty()) {
            userConsents.forEach(userConsent -> {
                if (userConsent.getClientId().equals(clientId))
                    userRepository.deleteUserConsent(realmId, userConsent.getUserId(), clientId);
            });
        }
    }

    @Override
    public void preRemove(ProtocolMapperModel protocolMapper) {
        // NOOP
    }

    @Override
    public void preRemove(ClientScopeModel clientScope) {
        String clientScopeId = clientScope.getId();
        String realmId = clientScope.getRealm().getId();
        log.debugv("preRemove[ClientScopeModel]({0})", clientScopeId);

        List<UserConsent> userConsents = userRepository.findUserConsentsByRealmId(realmId);
        if (userConsents != null && !userConsents.isEmpty()) {
            userConsents.forEach(userConsent -> {
                if (userConsent.removeGrantedClientScopesId(clientScopeId)) {
                    userConsent.setLastUpdatedTimestamp(Instant.now());
                    userRepository.createOrUpdateUserConsent(userConsent);
                }
            });
        }
    }

    @Override
    public void preRemove(RealmModel realm, ComponentModel component) {
        // NOOP
    }

    @Override
    public UserCredentialManager getUserCredentialManager(UserModel user) {
        return (UserCredentialManager) user.credentialManager();
    }

    private UserModel getByIdOrThrow(RealmModel realm, UserModel user) {
        UserModel userById = getUserById(realm, user.getId());
        if (userById == null) {
            throw new ModelException("Specified user doesn't exist.");
        }

        return userById;
    }
}
