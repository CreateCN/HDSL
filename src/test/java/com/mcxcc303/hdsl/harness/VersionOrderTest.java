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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order two versions sort in.
 * <p>
 * The comparison is the shared semver rule: the numbers first, a release over its prereleases,
 * and prerelease identifiers compared as numbers or names. The launcher sorts its version lists
 * with this, and marks the releases the lockstep package never had a twin for.
 */
class VersionOrderTest {

	@Test
	void aNewerNumberSortsAfterAnOlderOne() {
		assertTrue(VersionOrder.compareVersions("1.2.4", "1.2.3") > 0);
		assertTrue(VersionOrder.compareVersions("0.1.7-rc.1", "0.1.6") > 0,
		           "the numbers decide before the pre-release part does");
	}

	@Test
	void aReleaseOutranksItsOwnPreReleases() {
		assertTrue(VersionOrder.compareVersions("1.0.0", "1.0.0-rc.1") > 0);
		assertTrue(VersionOrder.compareVersions("0.1.5-alpha.2", "0.1.5") < 0);
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
