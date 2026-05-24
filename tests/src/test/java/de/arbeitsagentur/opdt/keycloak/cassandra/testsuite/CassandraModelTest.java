package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.keycloak.models.utils.KeycloakModelUtils.runJobInTransaction;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

@ExtendWith(TimeOffsetResetExtension.class)
public abstract class CassandraModelTest {

    protected static <T, R> R inCommittedTransaction(
            KeycloakSession serverSession, T parameter, BiFunction<KeycloakSession, T, R> what) {
        return inCommittedTransaction(serverSession.getKeycloakSessionFactory(), parameter, what, null);
    }

    protected static void inCommittedTransaction(KeycloakSession serverSession, Consumer<KeycloakSession> what) {
        inCommittedTransaction(serverSession, session -> {
            what.accept(session);
            return null;
        });
    }

    protected static <R> R inCommittedTransaction(KeycloakSession serverSession, Function<KeycloakSession, R> what) {
        return inCommittedTransaction(serverSession, 1, (session, ignored) -> what.apply(session), null);
    }

    protected static <T, R> R inCommittedTransaction(
            KeycloakSession serverSession,
            T parameter,
            BiFunction<KeycloakSession, T, R> what,
            BiConsumer<KeycloakSession, T> onCommit) {
        return inCommittedTransaction(serverSession.getKeycloakSessionFactory(), parameter, what, onCommit);
    }

    protected static <T, R> R inCommittedTransaction(
            KeycloakSessionFactory sessionFactory,
            T parameter,
            BiFunction<KeycloakSession, T, R> what,
            BiConsumer<KeycloakSession, T> onCommit) {
        AtomicReference<R> result = new AtomicReference<>();
        runJobInTransaction(sessionFactory, session -> {
            session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
                @Override
                protected void commitImpl() {
                    if (onCommit != null) {
                        onCommit.accept(session, parameter);
                    }
                }

                @Override
                protected void rollbackImpl() {
                    // Unsupported in Cassandra.
                }
            });
            result.set(what.apply(session, parameter));
        });
        return result.get();
    }

    protected static <R> R withRealm(
            KeycloakSession serverSession, String realmId, BiFunction<KeycloakSession, RealmModel, R> what) {
        return inCommittedTransaction(serverSession, session -> {
            RealmModel realm = session.realms().getRealm(realmId);
            if (realm == null) {
                realm = session.realms().getRealmByName(realmId);
            }
            session.getContext().setRealm(realm);
            return what.apply(session, realm);
        });
    }
}
