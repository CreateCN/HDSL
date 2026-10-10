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

import com.mcxcc303.hdsl.core.DshException;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
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

    /// Where the catalogue is published.
    public static final String CATALOG_URL = "https://awesome-dsh-plugin.com/plugins.json";

    /// The npm package the same catalogue is also published as.
    ///
    /// The community host is GitHub Pages, and a network that cannot reach GitHub can still reach
    /// an npm mirror — which is exactly the network this launcher is most likely to run on, since
    /// it installs everything else through npm. The package carries the same `plugins.json`, so it
    /// is the catalogue rather than a copy of it, and reading it goes through whichever registry
    /// the machine's `npm` is already configured to use.
    public static final String NPM_CATALOG_PACKAGE = "dsh-plugin-catalog";

    /// The address actually read first, which the `hdsl.pluginCatalog` property may
    /// point at a mirror of the catalogue. The marketplace's own deployment reads
    /// `DSHM_REGISTRY_URL` for the same reason: one host serving one JSON document
    /// is a single point of failure, and a mirror of it should be usable without
    /// rebuilding.
    ///
    /// @return the address to read
    public static String catalogUrl() {
        // Three sources, most specific first: the property is for a developer who wants one
        // run to read somewhere else, the setting is for a network that cannot reach the
        // community host, and the constant is what the ecosystem publishes.
        String property = System.getProperty("hdsl.pluginCatalog");
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        try {
            String setting = org.jackhuang.hmcl.setting.SettingsManager.settings()
                    .pluginCatalogUrlProperty().get();
            if (setting != null && !setting.isBlank()) {
                return setting.trim();
            }
        } catch (RuntimeException e) {
            LOG.warning("Could not read the catalogue address from the settings", e);
        }
        return CATALOG_URL;
    }

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
            // A developer asking one run to read somewhere else means somewhere
            // else, not somewhere else as well.
            return List.of(property.trim());
        }

        List<String> configured = new ArrayList<>();
        try {
            org.jackhuang.hmcl.setting.LauncherSettings settings =
                    org.jackhuang.hmcl.setting.SettingsManager.settings();
            for (String source : settings.pluginCatalogSourcesProperty()) {
                if (source != null && !source.isBlank()) {
                    configured.add(source.trim());
                }
            }
            if (configured.isEmpty()) {
                // The single address this launcher had before sources were a list.
                // Kept working rather than migrated: it is one string, and a
                // settings file that carries it is a settings file somebody is
                // using.
                String legacy = settings.pluginCatalogUrlProperty().get();
                if (legacy != null && !legacy.isBlank()) {
                    configured.add(legacy.trim());
                }
            }
        } catch (RuntimeException e) {
            LOG.warning("Could not read the catalogue sources from the settings", e);
        }
        if (configured.isEmpty()) {
            configured.add(CATALOG_URL);
        }
        return List.copyOf(configured.stream().distinct().toList());
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
    public static String fallbackSource() {
        return NPM_CATALOG_PACKAGE;
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
            java.net.URI uri = java.net.URI.create(trimmed);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /// Returns the sources the user arranged, without the ones the launcher adds.
    ///
    /// What the settings hold, which is what an editor shows: the built-in pair is
    /// added when this is empty, and a list that showed them would be a list whose
    /// entries cannot all be removed.
    ///
    /// @return the configured sources, empty when the launcher's own pair is used
    public static List<String> configuredSources() {
        try {
            return List.copyOf(org.jackhuang.hmcl.setting.SettingsManager.settings()
                    .pluginCatalogSourcesProperty());
        } catch (RuntimeException e) {
            LOG.warning("Could not read the catalogue sources from the settings", e);
            return List.of();
        }
    }

    /// Adds a source, unless it is already one.
    ///
    /// @param source an address or an npm package name
    /// @throws DshException when it is neither
    public static void addSource(String source) throws DshException {
        String trimmed = source == null ? "" : source.trim();
        if (!isUsableSource(trimmed)) {
            throw new DshException("\"" + source + "\" is neither a catalogue address nor a package name"
                    + " (an address looks like https://host/plugins.json, a package like "
                    + NPM_CATALOG_PACKAGE + ")");
        }
        List<String> updated = new ArrayList<>(configuredSources());
        if (updated.stream().anyMatch(existing -> existing.equalsIgnoreCase(trimmed))) {
            throw new DshException(trimmed + " is already a catalogue source");
        }
        updated.add(trimmed);
        setConfiguredSources(updated);
    }

    /// Removes a source.
    ///
    /// Removing the last one goes back to the pair the ecosystem publishes rather
    /// than leaving the launcher with none: a launcher with no source is a
    /// launcher whose plugin page shows nothing and cannot say why, and "stop
    /// using my list" is what removing the last entry means.
    ///
    /// @param source the source to remove
    /// @throws DshException when it is not a source
    public static void removeSource(String source) throws DshException {
        String trimmed = source == null ? "" : source.trim();
        List<String> updated = new ArrayList<>(configuredSources());
        if (!updated.removeIf(existing -> existing.equalsIgnoreCase(trimmed))) {
            throw new DshException(trimmed + " is not a catalogue source");
        }
        if (updated.isEmpty()) {
            resetSources();
            return;
        }
        setConfiguredSources(updated);
    }

    /// Puts the catalogue sources back to the pair the ecosystem publishes.
    public static void resetSources() {
        setConfiguredSources(List.of());
        try {
            org.jackhuang.hmcl.setting.LauncherSettings settings =
                    org.jackhuang.hmcl.setting.SettingsManager.settings();
            settings.pluginCatalogUrlProperty().set("");
            org.jackhuang.hmcl.setting.SettingsManager.save();
        } catch (RuntimeException e) {
            LOG.warning("Could not put the catalogue address back", e);
        }
    }

    /// Writes the configured sources down.
    ///
    /// @param sources the sources
    private static void setConfiguredSources(List<String> sources) {
        try {
            org.jackhuang.hmcl.setting.LauncherSettings settings =
                    org.jackhuang.hmcl.setting.SettingsManager.settings();
            settings.pluginCatalogSourcesProperty().setAll(sources);
            // The single address this launcher had before sources were a list is
            // cleared as soon as a list is written: leaving it set would make the
            // first source the one the list no longer names.
            settings.pluginCatalogUrlProperty().set("");
            org.jackhuang.hmcl.setting.SettingsManager.save();
        } catch (RuntimeException e) {
            LOG.warning("Could not write the catalogue sources", e);
        }
    }

    private CatalogSources() {
    }
}
