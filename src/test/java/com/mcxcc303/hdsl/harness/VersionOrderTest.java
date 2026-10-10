/*
 * HDSL
 * Copyright (C) 2026  HDSL contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.mcxcc303.hdsl.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The order two versions sort in.
///
/// The comparison is deliberately a small numeric one rather than semantic-version precedence, so
/// what it *does* is worth pinning: the numbers first, then a release over its pre-releases, then
/// the name itself.  The launcher sorts its version list with this, and marks the releases the
/// lockstep package never had a twin for.
class VersionOrderTest {

    @Test
    void aNewerNumberSortsAfterAnOlderOne() {
        assertTrue(VersionOrder.compareVersions("1.2.4", "1.2.3") > 0);
        assertTrue(VersionOrder.compareVersions("0.1.7-rc.1", "0.1.6") > 0,
                "the numbers decide before the pre-release part does");
        assertTrue(VersionOrder.compareVersions("0.1.1-rc2", "0.0.1-rc.5") > 0);
    }

    @Test
    void aVersionThatCarriesFewerComponentsSortsFirst() {
        // The numeric parts compare equal, so the spelling decides: "1.2" is not "1.2.0".
        assertTrue(VersionOrder.compareVersions("1.2", "1.2.0") < 0);
        assertTrue(VersionOrder.compareVersions("1.2.0", "1.2") > 0);
    }

    @Test
    void theSameVersionComparesEqual() {
        assertEquals(0, VersionOrder.compareVersions("1.2.3", "1.2.3"));
        assertEquals(0, VersionOrder.compareVersions("1.0.0-rc.1", "1.0.0-rc.1"));
    }

    @Test
    void aReleaseOutranksItsOwnPreReleases() {
        assertTrue(VersionOrder.compareVersions("1.0.0", "1.0.0-rc.1") > 0);
        assertTrue(VersionOrder.compareVersions("1.0.0-rc.1", "1.0.0") < 0);
        assertTrue(VersionOrder.compareVersions("0.1.5-alpha.2", "0.1.5") < 0);
    }

    @Test
    void twoPreReleasesAreOrderedByName() {
        assertTrue(VersionOrder.compareVersions("1.0.0-alpha", "1.0.0-beta") < 0);
        // The pre-release part is compared as text, not split into name and number, so `rc.10`
        // sorts before `rc.2`.  Pinned because it is the kind of thing a reader assumes the
        // other way round; the versions the harness publishes are single-numbered snapshots.
        assertTrue(VersionOrder.compareVersions("1.0.0-rc.2", "1.0.0-rc.10") > 0);
    }

    @Test
    void aLeadingVIsNotStripped() {
        // Documented rather than desired: a caller that has a `v` in front of a version
        // strips it before asking, and this is what happens when one does not — the component
        // reads as zero.
        assertTrue(VersionOrder.compareVersions("v2.0.0", "1.0.0") < 0);
    }

    @Test
    void aVersionWithoutAnythingToCompareAgainstDoesNotPredateIt() {
        assertFalse(VersionOrder.predatesLockstep("0.1.0", null),
                "not knowing the oldest is not the same as being older than it");
    }

    @Test
    void anOlderHarnessPredatesTheLockstepPackage() {
        assertTrue(VersionOrder.predatesLockstep("0.0.1", "0.1.1-rc2"));
        assertFalse(VersionOrder.predatesLockstep("0.2.0", "0.1.1-rc2"));
        assertFalse(VersionOrder.predatesLockstep("0.1.1-rc2", "0.1.1-rc2"),
                "the version the package starts at is not older than it");
    }
}
