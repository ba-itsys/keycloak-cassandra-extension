/*
 * Copyright 2023 IT-Systemhaus der Bundesagentur fuer Arbeit
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
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.empty;

import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.cassandra.CassandraKeycloakServerConfig;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.keycloak.models.*;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.testframework.annotations.InjectRealm;
import org.keycloak.testframework.annotations.KeycloakIntegrationTest;
import org.keycloak.testframework.injection.LifeCycle;
import org.keycloak.testframework.realm.ManagedRealm;
import org.keycloak.testframework.realm.RealmBuilder;
import org.keycloak.testframework.realm.RealmConfig;
import org.keycloak.testframework.realm.UserBuilder;
import org.keycloak.testframework.remote.annotations.TestOnServer;

@KeycloakIntegrationTest(config = CassandraKeycloakServerConfig.class)
public class GroupModelTest extends CassandraModelTest {
    private static final String REALM_NAME = "group-model";
    private static final String FIRST_GROUP_ID = "FIRST_GROUP_ID";
    private static final String SECOND_GROUP_ID = "SECOND_GROUP_ID";
    private static final String THIRD_GROUP_ID = "THIRD_GROUP_ID";

    @InjectRealm(ref = REALM_NAME, lifecycle = LifeCycle.METHOD, config = GroupModelRealmConfig.class)
    ManagedRealm managedRealm;

    private static final String OLD_VALUE = "oldValue";
    private static final String NEW_VALUE = "newValue";

    @TestOnServer
    public void testGroupUniqueness(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);

            GroupProvider groupProvider = session.groups();
            Assert.assertThrows(ModelDuplicateException.class, () -> groupProvider.createGroup(realm, "firstGroup"));

            GroupModel subGroup = session.groups().createGroup(realm, "subGroup", secondGroup);
            Assert.assertThrows(
                    ModelDuplicateException.class, () -> groupProvider.createGroup(realm, "subGroup", secondGroup));

            return null;
        });
    }

    @TestOnServer
    public void testBasicCreateRemoveGroup(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);

            GroupModel subGroup = session.groups().createGroup(realm, "firstGroup", secondGroup);

            Assert.assertEquals(4, session.groups().getGroupsCount(realm, false), 0);
            Assert.assertEquals(3, session.groups().getGroupsCount(realm, true), 0);

            session.groups().removeGroup(realm, subGroup);
            Assert.assertEquals(3, session.groups().getGroupsCount(realm, false), 0);

            GroupModel fourthGroup = session.groups().createGroup(realm, "fourthGroupId", "fourthGroup");
            Assert.assertEquals(
                    "fourthGroup",
                    session.groups().getGroupById(realm, "fourthGroupId").getName());

            Assert.assertFalse(session.groups().removeGroup(realm, null));

            return null;
        });
    }

    @TestOnServer
    public void testBasicGroupModel(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            firstGroup.setName("veryFirstGroup");

            firstGroup.setSingleAttribute("key1", "value1");
            firstGroup.setAttribute("key2", Arrays.asList("value21", "value22", "value23"));

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            Assert.assertEquals("veryFirstGroup", firstGroup.getName());

            Assert.assertEquals(2, firstGroup.getAttributes().size());
            Assert.assertEquals("veryFirstGroup", firstGroup.getName());
            Assert.assertTrue(firstGroup.getAttributeStream("key2").allMatch(value -> value.contains("value2")));
            Assert.assertEquals("value21", firstGroup.getFirstAttribute("key2"));

            firstGroup.removeAttribute("key1");
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            Assert.assertEquals(1, firstGroup.getAttributes().size());
            return null;
        });
    }

    @TestOnServer
    public void testAddRemoveGroupChild(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);

            firstGroup.addChild(secondGroup);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);

            Assert.assertEquals(1, firstGroup.getSubGroupsStream().count());
            Assert.assertEquals(FIRST_GROUP_ID, secondGroup.getParentId());

            firstGroup.removeChild(secondGroup);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);

            Assert.assertEquals(0, firstGroup.getSubGroupsStream().count());

            return null;
        });
    }

    @TestOnServer
    public void testGroupRoleMapping(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);

            ClientModel client = session.clients().addClient(realm, "client-id");

            RoleModel realmRole1 = session.roles().addRealmRole(realm, "realmRole1");
            RoleModel realmRole2 = session.roles().addRealmRole(realm, "realmRole2");
            RoleModel realmRole3 = session.roles().addRealmRole(realm, "realmRole3");
            realmRole1.addCompositeRole(realmRole2);

            RoleModel clientRole1 = session.roles().addClientRole(client, "clientRole1");
            RoleModel clientRole2 = session.roles().addClientRole(client, "clientRole2");
            clientRole1.addCompositeRole(clientRole2);

            firstGroup.grantRole(realmRole1);
            firstGroup.grantRole(realmRole3);

            secondGroup.grantRole(clientRole1);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel firstGroup = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            ClientModel client = session.clients().getClientByClientId(realm, "client-id");

            Assert.assertEquals(2, firstGroup.getRealmRoleMappingsStream().count());
            Assert.assertEquals(
                    0, firstGroup.getClientRoleMappingsStream(client).count());
            Assert.assertEquals(
                    1, secondGroup.getClientRoleMappingsStream(client).count());

            Assert.assertTrue(firstGroup.hasRole(session.roles().getRealmRole(realm, "realmRole2")));
            Assert.assertFalse(secondGroup.hasRole(session.roles().getRealmRole(realm, "realmRole2")));

            Assert.assertTrue(secondGroup.hasRole(session.roles().getClientRole(client, "clientRole2")));
            Assert.assertFalse(firstGroup.hasRole(session.roles().getClientRole(client, "clientRole2")));

            Assert.assertTrue(firstGroup.hasDirectRole(session.roles().getRealmRole(realm, "realmRole3")));
            Assert.assertFalse(firstGroup.hasDirectRole(session.roles().getRealmRole(realm, "realmRole2")));

            Assert.assertTrue(secondGroup.hasDirectRole(session.roles().getClientRole(client, "clientRole1")));
            Assert.assertFalse(secondGroup.hasDirectRole(session.roles().getClientRole(client, "clientRole2")));

            return null;
        });
    }

    @TestOnServer
    public void testSearchForGroupByNameStream(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertEquals(
                    1,
                    session.groups()
                            .searchForGroupByNameStream(realm, "firstGroup", true, null, null)
                            .count());
            Assert.assertEquals(
                    0,
                    session.groups()
                            .searchForGroupByNameStream(realm, "firstgroup", true, null, null)
                            .count());
            Assert.assertEquals(
                    1,
                    session.groups()
                            .searchForGroupByNameStream(realm, "firstgroup", false, null, null)
                            .count());

            Assert.assertEquals(
                    3,
                    session.groups()
                            .searchForGroupByNameStream(realm, "Gr", false, null, null)
                            .count());
            Assert.assertEquals(
                    0,
                    session.groups()
                            .searchForGroupByNameStream(realm, "Gr", true, null, null)
                            .count());

            Assert.assertEquals(
                    2,
                    session.groups()
                            .searchForGroupByNameStream(realm, "Gr", false, 1, -1)
                            .count());
            Assert.assertEquals(
                    2,
                    session.groups()
                            .searchForGroupByNameStream(realm, "Gr", false, -1, 2)
                            .count());
            Assert.assertEquals(
                    1,
                    session.groups()
                            .searchForGroupByNameStream(realm, "Gr", false, 2, 10)
                            .count());

            return null;
        });
    }

    @TestOnServer
    public void testSearchForGroupByNameStreamHandlesNullSearch(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertThrows(IllegalArgumentException.class, () -> session.groups()
                    .searchForGroupByNameStream(realm, null, null, null, null)
                    .count());
            Assert.assertThrows(IllegalArgumentException.class, () -> session.groups()
                    .searchForGroupByNameStream(realm, null, false, null, null)
                    .count());

            return null;
        });
    }

    @TestOnServer
    public void testSearchByNameWithHierarchy(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.groups()
                    .createGroup(
                            realm,
                            "deepGroup",
                            session.groups()
                                    .createGroup(
                                            realm, "subGroup", session.groups().getGroupById(realm, SECOND_GROUP_ID)));
            session.groups().createGroup(realm, "subGroup", session.groups().getGroupById(realm, THIRD_GROUP_ID));

            Assert.assertEquals(
                    1,
                    session.groups()
                            .searchForGroupByNameStream(realm, "deepGroup", true, null, null)
                            .count());
            Assert.assertEquals(
                    2,
                    session.groups()
                            .searchForGroupByNameStream(realm, "subGroup", true, null, null)
                            .count());
            Assert.assertEquals(
                    3,
                    session.groups()
                            .searchForGroupByNameStream(realm, "Group", false, null, null)
                            .count());

            return null;
        });
    }

    @TestOnServer
    public void testGetGroupsStream(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertEquals(3, session.groups().getGroupsStream(realm).count());
            Assert.assertEquals(
                    3,
                    session.groups()
                            .getGroupsStream(
                                    realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID, THIRD_GROUP_ID), "", null, null)
                            .count());

            Assert.assertEquals(
                    2,
                    session.groups()
                            .getGroupsStream(realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID), "Group", null, null)
                            .count());
            Assert.assertEquals(
                    2,
                    session.groups()
                            .getGroupsStream(realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID), "group", null, null)
                            .count());
            Assert.assertEquals(
                    1,
                    session.groups()
                            .getGroupsStream(realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID), "first", null, null)
                            .count());

            Assert.assertEquals(
                    3,
                    session.groups()
                            .getGroupsStream(
                                    realm,
                                    Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID, THIRD_GROUP_ID),
                                    "Group",
                                    null,
                                    null)
                            .count());
            Assert.assertEquals(
                    2,
                    session.groups()
                            .getGroupsStream(
                                    realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID, THIRD_GROUP_ID), "Group", -1, 2)
                            .count());
            Assert.assertEquals(
                    2,
                    session.groups()
                            .getGroupsStream(
                                    realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID, THIRD_GROUP_ID), "Group", 1, -1)
                            .count());
            Assert.assertEquals(
                    1,
                    session.groups()
                            .getGroupsStream(
                                    realm, Stream.of(FIRST_GROUP_ID, SECOND_GROUP_ID, THIRD_GROUP_ID), "Group", 2, 10)
                            .count());

            return null;
        });
    }

    @TestOnServer
    public void testGetGroupsCount(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.groups().createGroup(realm, "subGroup", session.groups().getGroupById(realm, THIRD_GROUP_ID));

            Assert.assertEquals(3, session.groups().getGroupsCount(realm, true), 0);
            Assert.assertEquals(4, session.groups().getGroupsCount(realm, false), 0);

            Assert.assertEquals(3, session.groups().getGroupsCountByNameContaining(realm, "Group"), 0);
            Assert.assertEquals(3, session.groups().getGroupsCountByNameContaining(realm, "group"), 0);
            Assert.assertEquals(1, session.groups().getGroupsCountByNameContaining(realm, "first"), 0);

            return null;
        });
    }

    @TestOnServer
    public void testGetTopLevelGroupsStream(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            session.groups()
                    .moveGroup(
                            realm,
                            session.groups().getGroupById(realm, FIRST_GROUP_ID),
                            session.groups().getGroupById(realm, FIRST_GROUP_ID));
            session.groups()
                    .moveGroup(
                            realm,
                            session.groups().getGroupById(realm, THIRD_GROUP_ID),
                            session.groups().getGroupById(realm, SECOND_GROUP_ID));

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertNull(
                    session.groups().getGroupById(realm, FIRST_GROUP_ID).getParentId());
            Assert.assertEquals(
                    2, session.groups().getTopLevelGroupsStream(realm).count());

            session.groups().addTopLevelGroup(realm, session.groups().getGroupById(realm, THIRD_GROUP_ID));
            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertEquals(
                    3, session.groups().getTopLevelGroupsStream(realm).count());

            Assert.assertEquals(
                    3,
                    session.groups().getTopLevelGroupsStream(realm, null, null).count());
            Assert.assertEquals(
                    2, session.groups().getTopLevelGroupsStream(realm, -1, 2).count());
            Assert.assertEquals(
                    2, session.groups().getTopLevelGroupsStream(realm, 1, -1).count());
            Assert.assertEquals(
                    1, session.groups().getTopLevelGroupsStream(realm, 2, 10).count());

            GroupModel secondGroup = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            GroupModel thirdGroup = session.groups().getGroupById(realm, THIRD_GROUP_ID);

            GroupProvider groupProvider = session.groups();
            groupProvider.moveGroup(realm, thirdGroup, secondGroup);
            GroupModel thirdGroupWithoutParent = groupProvider.createGroup(realm, "thirdGroup");

            Assert.assertThrows(
                    ModelDuplicateException.class,
                    () -> groupProvider.moveGroup(realm, thirdGroupWithoutParent, secondGroup));
            return null;
        });
    }

    @TestOnServer
    public void testGetTopLevelGroupsStreamHandlesNullSearchAndExact(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            Assert.assertThrows(IllegalArgumentException.class, () -> session.groups()
                    .getTopLevelGroupsStream(realm, null, null, null, null)
                    .count());

            return null;
        });
    }

    @TestOnServer
    public void testGetGroupsByRoleStream(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel group2 = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            GroupModel group3 = session.groups().getGroupById(realm, THIRD_GROUP_ID);

            RoleModel role1 = session.roles().addRealmRole(realm, "role1");
            RoleModel role2 = session.roles().addRealmRole(realm, "role2");
            RoleModel role3 = session.roles().addRealmRole(realm, "role3");

            group1.grantRole(role1);

            group2.grantRole(role1);
            group2.grantRole(role2);

            group3.grantRole(role1);
            group3.grantRole(role2);
            group3.grantRole(role3);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel group2 = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            GroupModel group3 = session.groups().getGroupById(realm, THIRD_GROUP_ID);

            RoleModel role1 = session.roles().getRealmRole(realm, "role1");
            RoleModel role2 = session.roles().getRealmRole(realm, "role2");
            RoleModel role3 = session.roles().getRealmRole(realm, "role3");

            List<GroupModel> groups = session.groups()
                    .getGroupsByRoleStream(realm, role1, null, null)
                    .collect(Collectors.toList());
            ;
            Assert.assertThat(groups, hasSize(3));
            Assert.assertThat(groups, containsInAnyOrder(group1, group2, group3));

            groups = session.groups()
                    .getGroupsByRoleStream(realm, role2, null, null)
                    .collect(Collectors.toList());
            ;
            Assert.assertThat(groups, hasSize(2));
            Assert.assertThat(groups, containsInAnyOrder(group2, group3));

            groups = session.groups()
                    .getGroupsByRoleStream(realm, role3, null, null)
                    .collect(Collectors.toList());
            ;
            Assert.assertThat(groups, hasSize(1));
            Assert.assertThat(groups, containsInAnyOrder(group3));

            groups = session.groups().getGroupsByRoleStream(realm, role1, -1, 2).collect(Collectors.toList());
            ;
            Assert.assertEquals(2, groups.size());

            groups = session.groups().getGroupsByRoleStream(realm, role1, 1, -1).collect(Collectors.toList());
            ;
            Assert.assertEquals(2, groups.size());

            groups = session.groups().getGroupsByRoleStream(realm, role1, 2, 10).collect(Collectors.toList());
            ;
            Assert.assertEquals(1, groups.size());

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            RoleModel role1 = session.roles().getRealmRole(realm, "role1");
            session.roles().removeRole(role1);

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel group2 = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            GroupModel group3 = session.groups().getGroupById(realm, THIRD_GROUP_ID);

            Assert.assertEquals(0, group1.getRealmRoleMappingsStream().count());
            Assert.assertEquals(1, group2.getRealmRoleMappingsStream().count());
            Assert.assertEquals(2, group3.getRealmRoleMappingsStream().count());

            return null;
        });
    }

    @TestOnServer
    public void testSearchGroupsByAttributes(KeycloakSession testSession) {

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel group2 = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            GroupModel group3 = session.groups().getGroupById(realm, THIRD_GROUP_ID);

            group1.setSingleAttribute("key1", "value1");
            group1.setSingleAttribute("key2", "value21");

            group2.setSingleAttribute("key1", "value1");
            group2.setSingleAttribute("key2", "value22");

            group3.setSingleAttribute("key2", "value21");

            return null;
        });

        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel group1 = session.groups().getGroupById(realm, FIRST_GROUP_ID);
            GroupModel group2 = session.groups().getGroupById(realm, SECOND_GROUP_ID);
            GroupModel group3 = session.groups().getGroupById(realm, THIRD_GROUP_ID);

            Map<String, String> attributesToSearch = new HashMap<>();

            attributesToSearch.put("key1", "value1");
            List<GroupModel> groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, null, null)
                    .collect(Collectors.toList());
            Assert.assertThat(groups, hasSize(2));
            Assert.assertThat(groups, containsInAnyOrder(group1, group2));

            attributesToSearch.clear();
            attributesToSearch.put("key2", "value21");
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, null, null)
                    .collect(Collectors.toList());
            Assert.assertThat(groups, hasSize(2));
            Assert.assertThat(groups, containsInAnyOrder(group1, group3));

            attributesToSearch.clear();
            attributesToSearch.put("key2", "value22");
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, null, null)
                    .collect(Collectors.toList());
            Assert.assertThat(groups, hasSize(1));
            Assert.assertThat(groups, contains(group2));

            attributesToSearch.clear();
            attributesToSearch.put("key3", "value3");
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, null, null)
                    .collect(Collectors.toList());
            Assert.assertThat(groups, empty());

            attributesToSearch.clear();
            attributesToSearch.put("key1", "value1");
            attributesToSearch.put("key2", "value21");
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, null, null)
                    .collect(Collectors.toList());
            Assert.assertEquals(3, groups.size());
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, -1, 2)
                    .collect(Collectors.toList());
            Assert.assertEquals(2, groups.size());
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, 1, -1)
                    .collect(Collectors.toList());
            Assert.assertEquals(2, groups.size());
            groups = session.groups()
                    .searchGroupsByAttributes(realm, attributesToSearch, 2, 10)
                    .collect(Collectors.toList());
            Assert.assertEquals(1, groups.size());

            return null;
        });
    }

    @TestOnServer
    public void testGroupAttributesSetter(KeycloakSession testSession) {

        String groupId = withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel groupModel = session.groups().createGroup(realm, "my-group");
            groupModel.setSingleAttribute("key", OLD_VALUE);

            return groupModel.getId();
        });
        withRealm(testSession, REALM_NAME, (session, realm) -> {
            GroupModel groupModel = session.groups().getGroupById(realm, groupId);
            assertThat(groupModel.getAttributes().get("key"), contains(OLD_VALUE));

            // Change value to NEW_VALUE
            groupModel.setSingleAttribute("key", NEW_VALUE);

            // Check all getters return the new value
            assertThat(groupModel.getAttributes().get("key"), contains(NEW_VALUE));
            assertThat(groupModel.getFirstAttribute("key"), equalTo(NEW_VALUE));
            assertThat(groupModel.getAttributeStream("key").findFirst().get(), equalTo(NEW_VALUE));

            return null;
        });
    }

    public static class GroupModelRealmConfig implements RealmConfig {
        @Override
        public RealmBuilder configure(RealmBuilder realm) {
            realm.users(
                    UserBuilder.create().username("john"), UserBuilder.create().username("mary"));
            realm.update(rep -> rep.setGroups(List.of(
                    group(FIRST_GROUP_ID, "firstGroup"),
                    group(SECOND_GROUP_ID, "secondGroup"),
                    group(THIRD_GROUP_ID, "thirdGroup"))));
            return realm;
        }

        private static GroupRepresentation group(String id, String name) {
            GroupRepresentation group = new GroupRepresentation();
            group.setId(id);
            group.setName(name);
            return group;
        }
    }
}
