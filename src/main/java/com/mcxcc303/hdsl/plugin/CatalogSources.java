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

import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Where the plugin catalogue is read from: the addresses and npm packages somebody configured,
/// and what the launcher reads when nobody has.
///
/// A list rather than one address, because one host serving one JSON document is one point of
/// failure and one list of plugins.  The order is the order of authority: the first source that
/// lists a plugin is the one its description, version and download count come from.
@NotNullByDefault
public final class CatalogSources {
	/// The npm names a plugin entry may name.
	static final Pattern NPM_NAME = Pattern.compile("^(@[a-z0-9-~][a-z0-9-._~]*/)?[a-z0-9-~][a-z0-9-._~]*$");

	/// Returns every place the catalogue is read from, in the order they are tried.
	///
	/// One catalogue is one document on one host, and a host is a single point of
	/// failure: a mirror is what a network that cannot reach the community host
	/// needs, and a second community catalogue is what somebody who wants more
	/// plugins than one list holds needs. So what is configured is a *list*, and
	/// every entry in it that answers contributes its entries — see [#fetch].
	///
	/// An entry is an address (`https://…/plugins.json`) or the name of an npm
	/// package that publishes the same document (`dsh-plugin-catalog`), which is
	/// the classification [#isPackageSource] makes.
	///
	/// The npm package that publishes the community catalogue is not added here.
	/// It is [#fallbackSource], read when nothing above answered: it is the one
	/// network path this launcher can be sure of — it goes through whichever
	/// registry this machine's npm is configured for — and reading it *as well*
	/// as a source that answered would be reading the same document twice.
	///
	/// @return the sources, in the order they are read
	public static List<String> sources() {
		String property = System.getProperty("hdsl.pluginCatalog");
		if (property != null && !property.isBlank()) {
			// A developer asking one run to read somewhere else means somewhere else, not somewhere
			// else as well.
			return List.of(property.trim());
		}
		return marketSource().addresses();
	}

	/// Returns the source read when every configured one has failed.
	///
	/// The community catalogue is published as an npm package as well as on the
	/// community host, and the host is GitHub Pages: a network that cannot reach
	/// GitHub can still reach an npm mirror, which is the network this launcher is
	/// most likely to run on, since it installs everything else through npm. The
	/// package carries the same `plugins.json`, so it is the catalogue rather than
	/// a copy of it.
	///
	/// @return the source to fall back on
	public static @Nullable String fallbackSource() {
		return marketSource().fallback();
	}

	/// Returns the market plugins are read from.
	///
	/// @return the market
	public static MarketSource marketSource() {
		try {
			return MarketSource.of(SettingsManager.settings().marketSourceProperty().get());
		} catch (RuntimeException e) {
			LOG.warning("Could not read the plugin market from the settings", e);
			return MarketSource.AWESOME_DSH_PLUGIN;
		}
	}

	/// Chooses the market plugins are read from.
	///
	/// @param source the market
	public static void setMarketSource(MarketSource source) {
		try {
			LauncherSettings settings = SettingsManager.settings();
			settings.marketSourceProperty().set(source == null ? MarketSource.AWESOME_DSH_PLUGIN.id() : source.id());
			SettingsManager.save();
		} catch (RuntimeException e) {
			LOG.warning("Could not write the plugin market down", e);
		}
	}

	/// Reports whether a source names an npm package rather than an address.
	///
	/// @param source the source
	/// @return whether it is a package for the registry to resolve
	public static boolean isPackageSource(String source) {
		String trimmed = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
		return !trimmed.startsWith("http://") && !trimmed.startsWith("https://")
		       && !trimmed.startsWith("file:");
	}

	/// Reports whether a source can be read at all.
	///
	/// A source is an address or a package name, and anything else is neither:
	/// refusing it here names the entry the user typed rather than failing later
	/// with a message about a URL that was never one.
	///
	/// @param source the source
	/// @return whether it is usable
	public static boolean isUsableSource(@Nullable String source) {
		if (source == null || source.isBlank()) {
			return false;
		}
		String trimmed = source.trim();
		if (isPackageSource(trimmed)) {
			return CatalogSources.NPM_NAME.matcher(trimmed).matches();
		}
		try {
			URI uri = URI.create(trimmed);
			return uri.getScheme() != null && uri.getHost() != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
