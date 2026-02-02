package de.arbeitsagentur.opdt.keycloak.cassandra.identityProvider;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.utils.StringUtil;

@JBossLog
public class CassandraIdentityProviderStorageProvider implements IdentityProviderStorageProvider {
    private final KeycloakSession session;

    public CassandraIdentityProviderStorageProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public IdentityProviderModel create(IdentityProviderModel model) {
        getRealm().addIdentityProvider(model);
        return getRealm().getIdentityProviderByAlias(model.getAlias());
    }

    @Override
    public void update(IdentityProviderModel model) {
        getRealm().updateIdentityProvider(model);
    }

    @Override
    public boolean remove(String providerAlias) {
        getRealm().removeIdentityProviderByAlias(providerAlias);
        return true;
    }

    @Override
    public void removeAll() {
        getRealm().getIdentityProvidersStream().forEach(idp -> getRealm()
                .removeIdentityProviderByAlias(idp.getAlias()));
    }

    @Override
    public IdentityProviderModel getById(String internalId) {
        return getRealm()
                .getIdentityProvidersStream()
                .filter(idp -> idp.getInternalId().equals(internalId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public IdentityProviderModel getByAlias(String alias) {
        return getRealm().getIdentityProviderByAlias(alias);
    }

    @Override
    public Stream<IdentityProviderModel> getAllStream(
            Map<String, String> options, Integer firstResult, Integer maxResults) {
        int first = firstResult == null || firstResult < 0 ? 0 : firstResult;
        int resultCount = maxResults == null || maxResults < 0 ? Integer.MAX_VALUE : maxResults;

        return getRealm()
                .getIdentityProvidersStream()
                .filter(idp -> {
                    if (options == null || options.isEmpty()) {
                        return true;
                    }

                    if (options.containsKey(IdentityProviderModel.ORGANIZATION_ID)) {
                        String organizationId = options.get(IdentityProviderModel.ORGANIZATION_ID);
                        if (!Objects.equals(idp.getOrganizationId(), organizationId)) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.ORGANIZATION_ID_NOT_NULL)) {
                        if (idp.getOrganizationId() == null) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.ENABLED)) {
                        boolean enabled = Boolean.parseBoolean(options.get(IdentityProviderModel.ENABLED));
                        if (idp.isEnabled() != enabled) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.HIDE_ON_LOGIN)) {
                        boolean hideOnLogin = Boolean.parseBoolean(options.get(IdentityProviderModel.HIDE_ON_LOGIN));
                        if (idp.isHideOnLogin() != hideOnLogin) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.LINK_ONLY)) {
                        boolean linkOnly = Boolean.parseBoolean(options.get(IdentityProviderModel.LINK_ONLY));
                        if (idp.isLinkOnly() != linkOnly) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.ALIAS)) {
                        String alias = options.get(IdentityProviderModel.ALIAS);
                        if (!idp.getAlias().equals(alias)) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.ALIAS_NOT_IN)) {
                        String aliasNotIn = options.get(IdentityProviderModel.ALIAS_NOT_IN);
                        if (Arrays.stream(aliasNotIn.split(","))
                                .anyMatch(alias -> idp.getAlias().equals(alias))) {
                            return false;
                        }
                    }

                    if (options.containsKey(IdentityProviderModel.SEARCH)) {
                        String search = options.get(IdentityProviderModel.SEARCH);
                        if (!StringUtil.isNullOrEmpty(search) && !idp.getAlias().contains(search)) {
                            return false;
                        }
                    }

                    return true;
                })
                .sorted(Comparator.comparing(IdentityProviderModel::getAlias))
                .skip(first)
                .limit(resultCount);
    }

    @Override
    public Stream<IdentityProviderModel> getAllStream(
            IdentityProviderQuery query, Integer firstResult, Integer maxResults) {
        // Delegate to existing method - filters will be applied by the query predicate in calling code
        return getAllStream(Map.of(), firstResult, maxResults);
    }

    @Override
    public Stream<String> getByFlow(String flowId, String search, Integer firstResult, Integer maxResults) {
        int first = firstResult == null || firstResult < 0 ? 0 : firstResult;
        int resultCount = maxResults == null || maxResults < 0 ? Integer.MAX_VALUE : maxResults;

        return getRealm()
                .getIdentityProvidersStream()
                .filter(idp -> search == null || idp.getAlias().contains(search.replace("*", "")))
                .filter(idp -> Objects.equals(idp.getFirstBrokerLoginFlowId(), flowId)
                        || Objects.equals(idp.getPostBrokerLoginFlowId(), flowId))
                .sorted(Comparator.comparing(IdentityProviderModel::getAlias))
                .skip(first)
                .limit(resultCount)
                .map(IdentityProviderModel::getAlias);
    }

    @Override
    public long count() {
        return getRealm().getIdentityProvidersStream().count();
    }

    @Override
    public IdentityProviderMapperModel createMapper(IdentityProviderMapperModel model) {
        return getRealm().addIdentityProviderMapper(model);
    }

    @Override
    public void updateMapper(IdentityProviderMapperModel model) {
        createMapper(model);
    }

    @Override
    public boolean removeMapper(IdentityProviderMapperModel model) {
        getRealm().removeIdentityProviderMapper(model);
        return true;
    }

    @Override
    public void removeAllMappers() {
        getRealm().getIdentityProviderMappersStream().forEach(getRealm()::removeIdentityProviderMapper);
    }

    @Override
    public IdentityProviderMapperModel getMapperById(String id) {
        return getRealm().getIdentityProviderMapperById(id);
    }

    @Override
    public IdentityProviderMapperModel getMapperByName(String identityProviderAlias, String name) {
        return getRealm().getIdentityProviderMapperByName(identityProviderAlias, name);
    }

    @Override
    public Stream<IdentityProviderMapperModel> getMappersStream(
            Map<String, String> options, Integer firstResult, Integer maxResults) {
        int first = firstResult == null || firstResult < 0 ? 0 : firstResult;
        int resultCount = maxResults == null || maxResults < 0 ? Integer.MAX_VALUE : maxResults;

        return getRealm()
                .getIdentityProviderMappersStream()
                .filter(idp -> {
                    if (options == null || options.isEmpty()) {
                        return true;
                    }

                    return idp.getConfig().entrySet().containsAll(options.entrySet());
                })
                .sorted(Comparator.comparing(IdentityProviderMapperModel::getName))
                .skip(first)
                .limit(resultCount);
    }

    @Override
    public Stream<IdentityProviderMapperModel> getMappersByAliasStream(String identityProviderAlias) {
        return getRealm()
                .getIdentityProviderMappersStream()
                .filter(mapper -> mapper.getIdentityProviderAlias().equals(identityProviderAlias))
                .sorted(Comparator.comparing(IdentityProviderMapperModel::getName));
    }

    @Override
    public void close() {}

    private RealmModel getRealm() {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new IllegalStateException("Session not bound to a realm");
        }
        return realm;
    }
}
