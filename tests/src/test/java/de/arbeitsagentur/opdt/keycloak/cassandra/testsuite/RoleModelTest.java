/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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

package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hamcrest.Matcher;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RoleModel;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ClientBuilder;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.RoleBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class RoleModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "role-model";

    private static final String MAIN_ROLE_ID = "main-role-id";
    private static final String CLIENT_WITH_ROLES = "client-with-roles";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = RoleModelRealmConfig.class)
    ManagedRealm managedRealm;

    private static List<String> rolesSubset() {
        return IntStream.range(0, 20).mapToObj(RoleModelRealmConfig::roleId).collect(Collectors.toList());
    }

    @FunctionalInterface
    public interface GetResult {
        List<RoleModel> getResult(String search, Integer first, Integer max);
    }

    private static List<RoleModel> getResult(
            KeycloakSession testSession, String realmName, String search, Integer first, Integer max) {
        return withRealm(testSession, realmName, (session, realm) -> session.roles()
                .getRolesStream(realm, rolesSubset().stream(), search, first, max)
                .collect(Collectors.toList()));
    }

    private static RoleModel getMainRole(KeycloakSession testSession, String realmName) {
        return withRealm(
                testSession, realmName, (session, realm) -> session.roles().getRoleById(realm, MAIN_ROLE_ID));
    }

    private static List<RoleModel> getModelResult(
            KeycloakSession testSession, String realmName, String search, Integer first, Integer max) {
        return withRealm(testSession, realmName, ((session, realm) -> session.roles()
                .getRoleById(realm, MAIN_ROLE_ID)
                .getCompositesStream(search, first, max)
                .collect(Collectors.toList())));
    }

    @TestOnServer
    public void testRolesWithIdsQueries(KeycloakSession testSession) {

        // should return all roles from the subset
        List<RoleModel> result = getResult(testSession, REALM_NAME, null, null, null);
        assertThat(result, hasSize(rolesSubset().size()));
        assertIndexValues(result, contains(0, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 3, 4, 5, 6, 7, 8, 9));

        // test non-existing role ids
        result = withRealm(testSession, REALM_NAME, (session, realm) -> session.roles()
                .getRolesStream(
                        realm,
                        IntStream.range(0, 10).boxed().map(i -> UUID.randomUUID()
                                .toString()),
                        null,
                        null,
                        null)
                .collect(Collectors.toList()));
        assertThat(result, is(empty()));

        // test mixed non-existing with existing
        result = withRealm(testSession, REALM_NAME, (session, realm) -> session.roles()
                .getRolesStream(
                        realm,
                        Stream.concat(
                                rolesSubset().subList(0, 10).stream(),
                                IntStream.range(0, 10).boxed().map(i -> UUID.randomUUID()
                                        .toString())),
                        null,
                        null,
                        null)
                .collect(Collectors.toList()));
        assertThat(result, hasSize(10));
        assertIndexValues(result, contains(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
    }

    @TestOnServer
    public void testCompositeRoles(KeycloakSession testSession) {

        List<RoleModel> result = getModelResult(testSession, REALM_NAME, null, null, null);
        assertThat(result, hasSize(rolesSubset().size()));
        assertIndexValues(result, contains(0, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 3, 4, 5, 6, 7, 8, 9));

        result = withRealm(testSession, REALM_NAME, (session, realm) -> session.roles()
                .getRoleById(realm, MAIN_ROLE_ID)
                .getCompositesStream()
                .collect(Collectors.toList()));
        assertThat(result, hasSize(rolesSubset().size()));
        assertIndexValues(
                result, containsInAnyOrder(0, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 3, 4, 5, 6, 7, 8, 9));
    }

    @TestOnServer
    public void testRolesWithIdsSearchQueries(KeycloakSession testSession) {

        testRolesWithIdsSearchQueries((search, first, max) -> getResult(testSession, REALM_NAME, search, first, max));
    }

    @TestOnServer
    public void testRolesWithIdsSearchQueriesHandleUnknownIdsWithoutNpe(KeycloakSession testSession) {

        List<RoleModel> result = withRealm(testSession, REALM_NAME, (session, realm) -> session.roles()
                .getRolesStream(
                        realm,
                        IntStream.range(0, 10).boxed().map(i -> UUID.randomUUID()
                                .toString()),
                        "role-composite",
                        null,
                        null)
                .collect(Collectors.toList()));

        assertThat(result, is(empty()));
    }

    @TestOnServer
    public void testCompositeRolesSearchQueries(KeycloakSession testSession) {

        testRolesWithIdsSearchQueries(
                (search, first, max) -> getModelResult(testSession, REALM_NAME, search, first, max));
    }

    public static void testRolesWithIdsSearchQueries(GetResult resultProvider) {
        // should return all roles from the subset
        List<RoleModel> result = resultProvider.getResult("", null, null);
        assertThat(result, hasSize(rolesSubset().size()));
        assertIndexValues(result, contains(0, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 3, 4, 5, 6, 7, 8, 9));

        // test string that all contains
        result = resultProvider.getResult("role-composite", null, null);
        assertThat(result, hasSize(rolesSubset().size()));
        assertIndexValues(result, contains(0, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 3, 4, 5, 6, 7, 8, 9));

        // test string that some contain
        result = resultProvider.getResult("role-composite-1", null, null);
        assertThat(result, hasSize(11));
        assertIndexValues(result, contains(1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19));

        // test string none contain
        result = resultProvider.getResult("nonsense-string", null, null);
        assertThat(result, is(empty()));
    }

    @TestOnServer
    public void testRolesWithIdsPaginationQueries(KeycloakSession testSession) {

        testRolesWithIdsPaginationQueries(
                (search, first, max) -> getResult(testSession, REALM_NAME, search, first, max));
    }

    @TestOnServer
    public void testCompositeRolesPaginationQueries(KeycloakSession testSession) {

        testRolesWithIdsPaginationQueries(
                (search, first, max) -> getModelResult(testSession, REALM_NAME, search, first, max));
    }

    public static void testRolesWithIdsPaginationQueries(GetResult resultProvider) {
        // should return all roles from the subset
        List<RoleModel> result =
                resultProvider.getResult(null, null, rolesSubset().size());
        assertThat(result, hasSize(rolesSubset().size()));
        assertIndexValues(result, contains(0, 1, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 2, 3, 4, 5, 6, 7, 8, 9));

        // test max parameter
        result = resultProvider.getResult(null, null, 5);
        assertThat(result, hasSize(5));
        assertIndexValues(result, contains(0, 1, 10, 11, 12));

        // test first parameter
        result = resultProvider.getResult(null, 10, null);
        assertThat(result, hasSize(rolesSubset().size() - 10));
        assertIndexValues(result, contains(18, 19, 2, 3, 4, 5, 6, 7, 8, 9));

        // test first and max
        result = resultProvider.getResult(null, 10, 5);
        assertThat(result, hasSize(5));
        assertIndexValues(result, contains(18, 19, 2, 3, 4));
    }

    @TestOnServer
    public void testRolesWithIdsPaginationSearchQueries(KeycloakSession testSession) {

        testRolesWithIdsPaginationSearchQueries(
                (search, first, max) -> getResult(testSession, REALM_NAME, search, first, max));
    }

    @TestOnServer
    public void testCompositeRolesPaginationSearchQueries(KeycloakSession testSession) {

        testRolesWithIdsPaginationSearchQueries(
                (search, first, max) -> getModelResult(testSession, REALM_NAME, search, first, max));
    }

    @TestOnServer
    public void testSearchRolesByDescription(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            List<RoleModel> realmRolesByDescription = session.roles()
                    .searchForRolesStream(realm, "This is a", null, null)
                    .collect(Collectors.toList());
            assertThat(realmRolesByDescription, hasSize(10));
            realmRolesByDescription = session.roles()
                    .searchForRolesStream(realm, "realm role.", 5, null)
                    .collect(Collectors.toList());
            assertThat(realmRolesByDescription, hasSize(5));
            realmRolesByDescription = session.roles()
                    .searchForRolesStream(realm, "DESCRIPTION FOR", 3, 9)
                    .collect(Collectors.toList());
            assertThat(realmRolesByDescription, hasSize(7));

            ClientModel client = session.clients().getClientByClientId(realm, "client-with-roles");

            List<RoleModel> clientRolesByDescription = session.roles()
                    .searchForClientRolesStream(client, "this is a", 0, 10)
                    .collect(Collectors.toList());
            assertThat(clientRolesByDescription, hasSize(10));

            clientRolesByDescription = session.roles()
                    .searchForClientRolesStream(client, "role-composite-13 client role", null, null)
                    .collect(Collectors.toList());
            assertThat(clientRolesByDescription, hasSize(1));
            assertThat(
                    clientRolesByDescription.get(0).getDescription(),
                    is("This is a description for main-role-composite-13 client role."));

            return null;
        });
    }

    @TestOnServer
    public void testCompositeRolesUpdateOnChildRoleRemoval(KeycloakSession testSession) {

        final AtomicReference<String> parentRealmRoleId = new AtomicReference<>();
        final AtomicReference<String> parentClientRoleId = new AtomicReference<>();

        final AtomicReference<String> childRealmRoleId = new AtomicReference<>();
        final AtomicReference<String> childClientRoleId = new AtomicReference<>();

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            // Create realm role
            RoleModel parentRealmRole = session.roles().addRealmRole(realm, "parentRealmRole");
            parentRealmRoleId.set(parentRealmRole.getId());

            // Create client role
            ClientModel client = session.clients().addClient(realm, "clientWithRole");

            RoleModel parentClientRole = session.roles().addClientRole(client, "parentClientRole");
            parentClientRoleId.set(parentClientRole.getId());

            // Create realm child role
            RoleModel childRealmRole = session.roles().addRealmRole(realm, "childRealmRole");
            childRealmRoleId.set(childRealmRole.getId());

            RoleModel childClientRole = session.roles().addClientRole(client, "childClientRole");
            childClientRoleId.set(childClientRole.getId());

            // Add composites
            parentRealmRole.addCompositeRole(childRealmRole);
            parentRealmRole.addCompositeRole(childClientRole);

            parentClientRole.addCompositeRole(childRealmRole);
            parentClientRole.addCompositeRole(childClientRole);
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RoleModel parentRealmRole = session.roles().getRoleById(realm, parentRealmRoleId.get());
            RoleModel parentClientRole = session.roles().getRoleById(realm, parentClientRoleId.get());
            assertThat(parentRealmRole.getCompositesStream().collect(Collectors.toSet()), hasSize(2));
            assertThat(parentClientRole.getCompositesStream().collect(Collectors.toSet()), hasSize(2));

            session.roles().removeRole(session.roles().getRoleById(realm, childRealmRoleId.get()));
            session.roles().removeRole(session.roles().getRoleById(realm, childClientRoleId.get()));
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RoleModel parentRealmRole = session.roles().getRoleById(realm, parentRealmRoleId.get());
            RoleModel parentClientRole = session.roles().getRoleById(realm, parentClientRoleId.get());
            assertThat(parentRealmRole.getCompositesStream().collect(Collectors.toSet()), empty());
            assertThat(parentClientRole.getCompositesStream().collect(Collectors.toSet()), empty());
            return null;
        });
    }

    public static void testRolesWithIdsPaginationSearchQueries(GetResult resultProvider) {
        // test all parameters together
        List<RoleModel> result = resultProvider.getResult("1", 4, 3);
        assertThat(result, hasSize(3));
        assertIndexValues(result, contains(13, 14, 15));
    }

    private static void assertIndexValues(
            List<RoleModel> roles, Matcher<? super Collection<? extends Integer>> matcher) {
        assertThat(
                roles.stream()
                        .map(RoleModel::getName)
                        .map(s -> s.substring("main-role-composite-".length()))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList()),
                matcher);
    }

    public static class RoleModelRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.clients(ClientBuilder.create(CLIENT_WITH_ROLES));
            RoleBuilder mainRole = RoleBuilder.create("main-role").id(MAIN_ROLE_ID);
            IntStream.range(0, 10).forEach(i -> {
                realm.realmRoles(role(RoleBuilder.create(roleName(i)), i, " realm role."));
                mainRole.realmComposite(roleName(i));
            });
            IntStream.range(10, 20).forEach(i -> {
                realm.clientRoles(CLIENT_WITH_ROLES, role(RoleBuilder.create(roleName(i)), i, " client role."));
                mainRole.clientComposite(CLIENT_WITH_ROLES, roleName(i));
            });
            realm.realmRoles(mainRole);
            IntStream.range(0, 20).forEach(i -> realm.realmRoles(RoleBuilder.create("non-returned-role-" + i)));
            return realm;
        }

        private static RoleBuilder role(RoleBuilder role, int index, String descriptionSuffix) {
            String name = roleName(index);
            return role.id(roleId(index)).description("This is a description for " + name + descriptionSuffix);
        }

        private static String roleName(int index) {
            return "main-role-composite-" + index;
        }

        private static String roleId(int index) {
            return "main-role-composite-" + index + "-id";
        }
    }
}
