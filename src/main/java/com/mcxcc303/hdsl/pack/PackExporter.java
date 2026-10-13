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

import com.mcxcc303.hdsl.account.DshProfilePatch;
import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.harness.AppBootPin;
import com.mcxcc303.hdsl.harness.DshPackageRegistry;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.launch.DshSession;
import com.mcxcc303.hdsl.launch.DshSessions;
import com.mcxcc303.hdsl.plugin.DshPluginBundle;
import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import com.mcxcc303.hdsl.plugin.DshPluginSettings;
import com.mcxcc303.hdsl.skill.DshSkill;
import com.mcxcc303.hdsl.skill.DshSkillManager;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Writing an instance out as a pack.
///
/// What travels is the instance's *configuration*: the version it pins, the profile, the plugin list
/// in its own order, the profile's patch layer, the local plugin files, the settings its plugins keep
/// and its skill packs.  What does not is its state — sessions, credentials, keys — because a pack is
/// meant to be published.
@NotNullByDefault
public final class PackExporter {
	/// The profile patch layer's name inside the archive.
	private static final String PATCH = "cordis.patch.yml";

	private PackExporter() {
	}

	/// @param instance the instance to describe
	/// @param target   the archive to create
	/// @param onStage  receives progress lines, or `null`
	/// @return what was written
	/// @throws DshException when the configuration cannot be read or written
	public static ExportResult export(DshInstance instance, Path target, @Nullable Consumer<String> onStage)
	throws DshException {
		return export(instance, target, PackManifest.Options.of(instance), onStage);
	}

