/*
 * Copyright 2022 IT-Systemhaus der Bundesagentur fuer Arbeit
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
package de.arbeitsagentur.opdt.keycloak.cassandra.connection;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import com.datastax.oss.driver.internal.core.type.codec.extras.enums.EnumNameCodec;
import com.datastax.oss.driver.internal.core.type.codec.extras.json.JsonCodec;
import com.google.auto.service.AutoService;
import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraJsonSerialization;
import de.arbeitsagentur.opdt.keycloak.cassandra.CompositeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.ManagedCompositeCassandraRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.AuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.persistence.CassandraAuthSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.cache.L1CacheInterceptor;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.CassandraClientRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.persistence.ClientRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.CassandraClientScopeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.ClientScopeMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.ClientScopeMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.ClientScopeRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.persistence.entities.ClientScopeValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.CassandraDeploymentStateRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.DeploymentStateMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.DeploymentStateMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.deploymentState.persistence.DeploymentStateRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.CassandraGroupRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.GroupMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.GroupMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.GroupRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.persistence.entities.GroupValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.CassandraLoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.CassandraRealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.persistence.RealmRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.CassandraRoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.RoleRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.persistence.entities.RoleValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.CassandraSingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.singleUseObject.persistence.SingleUseObjectRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.CassandraUserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.UserRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.persistence.entities.CredentialValue;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.CassandraUserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionMapper;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionMapperBuilder;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.UserSessionRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.persistence.entities.AuthenticatedClientSessionValue;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.cognitor.cassandra.migration.Database;
import org.cognitor.cassandra.migration.MigrationConfiguration;
import org.cognitor.cassandra.migration.MigrationRepository;
import org.cognitor.cassandra.migration.MigrationTask;
import org.keycloak.Config;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.UserSessionModel;
import org.keycloak.provider.EnvironmentDependentProviderFactory;
import org.keycloak.sessions.CommonClientSessionModel;

@JBossLog
@AutoService(CassandraConnectionProviderFactory.class)
public class DefaultCassandraConnectionProviderFactory
        implements CassandraConnectionProviderFactory<CassandraConnectionProvider>,
                EnvironmentDependentProviderFactory {
    public static final String PROVIDER_ID = "default";
    private CqlSession cqlSession;
    private CompositeRepository repository;

    @Override
    public CassandraConnectionProvider create(KeycloakSession session) {
        return new CassandraConnectionProvider() {
            @Override
            public CqlSession getCqlSession() {
                return cqlSession;
            }

            @Override
            public CompositeRepository getRepository() {
                L1CacheInterceptor intercepted = new L1CacheInterceptor(session, repository);
                return (CompositeRepository) Proxy.newProxyInstance(
                        Thread.currentThread().getContextClassLoader(),
                        new Class[] {CompositeRepository.class},
                        intercepted);
            }

            @Override
            public void close() {}
        };
    }

    @Override
    public void init(Config.Scope scope) {
        // kc.spi.cassandra-connection.default.contactPoints
        // Env: KC_SPI_CASSANDRA_CONNECTION_DEFAULT_CONTACT_POINTS

        String contactPoints = scope.get("contactPoints");
        log.infov("Init CassandraProviderFactory with contactPoints {0}", contactPoints);

        int port = Integer.parseInt(scope.get("port"));
        String localDatacenter = scope.get("localDatacenter");
        String keyspace = scope.get("keyspace");
        String username = scope.get("username");
        String password = scope.get("password");
        int replicationFactor = Integer.parseInt(scope.get("replicationFactor"));

        List<InetSocketAddress> contactPointsList = Arrays.stream(contactPoints.split(","))
                .map(cp -> InetSocketAddress.createUnresolved(cp, port))
                .collect(Collectors.toList());

        if (scope.getBoolean("createKeyspace", true)) {
            log.info("Create keyspace (if not exists)...");
            try (CqlSession createKeyspaceSession = CqlSession.builder()
                    .addContactPoints(contactPointsList)
                    .withAuthCredentials(username, password)
                    .withLocalDatacenter(localDatacenter)
                    .build()) {
                createKeyspaceIfNotExists(createKeyspaceSession, keyspace, replicationFactor);
            }
        } else {
            log.info("Skipping create keyspace, assuming keyspace already exists...");
        }

        if (scope.getBoolean("createSchema", true)) {
            log.info("Create schema...");
            ConsistencyLevel migrationConsistencyLevel =
                    DefaultConsistencyLevel.valueOf(scope.get("migrationConsistencyLevel", "ALL"));
            createDbIfNotExists(
                    contactPointsList, username, password, localDatacenter, keyspace, migrationConsistencyLevel);
        } else {
            log.info("Skipping schema creation...");
        }

        cqlSession = CqlSession.builder()
                .addContactPoints(contactPointsList)
                .withAuthCredentials(username, password)
                .withLocalDatacenter(localDatacenter)
                .withKeyspace(keyspace)
                .addTypeCodecs(new EnumNameCodec<>(UserSessionModel.State.class))
                .addTypeCodecs(new EnumNameCodec<>(GroupModel.Type.class))
                .addTypeCodecs(new EnumNameCodec<>(UserSessionModel.SessionPersistenceState.class))
                .addTypeCodecs(new EnumNameCodec<>(CommonClientSessionModel.ExecutionStatus.class))
                .addTypeCodecs(new JsonCodec<>(RoleValue.class, CassandraJsonSerialization.getMapper()))
                .addTypeCodecs(new JsonCodec<>(GroupValue.class, CassandraJsonSerialization.getMapper()))
                .addTypeCodecs(new JsonCodec<>(CredentialValue.class, CassandraJsonSerialization.getMapper()))
                .addTypeCodecs(
                        new JsonCodec<>(AuthenticatedClientSessionValue.class, CassandraJsonSerialization.getMapper()))
                .addTypeCodecs(new JsonCodec<>(ClientScopeValue.class, CassandraJsonSerialization.getMapper()))
                .build();

        repository = createRepository(cqlSession);
    }

    private void createDbIfNotExists(
            List<InetSocketAddress> contactPointsList,
            String username,
            String password,
            String localDatacenter,
            String keyspace,
            ConsistencyLevel migrationConsistencyLevel) {
        try (CqlSession createKeyspaceSession = CqlSession.builder()
                .addContactPoints(contactPointsList)
                .withAuthCredentials(username, password)
                .withLocalDatacenter(localDatacenter)
                .withKeyspace(keyspace)
                .build()) {
            createTables(createKeyspaceSession, keyspace, migrationConsistencyLevel);
        }
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isSupported(Config.Scope config) {
        return true;
    }

    @Override
    public void close() {
        cqlSession.close();
    }

    private void createKeyspaceIfNotExists(CqlSession cqlSession, String keyspaceName, int replicationFactor) {
        CreateKeyspace createKeyspace = SchemaBuilder.createKeyspace(keyspaceName)
                .ifNotExists()
                .withNetworkTopologyStrategy(
                        Map.of("replication_factor", replicationFactor)); // special dc-name to activate autodiscovery

        cqlSession.execute(createKeyspace.build());
        cqlSession.close();
    }

    private void createTables(CqlSession cqlSession, String keyspace, ConsistencyLevel migrationConsistencyLevel) {
        MigrationConfiguration mgConfig = new MigrationConfiguration().withKeyspaceName(keyspace);
        Database database = new Database(cqlSession, mgConfig).setConsistencyLevel(migrationConsistencyLevel);
        MigrationTask migration = new MigrationTask(database, new MigrationRepository());
        migration.migrate();
    }

    private CompositeRepository createRepository(CqlSession cqlSession) {
        UserMapper userMapper = new UserMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        UserRepository userRepository = new CassandraUserRepository(userMapper.userDao());

        RoleMapper roleMapper = new RoleMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        RoleRepository roleRepository = new CassandraRoleRepository(roleMapper.roleDao());

        GroupMapper groupMapper = new GroupMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        GroupRepository groupRepository = new CassandraGroupRepository(groupMapper.groupDao());

        RealmMapper realmMapper = new RealmMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        RealmRepository realmRepository = new CassandraRealmRepository(realmMapper.realmDao());

        UserSessionMapper userSessionMapper = new UserSessionMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        UserSessionRepository userSessionRepository =
                new CassandraUserSessionRepository(userSessionMapper.userSessionDao());

        AuthSessionMapper authSessionMapper = new AuthSessionMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        AuthSessionRepository authSessionRepository =
                new CassandraAuthSessionRepository(authSessionMapper.authSessionDao());

        LoginFailureMapper loginFailureMapper = new LoginFailureMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        LoginFailureRepository loginFailureRepository =
                new CassandraLoginFailureRepository(loginFailureMapper.loginFailureDao());

        SingleUseObjectMapper singleUseObjectMapper = new SingleUseObjectMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        SingleUseObjectRepository singleUseObjectRepository =
                new CassandraSingleUseObjectRepository(singleUseObjectMapper.singleUseObjectDao());

        DeploymentStateMapper deploymentStateMapper = new DeploymentStateMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        DeploymentStateRepository deploymentStateRepository =
                new CassandraDeploymentStateRepository(deploymentStateMapper.deploymentStateDao());

        ClientMapper clientMapper = new ClientMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        ClientRepository clientRepository = new CassandraClientRepository(clientMapper.clientDao());

        ClientScopeMapper clientScopeMapper = new ClientScopeMapperBuilder(cqlSession)
                .withSchemaValidationEnabled(false)
                .build();
        ClientScopeRepository clientScopeRepository =
                new CassandraClientScopeRepository(clientScopeMapper.clientScopeDao());

        ManagedCompositeCassandraRepository cassandraRepository = new ManagedCompositeCassandraRepository();
        cassandraRepository.setRoleRepository(roleRepository);
        cassandraRepository.setGroupRepository(groupRepository);
        cassandraRepository.setUserRepository(userRepository);
        cassandraRepository.setRealmRepository(realmRepository);
        cassandraRepository.setUserSessionRepository(userSessionRepository);
        cassandraRepository.setAuthSessionRepository(authSessionRepository);
        cassandraRepository.setLoginFailureRepository(loginFailureRepository);
        cassandraRepository.setSingleUseObjectRepository(singleUseObjectRepository);
        cassandraRepository.setDeploymentStateRepository(deploymentStateRepository);
        cassandraRepository.setClientRepository(clientRepository);
        cassandraRepository.setClientScopeRepository(clientScopeRepository);

        return cassandraRepository;
    }
}
