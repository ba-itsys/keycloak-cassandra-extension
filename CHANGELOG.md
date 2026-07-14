# Changelog

## [6.0.0](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.7.3...v6.0.0) (2026-07-14)


### ⚠ BREAKING CHANGES

* require Keycloak 26.7.0 with stateless and self-configure the datastore

### Features

* require Keycloak 26.7.0 with stateless and self-configure the datastore ([1c3acb6](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/1c3acb63734253b778e51666f7bbadcf8443f3c7))


### Dependencies

* **deps-dev:** bump com.diffplug.spotless:spotless-maven-plugin ([1ed4e41](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/1ed4e412be04266eb79b5cd363f2941a122b9c65))

## [5.7.3](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.7.2...v5.7.3) (2026-07-02)


### Dependencies

* **deps:** bump keycloak.version from 26.6.3 to 26.6.4 ([93fd163](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/93fd163d8e839162758276351e0d8c132e43455e))
* **deps:** bump org.sonatype.central:central-publishing-maven-plugin ([c37864d](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/c37864db21cd827887be8f44e9ceac3e0d72474c))

## [5.7.2](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.7.1...v5.7.2) (2026-06-25)


### Bug Fixes

* downgrade to java 21, which is used by keycloak itself as well ([78ede25](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/78ede2580310ef0fb04b9af09fad02594792424d))
* update to KC 26.6.3 and update tests accordingly ([a8b2f37](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/a8b2f371df6ac8ed304c3b712ac0c9941f2adf60))


### Dependencies

* **deps-dev:** bump com.diffplug.spotless:spotless-maven-plugin ([766008d](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/766008d1fd0bbd44c144dbf1b076394cbd368bb4))
* **deps:** bump cassandra-java-driver.version from 4.19.2 to 4.19.3 ([2e62673](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/2e6267342ce7e363714e46d81d24334410726962))
* **deps:** bump org.jacoco:jacoco-maven-plugin from 0.8.14 to 0.8.15 ([06a0c16](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/06a0c164a8e815ed2e7a63d117581550b5c4070d))

## [5.7.1](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.7.0...v5.7.1) (2026-06-15)


### Bug Fixes

* resource version must adhere to keycloak format ([f9330fc](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/f9330fc7b8236ce75e5974d7858ffc89e88a170b))


### Dependencies

* **deps-dev:** bump asm.version from 9.10 to 9.10.1 ([1bae9de](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/1bae9de786f212cb5dfb7fc9c40b382ac2eea5b4))
* **deps-dev:** bump com.diffplug.spotless:spotless-maven-plugin ([e587ef1](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/e587ef142a61f5a3dccb581ba40be50a443f1f96))
* **deps-dev:** bump org.apache.maven.plugins:maven-surefire-plugin ([65bb33d](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/65bb33d85fe573419508e5323f3bf8aac2c3847c))

## [5.7.0](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.6.2...v5.7.0) (2026-05-27)


### Features

* migrate to new keycloak testframework ([#289](https://github.com/ba-itsys/keycloak-cassandra-extension/issues/289)) ([2702e87](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/2702e870936033deae665f87c6bd11ec4b7eabea))

## [5.6.2](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.6.1...v5.6.2) (2026-05-26)


### Dependencies

* **deps-dev:** bump com.diffplug.spotless:spotless-maven-plugin ([ae1e9bc](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/ae1e9bcd47cd6fa547ce28d19a6a3bae3b072a36))
* **deps:** bump keycloak.version from 26.6.1 to 26.6.2 ([487f62a](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/487f62a65ed46f7427a27e58d9fb4dfa58c37679))

## [5.6.1](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.6.0...v5.6.1) (2026-05-19)


### Dependencies

* **deps-dev:** bump asm.version from 9.9.1 to 9.10 ([57fa74e](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/57fa74ec686ad5736d448ab21c7519f48c0d48dd))
* **deps-dev:** bump com.diffplug.spotless:spotless-maven-plugin ([f22191f](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/f22191f17d03aada5ea25b74237652a4d18da3f0))
* **deps:** bump org.slf4j:slf4j-nop from 2.0.17 to 2.0.18 ([8c0c1de](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/8c0c1decffdddeb1351171fcdcbac533406850b8))

## [5.6.0](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.5.5...v5.6.0) (2026-05-08)


### Features

* **deps:** bump keycloak.version from 26.5.7 to 26.6.1 ([b0090bc](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/b0090bc42e900305506bc374fe76986339c0bdaf))

## [5.5.5](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.5.4...v5.5.5) (2026-04-24)


### Dependencies

* **deps:** bump org.projectlombok:lombok from 1.18.44 to 1.18.46 ([0c90b4e](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/0c90b4e37bf3906189c3e69058ba73aa3fa16d4f))

## [5.5.4](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.5.3...v5.5.4) (2026-04-23)


### Bug Fixes

* eventual consistency for stale search index entries ([7866155](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/7866155343d8ab2798f1596e2c72f5094dacde18))


### Dependencies

* **deps-dev:** bump org.apache.maven.plugins:maven-jar-plugin ([e84e906](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/e84e906e45b116972d187bba3e577203792a2a11))
* **deps:** bump org.sonatype.central:central-publishing-maven-plugin ([44e0b0d](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/44e0b0d36b97f2cb5a233253c5e8163556d68094))

## [5.5.3](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.5.2...v5.5.3) (2026-04-08)


### Dependencies

* **deps-dev:** bump org.apache.maven.plugins:maven-compiler-plugin ([f763e1f](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/f763e1feae680b471aa9030e9be71b5cebd878d2))
* **deps:** bump cassandra-java-driver.version from 4.19.0 to 4.19.2 ([5e4d632](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/5e4d632c929bd8d630b7b47d752835c22446d7db))

## [5.5.2](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.5.1...v5.5.2) (2026-04-07)


### Bug Fixes

* **npe:** harden null handling in role/group/realm flows ([5cbb6a7](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/5cbb6a7df0943eff970127d62aa15d52e39d101c))


### Dependencies

* **deps-dev:** bump org.apache.maven.plugins:maven-surefire-plugin ([bbf51c1](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/bbf51c16c1358a61238e6e96907a96aef608c5fa))
* **deps:** bump keycloak.version from 26.5.2 to 26.5.7 ([#239](https://github.com/ba-itsys/keycloak-cassandra-extension/issues/239)) ([9c948c4](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/9c948c4c18ca8e805f91fa0deb2670469bf5fa21))

## [5.5.1](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.5.0...v5.5.1) (2026-03-11)


### Bug Fixes

* allow single use object upsert ([1d17d01](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/1d17d01163dd14681258fbc7854af5332a5427d6))

## [5.5.0](https://github.com/ba-itsys/keycloak-cassandra-extension/compare/v5.4.6...v5.5.0) (2026-02-02)


### Features

* **keycloak:** update to 26.5.2 ([#235](https://github.com/ba-itsys/keycloak-cassandra-extension/issues/235)) ([e2e002d](https://github.com/ba-itsys/keycloak-cassandra-extension/commit/e2e002dfc95bc479dd57a7cceac47a0bbaa31d5c))