	/// Writes an instance's configuration into a pack.
	///
	/// @param instance the instance to describe
	/// @param target   the archive to create
	/// @param options  what the pack should say and carry
	/// @param onStage  receives progress lines, or `null`
	/// @return what was written
	/// @throws DshException when the configuration cannot be read or written
	public static ExportResult export(DshInstance instance,
	                                  Path target,
	                                  PackManifest.Options options,
	                                  @Nullable Consumer<String> onStage) throws DshException {
		Path profileDirectory = instance.homeDirectory().resolve("profiles").resolve(instance.profile());
		Map<String, String> dependencies = DshPluginInstaller.readDependencies(instance.homeDirectory(),
		                                                                       instance.profile());
		List<String> bundles = new ArrayList<>(DshPluginInstaller.readBundles(instance.homeDirectory(),
		                                                                      instance.profile()));
		// A bundle somebody unticked is not in the pack and is not recorded as one of its plugins:
		// a pack that named a plugin it does not install would describe an installation nobody can
		// make, and an import would try.
		bundles.removeIf(bundle -> !options.includesBundle(bundle));

		List<PackManifest.Plugin> plugins = new ArrayList<>();
		List<DshPluginBundle.Payload> carried = new ArrayList<>();
		for (Map.Entry<String, String> entry : dependencies.entrySet()) {
			if (!options.includesBundle(entry.getKey())) {
				continue;
			}
			boolean active = bundles.contains(entry.getKey());
			if (!PackManifest.isLocalSpec(entry.getValue())) {
				plugins.add(new PackManifest.Plugin(entry.getKey(), entry.getValue(), active, false, false));
				continue;
			}
			plugins.add(localPlugin(profileDirectory, entry.getKey(), entry.getValue(), active, carried, onStage));
		}

		Path patch = profileDirectory.resolve("cordis.patch.yml");
		boolean hasPatch = Files.isRegularFile(patch);
		String appBoot = AppBootPin.readAppBoot(instance);
		if (appBoot != null && appBoot.equals(instance.version())) {
			// A pin equal to the version is what an instance has by default; a pack
			// does not need to say so, and saying it would make the pack claim a
			// choice nobody made.
			appBoot = null;
		}

		List<DshSession> sessions = options.includeSessions() ? DshSessions.list(instance.homeDirectory()) : List.of();

		DshPluginSettings.Carried settings = options.settings().isEmpty()
		                                     ? new DshPluginSettings.Carried("",
		                                                                     List.of(),
		                                                                     List.of())
		                                     : DshPluginSettings.extract(instance.homeDirectory(), options.settings());
		if (!settings.sections().isEmpty()) {
			report(onStage,
			       "Carrying the settings of " + settings.sections().size() + " plugin(s): " +
			       String.join(", ", settings.sections()));
		}
		if (!settings.omitted().isEmpty()) {
			// Named rather than counted: whoever opens the pack should be able to see what was left
			// out, and whoever made it should be able to see that a setting did not travel.
			report(onStage,
			       "Left out " + settings.omitted().size() + " value(s) that look like credentials: " +
			       String.join(", ", settings.omitted()));
		}

		// The pack carries the skill packs the options asked for and the home actually has:
		// a name for a skill that is no longer there is not something to fail over, because the
		// alternative is a pack that cannot be written after a skill was deleted.
		List<DshSkill> skills = DshSkillManager.packable(instance.homeDirectory(), options.skills());
		List<String> skillNames = skills.stream().map(DshSkill::fileName).toList();

		PackManifest.Manifest manifest = new PackManifest.Manifest(PackManifest.FORMAT,
		                                                           PackManifest.FORMAT_VERSION,
		                                                           Instant.now().toString(),
		                                                           instance.id(),
		                                                           instance.version(),
		                                                           appBoot,
		                                                           instance.profile(),
		                                                           List.copyOf(plugins),
		                                                           List.copyOf(bundles),
		                                                           hasPatch,
		                                                           options.name(),
		                                                           options.version(),
		                                                           options.author(),
		                                                           options.description(),
		                                                           options.url(),
		                                                           options.referenceUrl(),
		                                                           sessions.size(),
		                                                           settings.sections(),
		                                                           settings.omitted(),
		                                                           skillNames);

		report(onStage, "Recording " + plugins.size() + " plugin(s), " + bundles.size() + " active bundle(s)");
		Path parent = target.toAbsolutePath().getParent();
		try {
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
				if (hasPatch) {
					// The copy goes in repaired, for the same reason the installer repairs it on the
					// way out: a profile that both boots a plugin as a bundle and inserts it applies
					// it twice and does not start. Fixing it only on install would leave every pack
					// made from such an instance carrying the defect forward.
					byte[] body = DshProfilePatch.withoutRedundantInserts(Files.readString(patch,
					                                                                       StandardCharsets.UTF_8),
					                                                      bundles)
					                             .text()
					                             .getBytes(StandardCharsets.UTF_8);
					zip.putNextEntry(new ZipEntry(PATCH));
					zip.write(body);
					zip.closeEntry();
				}
				if (!settings.text().isEmpty()) {
					zip.putNextEntry(new ZipEntry(DshPluginSettings.ENTRY));
					zip.write(settings.text().getBytes(StandardCharsets.UTF_8));
					zip.closeEntry();
				}
				for (DshPluginBundle.Payload payload : carried) {
					// A local plugin nobody publishes travels as its own files, and there is no
					// smaller way to say it: the alternative is a pack that cannot be installed.
					DshPluginBundle.writeInto(zip, payload, onStage);
				}
				if (!sessions.isEmpty()) {
					// The conversations are written the way a session pack writes
					// them, because that is the layout the harness reads and the
					// rules are the same either way.
					DshSessionPacks.writeInto(zip, instance.homeDirectory(), sessions, onStage);
					DshSessionPacks.writeAttachmentsInto(zip, instance.homeDirectory(), sessions, onStage);
				}

				if (!skills.isEmpty()) {
					// The layout under the home is the layout in the pack, which is what makes
					// restoring one a copy rather than a translation.
					DshSkillManager.writeInto(zip, skills, onStage);
				}
				zip.putNextEntry(new ZipEntry(PackManifest.MANIFEST));
				zip.write(JsonUtils.GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		} catch (IOException e) {
			throw new DshException("Failed to write " + target, e);
		}

		long size = sizeOf(target);
		report(onStage,
		       "Wrote a pack describing DeepSeek Harness " + instance.version() +
		       (appBoot == null ? "" : " with boot library " + appBoot) + ", " + plugins.size() + " plugin(s)");
		LOG.info("Wrote a modpack for " + instance.id() + " to " + target);
		return new ExportResult(plugins.size(), size);
	}

	/// Records a plugin the instance installed from a file.
	///
	/// The registry is asked first, because a plugin that is published at the version the instance
	/// has does not need to travel: the pack names `name@version` and the install fetches it, which is
	/// smaller and is the same code either way. Only when the registry does not publish it — or cannot
	/// be asked at all, which is not the same as an answer — are the files carried, because a pack
	/// that named a package nobody can fetch would be a pack that does not install.
	///
	/// @param profileDirectory the profile the plugin was installed into
	/// @param name             the dependency name
	/// @param declared         what the profile declares for it
	/// @param active           whether it is in the bundle list
	/// @param carried          collects the payloads the pack has to carry
	/// @param onStage          receives progress lines, or `null`
	/// @return what the pack records for it
	static PackManifest.Plugin localPlugin(Path profileDirectory,
	                                       String name,
	                                       String declared,
	                                       boolean active,
	                                       List<DshPluginBundle.Payload> carried,
	                                       @Nullable Consumer<String> onStage) {
		DshPluginBundle.Payload payload = DshPluginBundle.locate(profileDirectory, name, declared);
		if (payload == null) {
			// Two reasons, and the difference is what a person can do about it: a file that was
			// deleted can be put back where it was, while a plugin somebody is working on has no
			// copy here at all — the profile holds a link to their directory, not the directory.
			report(onStage,
			       DshPluginBundle.namesADirectory(declared, profileDirectory)
			       ? "Note: " + name + " is a plugin under development, installed from the directory" +
			         " it is worked on in, so the pack does not carry it"
			       : "Note: " + name + " was installed from a file this instance no longer has," +
			         " so the pack cannot carry it");
			return new PackManifest.Plugin(name, declared, active, true, false);
		}

		DshPackageRegistry.Availability availability = DshPackageRegistry.availability(name, payload.version());
		if (availability == DshPackageRegistry.Availability.PUBLISHED) {
			report(onStage,
			       name + " " + payload.version() + " is published, so the pack fetches it" +
			       " from the registry rather than carrying it");
			return new PackManifest.Plugin(name, payload.version(), active, false, false);
		}
		if (availability == DshPackageRegistry.Availability.UNKNOWN) {
			report(onStage,
			       "Could not ask the registry about " + name + " " + payload.version() +
			       "; carrying its files instead");
		} else {
			report(onStage, name + " " + payload.version() + " is not published, so the pack must carry it");
		}
		carried.add(payload);
		return new PackManifest.Plugin(name, payload.version(), active, true, true);
	}

	/// Returns the plugins an instance installed from this machine rather than from a registry.
	///
	/// A `file:` or `link:` specification names something that was on the machine the instance was
	/// made on, and a pack is handed to somebody else. What the export does about it depends on
	/// which of the two it is: a packed plugin the launcher copied into the instance travels as the
	/// file it is, while a directory somebody is working on — `link:`, and a `file:` that names a
	/// directory — has no files of the instance's own to carry and is left out of the pack
	/// entirely. Both are worth saying **before** the export starts, which is the last moment at
	/// which the person can still do something about either.
	///
	/// @param instance the instance
	/// @return the plugins, in the profile's order, empty when there are none
	public static List<LocalPlugin> localPlugins(DshInstance instance) throws DshException {
		Path profileDirectory = instance.homeDirectory().resolve("profiles").resolve(instance.profile());
		List<LocalPlugin> plugins = new ArrayList<>();
		for (Map.Entry<String, String> entry : DshPluginInstaller.readDependencies(instance.homeDirectory(),
		                                                                           instance.profile()).entrySet()) {
			if (PackManifest.isLocalSpec(entry.getValue())) {
				plugins.add(new LocalPlugin(entry.getKey(),
				                            DshPluginBundle.namesADirectory(entry.getValue(), profileDirectory)));
			}
		}
		return List.copyOf(plugins);
	}

	/// Reads the settings a pack carries.
	///
	/// @param pack the archive
	/// @return the text, or an empty string when the pack carries none
	/// @throws DshException when the archive cannot be read
	static String readSettings(Path pack) throws DshException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(pack))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (DshPluginSettings.ENTRY.equals(entry.getName())) {
					return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		} catch (IOException e) {
			throw new DshException("Failed to read " + pack, e);
		}
		return "";
	}

	/// Reads a pack's patch layer into a profile.
	///
	/// @param pack   the archive
	/// @param target the file to write
	/// @throws DshException when the archive cannot be read or the file written
	static void writePatch(Path pack, Path target) throws DshException {
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(pack))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (PATCH.equals(entry.getName())) {
					byte[] body = zip.readAllBytes();
					Files.createDirectories(target.getParent());
					Path staging = target.resolveSibling("cordis.patch.yml.pack");
					Files.write(staging, body);
					Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
					return;
				}
			}
		} catch (IOException e) {
			throw new DshException("Failed to read " + pack, e);
		}
		throw new DshException("The pack says it carries a patch layer, but does not");
	}

	/// Reports progress.
	///
	/// @param onStage the sink, or `null`
	/// @param message the message
	static void report(@Nullable Consumer<String> onStage, String message) {
		if (onStage != null) {
			onStage.accept(message);
		}
	}

	/// Returns a file's size.
	///
	/// @param file the file
	/// @return the size, or zero
	static long sizeOf(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0;
		}
	}

	/// What an export wrote.
	///
	/// @param plugins how many plugins were recorded
	/// @param bytes   the pack's size
	public record ExportResult(int plugins, long bytes) {
	}

	/// One plugin an instance installed from this machine rather than from a registry.
	///
	/// @param name        the dependency name
	/// @param development whether it was installed from a directory somebody is working on — a
	///                    `link:`, or a `file:` that is not an archive — which a pack does not carry
	public record LocalPlugin(String name, boolean development) {
	}
}
