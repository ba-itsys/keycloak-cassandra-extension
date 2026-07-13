package de.arbeitsagentur.opdt.keycloak.cassandra.identityProvider;

import static de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.isAreaEnabled;

import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.Area;
import org.keycloak.Config;
import org.keycloak.models.*;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

@AutoService(IdentityProviderStorageProviderFactory.class)
public class CassandraIdentityProviderStorageProviderFactory
        implements IdentityProviderStorageProviderFactory<CassandraIdentityProviderStorageProvider>,
                EnvironmentDependentProviderFactory {
    @Override
    public boolean isSupported(Config.Scope scope) {
        return isAreaEnabled(Area.IDENTITY_PROVIDER);
    }

    @Override
    public CassandraIdentityProviderStorageProvider create(KeycloakSession session) {
        return new CassandraIdentityProviderStorageProvider(session);
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return "cassandra";
    }

    @Override
    public int order() {
        return 11;
    } // Infinispan-Order + 1
}
