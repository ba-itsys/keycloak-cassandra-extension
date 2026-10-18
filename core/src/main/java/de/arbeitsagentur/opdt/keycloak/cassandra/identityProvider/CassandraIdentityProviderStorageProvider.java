package de.arbeitsagentur.opdt.keycloak.cassandra.identityProvider;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.broker.provider.util.IdentityProviderTypeUtil;
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
            IdentityProviderQuery query, Integer firstResult, Integer maxResults) {
        Set<String> includedProviderIds = includedProviderIds(query);
        Map<String, String> options = query.getOptions() == null ? Map.of() : query.getOptions();

        Stream<IdentityProviderModel> result = getRealm()
                .getIdentityProvidersStream()
                .filter(idp -> includedProviderIds == null || includedProviderIds.contains(idp.getProviderId()))
                .filter(idp -> options.entrySet().stream()
                        .allMatch(option -> matches(idp, option.getKey(), option.getValue())))
                .sorted(Comparator.comparing(IdentityProviderModel::getAlias));
        return paginate(result, firstResult, maxResults);
    }

    @Override
    public Stream<String> getByFlow(String flowId, String search, Integer firstResult, Integer maxResults) {
        Stream<String> result = getRealm()
                .getIdentityProvidersStream()
                .filter(idp -> StringUtil.isBlank(flowId)
                        || Objects.equals(idp.getFirstBrokerLoginFlowId(), flowId)
                        || Objects.equals(idp.getPostBrokerLoginFlowId(), flowId))
                .filter(idp -> StringUtil.isBlank(search) || matchesAliasSearch(idp, search))
                .sorted(Comparator.comparing(IdentityProviderModel::getAlias))
                .map(IdentityProviderModel::getAlias);
        return paginate(result, firstResult, maxResults);
    }

    private Set<String> includedProviderIds(IdentityProviderQuery query) {
        if (query.getType() != null && query.getType() != IdentityProviderType.ANY) {
            return Set.copyOf(IdentityProviderTypeUtil.listFactoriesByType(session, query.getType()));
        }
        if (query.getCapability() != null) {
            return Set.copyOf(IdentityProviderTypeUtil.listFactoriesByCapability(session, query.getCapability()));
        }
        return null;
    }

    private static boolean matches(IdentityProviderModel idp, String key, String value) {
        if (StringUtil.isBlank(key)) {
            return true;
        }
        return switch (key) {
            case IdentityProviderModel.AUTHENTICATE_BY_DEFAULT ->
                Boolean.parseBoolean(value) == Boolean.TRUE.equals(idp.isAuthenticateByDefault());
            case IdentityProviderModel.ENABLED -> Boolean.parseBoolean(value) == idp.isEnabled();
            case IdentityProviderModel.HIDE_ON_LOGIN ->
                Boolean.parseBoolean(value) == Boolean.TRUE.equals(idp.isHideOnLogin());
            case IdentityProviderModel.LINK_ONLY ->
                Boolean.parseBoolean(value) == Boolean.TRUE.equals(idp.isLinkOnly());
            case IdentityProviderModel.ALIAS -> matchesOptional(idp.getAlias(), value);
            case IdentityProviderModel.FIRST_BROKER_LOGIN_FLOW_ID ->
                matchesOptional(idp.getFirstBrokerLoginFlowId(), value);
            case IdentityProviderModel.POST_BROKER_LOGIN_FLOW_ID ->
                matchesOptional(idp.getPostBrokerLoginFlowId(), value);
            case IdentityProviderModel.ORGANIZATION_ID ->
                StringUtil.isBlank(value) ? !idp.hasOrganization() : idp.isLinkedToOrganization(value);
            case IdentityProviderModel.ORGANIZATION_ID_NOT_NULL -> idp.hasOrganization();
            case IdentityProviderModel.SEARCH -> StringUtil.isBlank(value) || matchesAliasSearch(idp, value);
            case IdentityProviderModel.ALIAS_NOT_IN ->
                StringUtil.isBlank(value) || !Arrays.asList(value.split(",")).contains(idp.getAlias());
            default -> matchesConfig(idp, key, value);
        };
    }

    private static boolean matchesOptional(String actual, String expected) {
        return StringUtil.isBlank(expected) ? actual == null : expected.equals(actual);
    }

    private static boolean matchesConfig(IdentityProviderModel idp, String key, String value) {
        String actual = idp.getConfig() == null ? null : idp.getConfig().get(key);
        if (Objects.equals(actual, value)) {
            return true;
        }
        return IdentityProviderModel.SHOW_IN_ACCOUNT_CONSOLE.equals(key)
                && IdentityProviderShowInAccountConsole.ALWAYS.name().equals(value)
                && actual == null;
    }

    // Same semantics as the JPA provider: "quoted" is exact, '*' is a wildcard, otherwise a prefix match
    private static boolean matchesAliasSearch(IdentityProviderModel idp, String search) {
        if (search.length() > 1 && search.startsWith("\"") && search.endsWith("\"")) {
            String exact = search.substring(1, search.length() - 1);
            return exact.equals(idp.getAlias()) || exact.equals(idp.getDisplayName());
        }
        String regex =
                Arrays.stream(search.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*"));
        Pattern pattern =
                Pattern.compile(regex + ".*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
        return Stream.of(idp.getAlias(), idp.getDisplayName())
                .filter(Objects::nonNull)
                .anyMatch(candidate -> pattern.matcher(candidate).matches());
    }

    private static <T> Stream<T> paginate(Stream<T> stream, Integer firstResult, Integer maxResults) {
        if (firstResult != null && firstResult > 0) {
            stream = stream.skip(firstResult);
        }
        if (maxResults != null && maxResults >= 0) {
            stream = stream.limit(maxResults);
        }
        return stream;
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
