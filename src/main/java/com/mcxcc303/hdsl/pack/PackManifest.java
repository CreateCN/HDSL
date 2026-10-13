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
package com.mcxcc303.hdsl.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.plugin.DshPluginSettings;
import com.mcxcc303.hdsl.skill.DshSkillManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The `hdslp` format: what a pack declares, and how it is read back.
///
/// A pack is a zip holding `manifest.json` and the plugins the exporting machine could not
/// name from the registry, carried under `plugins/`.  The manifest is what a launcher reads
/// before it installs anything: the harness version, the profile, the plugins — each with whether it
/// is active, whether it came from a file, and whether it is part of the in-box set.
@NotNullByDefault
public final class PackManifest {
	/// What a pack says it is.
	public static final String FORMAT = "hdsl-modpack";

	/// The file extension this launcher's own packs are written with.
	///
	/// The container is a plain ZIP, so `.zip` opened it — and told nobody anything. A pack written
	/// by this launcher is a different thing from an archive somebody zipped by hand, and the name
	/// is the only place that can say so before anything is unzipped: it is what a file manager
	/// shows, what a browser offers to download, and what somebody types into the import box.
	///
	/// `.dspack` is the community's and stays theirs; this is the launcher's own format.
	public static final String FILE_EXTENSION = ".hdslp";

	/// The extensions an import accepts.
	///
	/// `.zip` is kept because packs written before the extension existed are `.zip`, and refusing
	/// them would be refusing the user's own files to make a naming point. What a file *is* is
	/// decided by its manifest, which both spellings carry.
	public static final List<String> ACCEPTED_EXTENSIONS = List.of(".hdslp", ".zip");

	/// The format's version.
	///
	/// Version 2 added the plugins a pack carries — a plugin installed from a file that the registry
	/// does not publish now travels inside the pack, under `plugins/`, and is put back into the
	/// instance's own plugin directory on install. A version 2 pack is therefore one a version 1
	/// launcher cannot install correctly: it would read the plugin as a local one it cannot fetch and
	/// leave it out, so the pack is refused instead, which is the honest answer.
	///
	/// Version 3 added the skill packs a pack carries — the skills an instance had under its
	/// home, under `skills/`, put back into the next home on install. Skills are content rather
	/// than configuration, which is why they are the second exception to the rule at the top of
	/// this file: they cannot be named and fetched, so they travel as their own files. A version
	/// 2 launcher would leave them out in silence, so a version 3 pack is refused by it instead.
	public static final int FORMAT_VERSION = 3;

	/// The manifest's name inside the archive.
	public static final String MANIFEST = "manifest.json";

	private PackManifest() {
	}

