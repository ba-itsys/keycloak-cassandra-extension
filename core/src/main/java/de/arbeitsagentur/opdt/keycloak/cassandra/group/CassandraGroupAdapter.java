package de.arbeitsagentur.opdt.keycloak.cassandra.group;

import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.GroupValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.Groups;
import java.util.*;
import java.util.stream.Stream;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.*;
import org.keycloak.models.utils.RoleUtils;

@JBossLog
@RequiredArgsConstructor
@EqualsAndHashCode(of = "groupId")
public class CassandraGroupAdapter implements GroupModel {
    private final String groupId;

    protected final KeycloakSession session;
    private final RealmModel realm;
    private final GroupValue groupValue;
    private final Groups groups;
    private final CassandraGroupProvider provider;

    @Override
    public String getId() {
        return groupValue.getId();
    }

    @Override
    public String getName() {
        return groupValue.getName();
    }

    @Override
    public void setName(String name) {
        groupValue.setName(name);
        provider.markChanged(realm.getId());
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        groupValue.getAttributes().remove(name);
        setAttribute(name, Collections.singletonList(value));
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        groupValue.getAttributes().put(name, values);
        provider.markChanged(realm.getId());
    }

    @Override
    public void removeAttribute(String name) {
        groupValue.getAttributes().remove(name);
        provider.markChanged(realm.getId());
    }

    @Override
    public String getFirstAttribute(String name) {
        return getAttributeStream(name).findFirst().orElse(null);
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        return groupValue.getAttributes().getOrDefault(name, Collections.emptyList()).stream();
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return groupValue.getAttributes();
    }

    @Override
    public GroupModel getParent() {
        String parentId = getParentId();
        if (parentId == null) {
            return null;
        }
        return new CassandraGroupAdapter(parentId, session, realm, groups.getGroupById(parentId), groups, provider);
    }

    @Override
    public String getParentId() {
        return groupValue.getParentId();
    }

    @Override
    public Stream<GroupModel> getSubGroupsStream() {
        return groups.getRealmGroupsByParentId(groupValue.getId()).stream()
                .map(group -> new CassandraGroupAdapter(group.getId(), session, realm, group, groups, provider));
    }

    @Override
    public void setParent(GroupModel group) {
        groupValue.setParentId(group == null ? null : group.getId());
        provider.markChanged(realm.getId());
    }

    @Override
    public void addChild(GroupModel subGroup) {
        subGroup.setParent(this);
    }

    @Override
    public void removeChild(GroupModel subGroup) {
        if (getId().equals(subGroup.getParentId())) {
            subGroup.setParent(null);
        }
    }

    @Override
    public OrganizationModel getOrganization() {
        return null;
    }

    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        return getRoleMappingsStream().filter(roleModel -> roleModel.getContainer() instanceof RealmModel);
    }

    @Override
    public Stream<RoleModel> getClientRoleMappingsStream(ClientModel app) {
        final String clientId = app.getId();
        return getRoleMappingsStream()
                .filter(roleModel -> roleModel.getContainer() instanceof ClientModel)
                .filter(roleModel -> roleModel.getContainer().getId().equals(clientId));
    }

    @Override
    public boolean hasDirectRole(RoleModel role) {
        Set<String> grantedRoles = groupValue.getGrantedRoles();
        return grantedRoles != null && grantedRoles.contains(role.getId());
    }

    @Override
    public boolean hasRole(RoleModel role) {
        if (RoleUtils.hasRole(getRoleMappingsStream(), role)) return true;
        GroupModel parent = getParent();
        return parent != null && parent.hasRole(role);
    }

    @Override
    public void grantRole(RoleModel role) {
        groupValue.addGrantedRole(role.getId());
        provider.markChanged(realm.getId());
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        Set<String> grantedRoles = groupValue.getGrantedRoles();

        return grantedRoles == null
                ? Stream.empty()
                : grantedRoles.stream().map(roleId -> session.roles().getRoleById(realm, roleId));
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        groupValue.removeGrantedRole(role.getId());
        provider.markChanged(realm.getId());
    }

    @Override
    public String getDescription() {
        return groupValue.getDescription();
    }

    @Override
    public void setDescription(String description) {
        groupValue.setDescription(description);
        provider.markChanged(realm.getId());
    }
}
