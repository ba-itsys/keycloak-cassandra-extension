[![CI](https://github.com/opdt/keycloak-cassandra-extension/workflows/CI/badge.svg)](https://github.com/opdt/keycloak-cassandra-extension/actions?query=workflow%3ACI)
[![Maven Central](https://img.shields.io/maven-central/v/de.arbeitsagentur.opdt/keycloak-cassandra-extension.svg)](https://search.maven.org/artifact/de.arbeitsagentur.opdt/keycloak-cassandra-extension)

# Cassandra storage extension for Keycloak

Uses Apache Cassandra to store and retrieve entities of all storage areas except authorization and events.
Requires Keycloak >= 26.7.0 (older versions may be supported by older versions of this extension).

## How to use

- Download the JAR from Maven Central: https://repo1.maven.org/maven2/de/arbeitsagentur/opdt/keycloak-cassandra-extension/xxx/keycloak-cassandra-extension-xxx.jar
- Put the JAR in Keycloak's providers folder
- Select the cassandra datastore with `--spi-datastore--provider=cassandra` and enable the required preview feature with `--features=stateless`
- Set the necessary configuration options like cassandra endpoints (see the overview below)

> :warning: **Important information:**
Since map storage has been removed from Keycloak, using different storage providers for different storage areas (like users, roles) requires this extension to provide its own `DatastoreProvider`, which is selected as shown above. (The former `KC_COMMUNITY_DATASTORE_CASSANDRA_ENABLED` / `..._CACHE_ENABLED` env vars have been removed in favour of the datastore selection.)

### Storage areas

Which storage areas cassandra serves is controlled by `--spi-datastore--cassandra--areas` (default: `all`):

- `all` — every area; cassandra is the full datastore and no relational database is needed (the default).
- `cache` — only the dynamic areas (`user-session`, `auth-session`, `login-failure`, `single-use-object`, `revoked-token`); a relational database (JPA) serves the rest. This replaces the former "cache mode".
- an explicit comma-separated list of area names: `realm, client, client-scope, role, group, identity-provider, user, user-session, auth-session, login-failure, single-use-object, revoked-token`.

An explicit list also lets cassandra fill the areas another datastore extension does not serve. For example, paired with the [filestore extension](https://github.com/ba-itsys/keycloak-extension-filestore) (which serves the config areas — realms, clients, roles, groups — from files) while cassandra serves users and sessions:

```
--spi-datastore--provider=file
--spi-datastore--cassandra--areas=user,user-session,auth-session,login-failure,single-use-object,revoked-token
```

The filestore extension follows the same activation patterns (datastore selection, `stateless`, automatic cache disables), so the two compose: `file` is the selected datastore and serves its config areas, while its dynamic-area fall-through resolves to cassandra's providers for the areas listed above.

When `cassandra` is the selected datastore, this extension automatically disables the coherency-sensitive realm and authorization caches (unsafe under `stateless`), so you no longer need to set those by hand.

The following parameters might be needed in addition to the configuration options of this extension (see below):

| CLI-Parameter                                                           | Description                                                              |
|-------------------------------------------------------------------------|-------------------------------------------------------------------------|
| --features=stateless                                                    | Required — moves auth/sessions/tokens to the datastore                   |
| --features-disabled=authorization,admin-fine-grained-authz,organization | Disable unsupported features                                            |
| --spi-connections-jpa-legacy-enabled=false                              | Deactivate automatic JPA schema migration (full mode; keep on for `cache`) |

## Configuration options

| CLI-Parameter                                         | Description                                                                             |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------|
| --spi-cassandra-connection-default-port               | Cassandra CQL-Port                                                                      |
| --spi-cassandra-connection-default-contact-points     | Comma-separated list of cassandra node-endpoints                                        |
| --spi-cassandra-connection-default-local-datacenter   | Local datacenter name                                                                   |
| --spi-cassandra-connection-default-username           | Username                                                                                |
| --spi-cassandra-connection-default-password           | Password                                                                                |
| --spi-cassandra-connection-default-keyspace           | Keyspace-name (will be generated by the extension if it does not exist at startup-time) |
| --spi-cassandra-connection-default-replication-factor | Replication factor used if the extension creates the keyspace with simple strategy      |

## Deviations from standard storage providers

### Organizations

Keycloak Organizations are currently *not* supported and have to be turned off.

### User Lookup
Due to Cassandras query first nature, users can only be looked up by specific fields.
`UserProvider::searchForUserStream` supports the following subset of Keycloaks standard search attributes:
- `keycloak.session.realm.users.query.search` for a case insensitive username search
- `keycloak.session.realm.users.query.include_service_account` to include service accounts
- `email` for an email search

`UserProvider::searchForUserByUserAttributeStream` by default iterates all users in the entire database to filter for the requested attribute in-memory.
For efficient searches, attributes can be defined as **indexed attributes** by prefixing their name with **indexed.**, e.g. **indexed.businessKey**

### Conditional updates / optimistic locking
All write-queries are done conditionally via Cassandra Lightweight Transactions. Therefore we store a version column in each of the tables. To be able to use this to get notified if a conflicting change occured after data was read, the entityVersion is exposed via a **readonly attribute readonly.entityVersion**.
In order to pass a version in update operations, one can use the corresponding attribute **internal.entityVersion**.

### Uniqueness across username and password

This extension supports additional checks to prevent setting username to a value that is already as email of another user and setting email to a value used as username.

To enable these checks for a realm, set its attribute `enableCheckForDuplicatesAcrossUsernameAndEmail` to `true` (default when not set: `false`)

### Multi-Tab Refresh Token Rotation
This extension adds support for a grace period when checking for reuses. It can be set via `refreshTokenReuseInterval` realm attribute. Refresh token reuses during this grace period are allowed, which can be useful in case of retries / network problems.

### Unattended service account role assignment
Keycloak creates a service account user for each client with `serviceAccountEnabled` set to `true`.
This extension allows to dynamically assign roles to this service account. This needs to be enabled per role by setting an attribute `unattendedServiceAccountAssignment.enabled` to true.
It can further be restricted by setting the role attribute `unattendedServiceAccountAssignment.clientIdPattern` to a regex matching all client-ids where this role can be dynamically assigned.

To assign a role to a service account, a client attribute `initialServiceAccountRoles` needs to contain a comma-separated list of all role ids to set (which need to have the aforementioned attributes set).

### Private image registries

If you use a private image registry, you can use the .testcontainers file in your user directory to override all
image-registries used by the tests.
See https://www.testcontainers.org/features/image_name_substitution/

Example:

```properties
docker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
hub.image.name.prefix=private-registry/3rd-party/
```

## Local Development

### Prerequisites

- **JDK**: See `pom.xml` for the required Java version.
- **Maven**: Used for building the project.
- **Docker**: Required for running integration tests via Testcontainers. A `docker-compose.yaml` is provided for a local setup including Cassandra.

### Checks (formatting and tests)

Run the following commands locally to ensure code quality:

- **Formatting**: `mvn spotless:apply` (Ensures consistent code style).
- **Verification**: `mvn verify` (Runs the full test suite and builds the project).

### Debugging

Debugging can be enabled via `mvn -Dmaven.surefire.debug verify` (Port 5005).

### Using an external cassandra instance

If you want to use an external cassandra instance on localhost (Port 9042) you can
use `mvn -Dkeycloak.testsuite.start-cassandra-container=false verify`

## Conflicts with the JPA implementation (since 26.1)

The official Keycloak implementation now executes a `dependsOn()` for each provider. Even if it has a lower priority.
This will implicitly initialize the default JPA-Provider. 

To circumvent this problem you may want to define a "NullProvider" which overrides the default JPA-Provider.

## Troubleshooting

### Docker Environment Not Found for Integration Tests

The Integration Tests require a container runtime.
If you encounter the error "Could not find a valid Docker environment", this typically occurs when:

1. Docker is not running
2. You're using Podman instead of Docker
3. The container socket is not accessible

**For Podman users:**
To run the tests with Podman, configure the Docker compatibility socket:

```shell
export DOCKER_HOST=unix://${XDG_RUNTIME_DIR}/podman/podman.sock
mvn clean verify
```
