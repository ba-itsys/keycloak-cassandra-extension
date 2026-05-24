package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra;

import java.util.List;
import org.keycloak.testframework.TestFrameworkExtension;
import org.keycloak.testframework.injection.Supplier;

public class CassandraTestFrameworkExtension implements TestFrameworkExtension {

    @Override
    public List<Supplier<?, ?>> suppliers() {
        return List.of(new CassandraDatabaseSupplier());
    }
}
