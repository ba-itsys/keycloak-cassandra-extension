package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.keycloak.common.util.Time;

public class TimeOffsetResetExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        Time.setOffset(0);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Time.setOffset(0);
    }
}
