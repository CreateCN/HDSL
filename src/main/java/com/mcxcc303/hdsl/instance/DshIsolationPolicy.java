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
package com.mcxcc303.hdsl.instance;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Whether a new instance keeps its own home.
///
/// New instances keep their writable state to themselves by default. Sharing remains an explicit
/// choice, either for every new instance or only for installations without their own plugins.
@NotNullByDefault
public enum DshIsolationPolicy {
	/// Every new instance keeps its own home.
	ALWAYS("always"),

	/// A new instance keeps its own home when it is given plugins of its own.
	WITH_PLUGINS("modded"),

	/// New instances share one home unless somebody says otherwise.
	NEVER("never");

	/// The value the setting is stored as.
	private final String id;

	/// Creates a policy.
	///
	/// @param id the stored value
	DshIsolationPolicy(String id) {
		this.id = id;
	}

	/// Reads a stored value.
	///
	/// @param value the stored value, or `null`
	/// @return the policy, or [DshIsolationPolicy#ALWAYS] when it is not one
	public static DshIsolationPolicy of(@Nullable String value) {
		if (value != null) {
			for (DshIsolationPolicy policy : values()) {
				if (policy.id.equalsIgnoreCase(value.trim())) {
					return policy;
				}
			}
		}
		return ALWAYS;
	}

	/// Returns the value the setting is stored as.
	///
	/// @return the id
	public String id() {
		return id;
	}

	/// Decides how a new instance keeps its state.
	///
	/// @param carriesItsOwn whether the installation brings plugins or presets of its own
	/// @param shared        the mode used when the instance does not keep a home of its own
	/// @return the mode to create the instance with
	public DshHomeMode homeMode(boolean carriesItsOwn, DshHomeMode shared) {
		return switch (this) {
			case ALWAYS -> DshHomeMode.ISOLATED;
			case NEVER -> shared;
			case WITH_PLUGINS -> carriesItsOwn ? DshHomeMode.ISOLATED : shared;
		};
	}
}
