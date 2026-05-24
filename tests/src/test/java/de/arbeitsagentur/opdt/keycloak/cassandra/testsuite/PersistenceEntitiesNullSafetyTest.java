package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite;

import static org.junit.jupiter.api.Assertions.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopes;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.GroupValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.Roles;
import org.junit.jupiter.api.Test;

public class PersistenceEntitiesNullSafetyTest {

    @Test
    public void testGroupsHandlesNullRealmGroupsCollection() {
        Groups groups = Groups.builder().realmId("realm").realmGroups(null).build();

        assertNull(groups.getGroupById("missing"));
        assertFalse(groups.removeRealmGroup("missing"));

        groups.addRealmGroup(GroupValue.builder().id("group-1").name("group-1").build());

        assertNotNull(groups.getGroupById("group-1"));
        assertEquals(1, groups.getRealmGroupsByParentId(null).size());
    }

    @Test
    public void testClientScopesHandlesNullClientScopesCollection() {
        ClientScopes clientScopes =
                ClientScopes.builder().realmId("realm").clientScopes(null).build();

        assertNull(clientScopes.getClientScopeById("missing"));
        assertFalse(clientScopes.removeClientScope("missing"));

        clientScopes.addClientScope(
                ClientScopeValue.builder().id("scope-1").name("scope-1").build());

        assertNotNull(clientScopes.getClientScopeById("scope-1"));
        assertEquals(1, clientScopes.getClientScopesByProtocol(null).size());
    }

    @Test
    public void testRolesHandlesNullCollections() {
        Roles roles = Roles.builder()
                .realmId("realm")
                .realmRoles(null)
                .clientRoles(null)
                .build();

        assertNull(roles.getRoleById("missing"));
        assertFalse(roles.removeRealmRole("missing"));
        assertFalse(roles.removeClientRole("client-1", "missing"));

        roles.addRealmRole(
                RoleValue.builder().id("realm-role-1").name("realm-role-1").build());
        roles.addClientRole(
                "client-1",
                RoleValue.builder().id("client-role-1").name("client-role-1").build());

        assertNotNull(roles.getRoleById("realm-role-1"));
        assertNotNull(roles.getRoleById("client-role-1"));
        assertTrue(roles.removeClientRole("client-1", "client-role-1"));
    }
}
