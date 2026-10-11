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
package com.mcxcc303.hdsl.plugin;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// The markets the launcher can read plugins from.
///
/// Each one is a market of its own rather than another address of the same document: what is asked
/// for, and what comes back, is that market's business. What the launcher needs from any of them is
/// the same thing — a list of plugins — and every market says it in its own shape, which is why
/// these are values with a reader each rather than a list of addresses of one kind of document.
@NotNullByDefault
public enum MarketSource {
	/// The community's own market: one `plugins.json`, published to its host and to npm.
	///
	/// The npm package carries the same document, and reading it goes through whichever registry the
	/// machine's `npm` is already configured for — which is the one network path this launcher can
	/// be sure of, since it installs everything else that way. The host is GitHub Pages, so a network
	/// that cannot reach GitHub can still reach an npm mirror; the package is therefore read when
	/// the host does not answer, rather than as well as it.
	AWESOME_DSH_PLUGIN("awesome-dsh-plugin",
	                   "Awesome DSH Plugin",
	                   "https://awesome-dsh-plugin.com/plugins.json",
	                   "dsh-plugin-catalog");

	/// The name this market is stored under.
	private final String id;

	/// What people see in the list.
	private final String label;

	/// Where this market's document is read from, in the order it is tried.
	private final List<String> addresses;

	/// What is read when none of the addresses answers: an npm package carrying the same document,
	/// or `null` for a market with nowhere else to go.
	private final @Nullable String fallback;

	MarketSource(String id, String label, String... addresses) {
		this.id = id;
		this.label = label;
		this.addresses = List.of(addresses);
		this.fallback = null;
	}

	MarketSource(String id, String label, String address, String fallback) {
		this.id = id;
		this.label = label;
		this.addresses = List.of(address);
		this.fallback = fallback;
	}

	/// Returns the market a stored id names.
	///
	/// A setting written by a newer launcher names a market this one does not have, and reading it
	/// then falls back to the community's rather than to nothing: a plugin list somebody can use is
	/// a better answer than an empty page.
	///
	/// @param id the stored id, or `null`
	/// @return the market
	public static MarketSource of(@Nullable String id) {
		if (id != null) {
			for (MarketSource source : values()) {
				if (source.id.equals(id.trim())) {
					return source;
				}
			}
		}
		return AWESOME_DSH_PLUGIN;
	}

	/// Returns the name this market is stored under.
	///
	/// @return the id
	public String id() {
		return id;
	}

	/// Returns what the market is called.
	///
	/// @return the label
	public String label() {
		return label;
	}

	/// Returns where this market's document is read from.
	///
	/// @return the addresses, in the order they are tried
	public List<String> addresses() {
		return addresses;
	}

	/// Returns what is read when none of the addresses answers.
	///
	/// @return the fallback, or `null` when this market has none
	public @Nullable String fallback() {
		return fallback;
	}
}