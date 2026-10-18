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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/**
 * A version parsed into the parts it is compared by.
 *
 * <p>Build metadata is discarded and a leading {@code v} is decoration. The comparison is
 * semantic-version precedence: the three numbers first, then a release over its prereleases, then
 * the prerelease identifiers, numbers before names.
 *
 * @param major      the major component
 * @param minor      the minor component
 * @param patch      the patch component
 * @param prerelease the prerelease identifiers, or null when the version carries none
 */
@NotNullByDefault
public record Semver(int major, int minor, int patch, @Nullable List<String> prerelease)
		implements Comparable<Semver> {

	public Semver {
		prerelease = prerelease == null ? null : List.copyOf(prerelease);
	}

	/**
	 * Parses a complete version.
	 *
	 * <p>A missing minor or patch is zero; a present component that is not a number, or a fourth
	 * component, makes the version unreadable.
	 *
	 * @param version the version, optionally prefixed with {@code v}
	 * @return the parsed version, or {@code null} when it is not one
	 */
	public static @Nullable Semver parse(String version) {
		String clean = compared(version);
		String[] halves = clean.split("-", 2);
		String[] written = halves[0].split("\\.", -1);
		if (written.length > 3) {
			return null;
		}

		int[] numbers = new int[3];
		for (int i = 0; i < written.length; i++) {
			String component = written[i].trim();
			if (!isNumber(component)) {
				return null;
			}
			try {
				numbers[i] = Integer.parseInt(component);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return new Semver(numbers[0], numbers[1], numbers[2], prereleaseOf(halves));
	}

	/**
	 * Parses a version as npm reads a range's operand.
	 *
	 * <p>A component that is missing or not a number counts as zero, so {@code 1.x} and {@code 1}
	 * compare as {@code 1.0.0}; components past the third are ignored. Use {@link #parse(String)}
	 * when an unreadable version must be rejected.
	 *
	 * @param version the version, optionally prefixed with {@code v}
	 * @return the parsed version
	 */
	public static Semver parsePartial(String version) {
		String clean = compared(version);
		String[] halves = clean.split("-", 2);
		String[] written = halves[0].split("\\.", -1);
		int[] numbers = new int[3];
		for (int i = 0; i < 3; i++) {
			numbers[i] = i < written.length ? numeric(written[i]) : 0;
		}
		return new Semver(numbers[0], numbers[1], numbers[2], prereleaseOf(halves));
	}

	/**
	 * Compares two version strings as {@link #parsePartial(String)} reads them.
	 *
	 * @param left  the first version
	 * @param right the second version
	 * @return negative, zero or positive as {@code left} is below, equal to or above {@code right}
	 */
	public static int compare(String left, String right) {
		return parsePartial(left).compareTo(parsePartial(right));
	}

	/**
	 * Returns the three numeric components.
	 *
	 * @return major, minor and patch
	 */
	public int[] numbers() {
		return new int[]{major, minor, patch};
	}

	@Override
	public int compareTo(Semver other) {
		if (major != other.major) {
			return Integer.compare(major, other.major);
		}
		if (minor != other.minor) {
			return Integer.compare(minor, other.minor);
		}
		if (patch != other.patch) {
			return Integer.compare(patch, other.patch);
		}

		List<String> mine = prerelease;
		List<String> theirs = other.prerelease;
		if (mine == null || theirs == null) {
			return mine == theirs ? 0 : mine == null ? 1 : -1;
		}

		int length = Math.max(mine.size(), theirs.size());
		for (int i = 0; i < length; i++) {
			if (i == mine.size()) {
				return -1;
			}
			if (i == theirs.size()) {
				return 1;
			}
			int order = compareIdentifiers(mine.get(i), theirs.get(i));
			if (order != 0) {
				return order;
			}
		}
		return 0;
	}

	private static @Nullable List<String> prereleaseOf(String[] halves) {
		return halves.length > 1 && !halves[1].isEmpty() ? List.of(halves[1].split("\\.", -1)) : null;
	}

	private static String compared(String version) {
		String clean = version.trim();
		if (clean.startsWith("v") || clean.startsWith("V")) {
			clean = clean.substring(1);
		}
		int build = clean.indexOf('+');
		return build < 0 ? clean : clean.substring(0, build);
	}

	private static int numeric(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static boolean isNumber(String identifier) {
		if (identifier.isEmpty()) {
			return false;
		}
		for (int i = 0; i < identifier.length(); i++) {
			char c = identifier.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return true;
	}

	private static int compareIdentifiers(String left, String right) {
		boolean leftNumber = isNumber(left);
		boolean rightNumber = isNumber(right);
		if (leftNumber && rightNumber) {
			return new BigInteger(left).compareTo(new BigInteger(right));
		}
		if (leftNumber != rightNumber) {
			return leftNumber ? -1 : 1;
		}
		return left.compareTo(right);
	}
}
