/*
 * Copyright 2026 IT-Systemhaus der Bundesagentur fuer Arbeit
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
package de.arbeitsagentur.opdt.keycloak.cassandra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.arbeitsagentur.opdt.keycloak.cassandra.CassandraStoreConfig.Area;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Unit coverage for the {@code areas} grammar and the programmatic {@link CassandraStoreConfig#of} path. */
class CassandraStoreConfigTest {

    @AfterEach
    void tearDown() {
        CassandraStoreConfig.reset();
    }

    @Test
    void allShorthandSelectsEveryArea() {
        assertEquals(EnumSet.allOf(Area.class), CassandraStoreConfig.parseAreas("all"));
    }

    @Test
    void cacheShorthandSelectsOnlyDynamicAreas() {
        assertEquals(CassandraStoreConfig.dynamicAreas(), CassandraStoreConfig.parseAreas("cache"));
        CassandraStoreConfig.parseAreas("cache").forEach(a -> assertTrue(a.isDynamic()));
    }

    @Test
    void explicitListIsParsedCaseAndWhitespaceInsensitively() {
        assertEquals(
                EnumSet.of(Area.USER, Area.REALM, Area.USER_SESSION),
                CassandraStoreConfig.parseAreas(" User , realm ,user-session"));
    }

    @Test
    void unknownAreaNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> CassandraStoreConfig.parseAreas("bogus"));
    }

    @Test
    void ofResolvesIsAreaEnabledAgainstTheGivenSet() {
        CassandraStoreConfig.of(EnumSet.of(Area.USER, Area.USER_SESSION));
        assertTrue(CassandraStoreConfig.isAreaEnabled(Area.USER));
        assertTrue(CassandraStoreConfig.isAreaEnabled(Area.USER_SESSION));
        assertFalse(CassandraStoreConfig.isAreaEnabled(Area.REALM));
    }

    @Test
    void emptyAreaSetLeavesEverythingDisabled() {
        CassandraStoreConfig.of(EnumSet.noneOf(Area.class));
        for (Area area : Area.values()) {
            assertFalse(CassandraStoreConfig.isAreaEnabled(area));
        }
    }
}
