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
package com.mcxcc303.hdsl.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the semver rules the version lists and the toolchain gates share
 * <p>
 * The parser has to stay lenient where npm reads a range operand, and strict where a toolchain
 * version has to be known before it is trusted.
 */
class SemverTest {

	@Test
	void aVersionIsReadThroughItsPrefixBuildAndPrerelease() {
		assertEquals(new Semver(10, 9, 0, null), Semver.parse("10.9.0"));
		assertEquals(new Semver(10, 9, 0, null), Semver.parse("10.9"));
		assertEquals(new Semver(10, 9, 0, null), Semver.parse("v10.9.0"));
		assertEquals(new Semver(10, 9, 0, null), Semver.parse("10.9.0+build"));
		assertEquals(new Semver(10, 9, 0, List.of("rc", "1")), Semver.parse("10.9.0-rc.1"));
	}

	@Test
	void anUnreadableVersionIsRejectedByTheStrictParser() {
		assertNull(Semver.parse("unknown"));
		assertNull(Semver.parse(""));
		assertNull(Semver.parse("10.x"));
		assertNull(Semver.parse("10.9.0.1"));
	}

	@Test
	void aRangeOperandMayLeaveNumbersOut() {
		assertEquals(new Semver(1, 0, 0, null), Semver.parsePartial("1.x"));
		assertEquals(new Semver(1, 0, 0, null), Semver.parsePartial("1"));
	}

	@Test
	void prereleaseIdentifiersCompareByTheirOwnRules() {
		assertTrue(Semver.compare("1.0.0-alpha.10", "1.0.0-alpha.2") > 0);
		assertTrue(Semver.compare("1.0.0-alpha", "1.0.0-alpha.1") < 0);
		assertTrue(Semver.compare("1.0.0-1", "1.0.0-alpha") < 0);
	}

	@Test
	void buildMetadataAndALeadingVAreDecoration() {
		assertEquals(0, Semver.compare("1.0.0+build", "1.0.0"));
		assertEquals(0, Semver.compare("v1.0.0", "1.0.0"));
	}
}
