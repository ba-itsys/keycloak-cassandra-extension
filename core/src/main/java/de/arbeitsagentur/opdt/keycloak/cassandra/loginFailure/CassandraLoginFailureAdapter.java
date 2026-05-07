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
package de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure;

import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.LoginFailureRepository;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.persistence.entities.LoginFailure;
import de.arbeitsagentur.opdt.keycloak.common.TimeAdapter;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserLoginFailureModel;

@EqualsAndHashCode(of = "entity")
@RequiredArgsConstructor
public class CassandraLoginFailureAdapter implements UserLoginFailureModel {
    private final RealmModel realm;
    private final LoginFailure entity;
    private final LoginFailureRepository loginFailureRepository;

    @Override
    public String getId() {
        return entity.getId();
    }

    @Override
    public String getUserId() {
        return entity.getUserId();
    }

    @Override
    public int getFailedLoginNotBefore() {
        Long failedLoginNotBefore = entity.getFailedLoginNotBefore();
        return failedLoginNotBefore == null
                ? 0
                : TimeAdapter.fromLongWithTimeInSecondsToIntegerWithTimeInSeconds(failedLoginNotBefore);
    }

    @Override
    public void setFailedLoginNotBefore(int notBefore) {
        entity.setFailedLoginNotBefore(TimeAdapter.fromIntegerWithTimeInSecondsToLongWithTimeAsInSeconds(notBefore));
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public int getNumFailures() {
        Integer numFailures = entity.getNumFailures();
        return numFailures == null ? 0 : numFailures;
    }

    @Override
    public void incrementFailures() {
        entity.setNumFailures(getNumFailures() + 1);
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public int getNumTemporaryLockouts() {
        return entity.getNumTemporaryLockouts() == null ? 0 : entity.getNumTemporaryLockouts();
    }

    @Override
    public void incrementTemporaryLockouts() {
        entity.setNumTemporaryLockouts(getNumTemporaryLockouts() + 1);
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public void clearFailures() {
        entity.setFailedLoginNotBefore(null);
        entity.setNumFailures(null);
        entity.setLastFailure(null);
        entity.setLastIpFailure(null);
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public long getLastFailure() {
        Long lastFailure = entity.getLastFailure();
        return lastFailure == null ? 0l : lastFailure;
    }

    @Override
    public void setLastFailure(long lastFailure) {
        entity.setLastFailure(lastFailure);
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public String getLastIPFailure() {
        return entity.getLastIpFailure();
    }

    @Override
    public void setLastIPFailure(String ip) {
        entity.setLastIpFailure(ip);
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public int getNumSecondaryAuthFailures() {
        return entity.getNumSecondaryAuthFailures() == null ? 0 : entity.getNumSecondaryAuthFailures();
    }

    @Override
    public void incrementSecondaryAuthFailures() {
        entity.setNumSecondaryAuthFailures(getNumSecondaryAuthFailures() + 1);
        loginFailureRepository.insertOrUpdate(entity);
    }

    @Override
    public void clearPrimaryAndSecondaryAuthFailures() {
        clearFailures();
        entity.setNumSecondaryAuthFailures(0);
        loginFailureRepository.insertOrUpdate(entity);
    }
}