	/// Reads a pack's manifest.
	///
	/// @param pack the archive
	/// @return the manifest
	/// @throws DshException when the archive holds no manifest
	public static Manifest readManifest(Path pack) throws DshException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(pack))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (MANIFEST.equals(entry.getName())) {
					JsonElement parsed = JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
					if (!parsed.isJsonObject()) {
						throw new DshException("The pack's manifest is not an object");
					}
					return manifestOf(parsed.getAsJsonObject());
				}
			}
		} catch (IOException e) {
			throw new DshException("Failed to read " + pack, e);
		}
		throw new DshException("This file holds no modpack manifest");
	}

	/// Reads a manifest object.
	///
	/// @param root the manifest
	/// @return the manifest record
	static Manifest manifestOf(JsonObject root) {
		List<Plugin> plugins = new ArrayList<>();
		JsonElement list = root.get("plugins");
		if (list != null && list.isJsonArray()) {
			for (JsonElement element : list.getAsJsonArray()) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject object = element.getAsJsonObject();
				String name = string(object, "name");
				if (name == null) {
					continue;
				}
				plugins.add(new Plugin(name,
				                       string(object, "version"),
				                       bool(object, "active"),
				                       bool(object, "local"),
				                       bool(object, "bundled")));
			}
		}

		List<String> bundles = new ArrayList<>();
		JsonElement active = root.get("bundles");
		if (active != null && active.isJsonArray()) {
			for (JsonElement element : active.getAsJsonArray()) {
				if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
					bundles.add(element.getAsString());
				}
			}
		}

		return new Manifest(string(root, "format") == null ? "" : string(root, "format"),
		                    integer(root, "version"),
		                    string(root, "createdAt") == null ? "" : string(root, "createdAt"),
		                    string(root, "instanceId") == null ? "" : string(root, "instanceId"),
		                    string(root, "dshVersion") == null ? "" : string(root, "dshVersion"),
		                    string(root, "appBoot"),
		                    string(root, "profile") == null ? DshInstance.DEFAULT_PROFILE : string(root, "profile"),
		                    List.copyOf(plugins),
		                    List.copyOf(bundles),
		                    bool(root, "hasPatch"),
		                    string(root, "name") == null ? "" : string(root, "name"),
		                    string(root, "packVersion") == null ? "1.0" : string(root, "packVersion"),
		                    string(root, "author") == null ? "" : string(root, "author"),
		                    string(root, "description") == null ? "" : string(root, "description"),
		                    string(root, "url") == null ? "" : string(root, "url"),
		                    string(root, "referenceUrl") == null ? "" : string(root, "referenceUrl"),
		                    integer(root, "sessionCount"),
		                    strings(root, "settings"),
		                    strings(root, "settingsOmitted"),
		                    strings(root, "skills"));
	}

	/// Reads an array of strings.
	///
	/// @param root the object
	/// @param name the field
	/// @return the strings, empty when the field is absent or not an array
	static List<String> strings(JsonObject root, String name) {
		List<String> values = new ArrayList<>();
		JsonElement element = root.get(name);
		if (element != null && element.isJsonArray()) {
			for (JsonElement item : element.getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
					values.add(item.getAsString());
				}
			}
		}
		return List.copyOf(values);
	}

	/// Refuses a pack this launcher cannot read.
	///
	/// @param manifest the pack's manifest
	/// @throws DshException when the format or version is not one this reads
	static void requireFormat(Manifest manifest) throws DshException {
		if (!FORMAT.equals(manifest.format())) {
			throw new DshException("This file is not an instance pack (" + manifest.format() + ")");
		}
		if (manifest.version() > FORMAT_VERSION) {
			throw new DshException("This pack was written by a newer launcher (format " + manifest.version() +
			                       "), which this one cannot read");
		}
		if (manifest.dshVersion().isBlank()) {
			throw new DshException("This pack does not say which DeepSeek Harness version it wants");
		}
	}

	/// Returns a member as a primitive, or `null`.
	///
	/// @param object the object
	/// @param name   the member
	/// @return the value, or `null`
	static @Nullable JsonElement member(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element == null || !element.isJsonPrimitive() ? null : element;
	}

	/// Returns a string member, or `null`.
	///
	/// @param object the object
	/// @param name   the member
	/// @return the string, or `null`
	static @Nullable String string(JsonObject object, String name) {
		JsonElement element = member(object, name);
		return element == null || !((JsonPrimitive) element).isString() ? null : element.getAsString();
	}

	/// Returns a boolean member, or false.
	///
	/// @param object the object
	/// @param name   the member
	/// @return the value, or false
	static boolean bool(JsonObject object, String name) {
		JsonElement element = member(object, name);
		if (element == null) {
			return false;
		}
		try {
			return element.getAsBoolean();
		} catch (RuntimeException e) {
			return false;
		}
	}

	/// Returns an integer member, or zero.
	///
	/// @param object the object
	/// @param name   the member
	/// @return the number, or zero
	static int integer(JsonObject object, String name) {
		JsonElement element = member(object, name);
		if (element == null || !((JsonPrimitive) element).isNumber()) {
			return 0;
		}
		return element.getAsInt();
	}

	/// Reports whether a declared version is really the path of a local file.
	///
	/// A package manager records a local installation by the specification it was
	/// given, in the field a version would otherwise hold, so a profile that
	/// installed a packed plugin says `file:/…/plugins/x.tgz` where a version
	/// belongs. Reading that as a version would have a pack ask for a package
	/// called `x@file:/…`, which resolves to nothing.
	///
	/// @param version the declared version
	/// @return whether it is a local installation's path
	static boolean isLocalSpec(@Nullable String version) {
		if (version == null || version.isBlank()) {
			return false;
		}
		String value = version.trim().toLowerCase(Locale.ROOT);
		return value.startsWith("file:") || value.startsWith("link:") || value.startsWith("/") ||
		       value.startsWith("./") || value.startsWith("../");
	}

	/// One plugin the pack names.
	///
	/// @param name    the package name
	/// @param version the declared version, or the path a locally installed plugin
	///                was installed from
	/// @param active  whether the package is in the profile's bundle list
	/// @param local   whether the plugin was installed from a file this instance
	///                keeps, and so cannot be fetched by anyone else
	/// One plugin a pack names.
	/// @param name    the package name
	/// @param version the version the pack pins, or a local specification for a plugin that was not
	///                fetched — which is a path, and only meaningful on the machine it names
	/// @param active  whether the pack boots it as a bundle
	/// @param local   whether the instance installed it from a file rather than a registry
	/// @param bundled whether the pack carries the plugin's own files, which is what makes a local
	///                plugin installable somewhere else
	public record Plugin(String name, String version, boolean active, boolean local, boolean bundled) {
	}

	/// What a pack describes.
	///
	/// @param format          the format identifier
	/// @param version         the format version
	/// @param createdAt       when the pack was written
	/// @param instanceId      the instance it came from
	/// @param dshVersion      the DeepSeek Harness version it pins
	/// @param appBoot         the boot library it is paired with, or `null` for the same version
	/// @param profile         the profile the plugins belong to
	/// @param plugins         the plugins, in the profile manifest's order
	/// @param bundles         the active bundle list, in load order
	/// @param hasPatch        whether the pack carries the profile's patch layer
	/// @param name            what the pack is called
	/// @param packVersion     the pack's own version
	/// @param author          who made it, or an empty string
	/// @param description     what it is for, or an empty string
	/// @param sessionCount    how many conversations it carries, zero for none
	/// @param settings        the `settings.yaml` sections the pack carries, in the file's order
	/// @param settingsOmitted the `section.key` paths left out of them because they look like
	///                        credentials, so whoever opens the pack can see what it does not hold
	/// @param skills          the skill packs the pack carries, by the name each has on disk
	public record Manifest(String format, int version, String createdAt, String instanceId, String dshVersion,
	                       @Nullable String appBoot, String profile, List<Plugin> plugins, List<String> bundles,
	                       boolean hasPatch, String name, String packVersion, String author, String description,
	                       String url, String referenceUrl, int sessionCount, List<String> settings,
	                       List<String> settingsOmitted, List<String> skills) {

		/// Returns the plugins that should be installed, in the order they are
		/// listed in.
		///
		/// A pack can name a package that is not in the bundle list — an installed
		/// but switched-off plugin. It is still installed, because that is what
		/// the instance it came from had; whether the harness then contributes its
		/// layer is decided by the bundle list, which is what the pack restores
		/// separately.
		///
		/// @return the installation specifications, in order
		public List<String> installSpecs() {
			List<String> specs = new ArrayList<>();
			for (Plugin plugin : plugins) {
				if (plugin.local()) {
					continue;
				}
				specs.add(plugin.version() == null || plugin.version().isBlank()
				          ? plugin.name()
				          : plugin.name() + "@" + plugin.version());
			}
			return specs;
		}

		/// Returns the plugins that were installed from a file rather than fetched.
		///
		/// A profile records the path it installed a local plugin from, which is a
		/// path inside the instance that had it; another instance cannot install
		/// from it, and a pack that pretended otherwise would describe an
		/// installation that cannot be made.
		///
		/// @return the local plugins
		public List<Plugin> localPlugins() {
			return plugins.stream().filter(Plugin::local).toList();
		}

		/// Returns the local plugins a pack carries the files of.
		///
		/// @return the plugins whose files travel inside the pack
		public List<Plugin> bundledPlugins() {
			return plugins.stream().filter(Plugin::bundled).toList();
		}

		/// Returns the local plugins a pack cannot install.
		///
		/// A pack written before it carried local plugin files names one, and so does a pack whose
		/// instance had already lost the file: either way there is nothing to install from, and
		/// saying which plugin it is beats a resolve that fails on a path from another machine.
		///
		/// @return the plugins that cannot be installed
		public List<Plugin> uninstallablePlugins() {
			return plugins.stream().filter(plugin -> plugin.local() && !plugin.bundled()).toList();
		}
	}

	/// Writes an instance's configuration into a pack.
	///
	/// What a pack says about itself, and what it carries.
	///
	/// @param name            what the pack is called, or an empty string for the instance's id
	/// @param version         the pack's own version
	/// @param author          who made it
	/// @param description     what it is for
	/// @param includeSessions whether the instance's conversations travel with it
	/// @param excludedBundles the bundles the person chose to leave out
	/// @param settings        the `settings.yaml` sections that travel: a plugin's own settings, and
	///                        most of what "the same environment" means for it
	/// @param skills          the skill packs that travel, by the name each has under the home's
	///                        skills directory; names rather than packs, so the choice is a small
	///                        thing to hold and is read against what is there when the pack is
	///                        written
	public record Options(String name, String version, String author, String description, String url,
	                      String referenceUrl, boolean includeSessions, Set<String> excludedBundles,
	                      Set<String> settings, Set<String> skills) {
		/// Returns options that carry no addresses.
		///
		/// @param name            what the pack is called
		/// @param version         the pack's own version
		/// @param author          who made it
		/// @param description     what it is for
		/// @param includeSessions whether the conversations travel
		public Options(String name, String version, String author, String description, boolean includeSessions) {
			this(name, version, author, description, "", "", includeSessions, Set.of(), Set.of(), Set.of());
		}

		/// Returns options that carry every bundle.
		///
		/// @param name            what the pack is called
		/// @param version         the pack's own version
		/// @param author          who made it
		/// @param description     what it is for
		/// @param includeSessions whether the conversations travel

		/// Returns the options a pack is written with when nobody chose any.
		///
		/// Everything a plugin keeps in the harness's settings travels, because that is what makes a
		/// pack an environment rather than a list of packages — and because the alternative, a pack
		/// that names a sidebar but not the stylesheet it was configured with, is the difference
		/// nobody notices until they open it. Values that look like credentials are removed on the
		/// way out; see [DshPluginSettings].
		///
		/// The same is true of the skill packs an instance has: a pack that reproduces an
		/// environment without the skills it was set up around is missing the part that took the
		/// longest to assemble, and a skill is content rather than configuration, so there is
		/// nowhere else for it to come from.
		///
		/// @param instance the instance
		/// @return the options
		public static Options of(DshInstance instance) {
			Set<String> settings = Set.of();
			try {
				settings = new LinkedHashSet<>(DshPluginSettings.sectionsOf(instance.homeDirectory()));
			} catch (DshException e) {
				LOG.warning("Failed to read the settings of " + instance.id(), e);
			}
			Set<String> skills = Set.of();
			try {
				skills = new LinkedHashSet<>(DshSkillManager.packableNames(instance.homeDirectory()));
			} catch (DshException e) {
				LOG.warning("Failed to read the skills of " + instance.id(), e);
			}
			return new Options(instance.id(), "1.0", "", "", "", "", false, Set.of(), settings, skills);
		}

		/// Returns whether a bundle travels.
		///
		/// @param bundle the bundle's name
		/// @return whether it does
		public boolean includesBundle(String bundle) {
			return !excludedBundles.contains(bundle);
		}
	}
}
