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
import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.instance.DshHomeMode;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.instance.DshInstanceManager;
import com.mcxcc303.hdsl.launch.DshLauncher;
import com.mcxcc303.hdsl.launch.DshProcess;
import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/// Writing a pack into an instance, and proving it starts.
///
/// The files land by part: the instance's own files, its profile's overrides, its home, and the
/// machine-wide settings a pack may carry.  What is written is then started once, and a pack whose
/// instance does not come up is reported with the child's own last lines rather than an
/// explanation of them.
@NotNullByDefault
public final class DspackLander {
	/// What landing a pack's files came to.
	///
	/// @param files      how many files were written
	/// How long the installed pack is given to come up, and to go down again, before the check
	/// gives up on it. An instance answers in seconds; one that answers by hanging is not one that
	/// would have worked for a person either.
	private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(90);

	/// How many lines of a failed composition are shown. Enough for the reason and the package or
	/// route it names, and not the plugin tree above it.
	private static final int FAILURE_LINES = 12;

	private DspackLander() {
	}

	/// Writes a pack's files into a destination.
	///
	/// Two places, and which is which is the container's own layout:
	///
	/// - `overrides/` goes to the **destination root**, which for a `profile` pack is the profile's
	///   directory and for a `dshhome` pack is the whole home.
	/// - `home/` goes to the home, and is only meaningful for a `profile` pack — a `dshhome` pack
	///   already covers the home with its overrides.
	///
	/// The machine files at the archive's root (`package.json`, the lock file, the workspace file)
	/// are copied to the destination root as well. They are *not* part of `overrides/`, because they
	/// describe the pack rather than overriding anything — but a profile without them is not the
	/// profile the pack describes.
	///
	/// The order is the specification's, and it matters: overrides are landed so that the user's own
	/// files — `cordis.patch.yml` among them — end up on top of whatever a dependency would install.
	///
	/// @param archive     the pack
	/// @param destination where `overrides/` lands
	/// @param home        where `home/` lands, or `null` for a pack that has none
	/// @return what was written
	/// @throws DshException when the archive cannot be read, or holds a member it may not write
	public static Landed land(Path archive, Path destination, @Nullable Path home) throws DshException {
		return land(archive, destination, home, EnumSet.allOf(Part.class));
	}

	/// Writes the parts of a pack that were asked for.
	///
	/// @param archive     the pack
	/// @param destination where `overrides/` lands
	/// @param home        where `home/` lands, or `null` for a pack that has none
	/// @param parts       which sets to write
	/// @return what was written
	/// @throws DshException when the archive cannot be read, or holds a member it may not write
	public static Landed land(Path archive, Path destination, @Nullable Path home, Set<Part> parts)
	throws DshException {
		int files = 0;
		int overrides = 0;
		int fromHome = 0;
		int machine = 0;
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (entry.isDirectory()) {
					continue;
				}
				// Symbolic links need no special handling here, and that is worth writing down
				// because it looks like an omission. A ZIP stores a link as an entry whose *content*
				// is the target's path and whose Unix mode marks it as a link; `java.util.zip` does
				// not expose the mode, so this cannot tell one from a small text file — and it does
				// not have to. Nothing here calls `Files.createSymbolicLink`, so such an entry is
				// written out as an ordinary file holding the text of a path. The link is never
				// created, and the path check below already refuses a member that would land outside
				// the destination whatever it points at.

				Path target;
				if (name.startsWith("overrides/")) {
					if (!parts.contains(Part.OVERRIDES)) {
						continue;
					}
					target = DspackSafety.resolve(destination, name.substring("overrides/".length()));
					overrides++;
				} else if (name.startsWith("home/") && home != null) {
					if (!parts.contains(Part.HOME)) {
						continue;
					}
					target = DspackSafety.resolve(home, name.substring("home/".length()));
					fromHome++;
				} else if (DspackSafety.isMachineFile(name)) {
					if (!parts.contains(Part.MACHINE)) {
						continue;
					}
					target = DspackSafety.resolve(destination, name);
					machine++;
				} else {
					// `manifest.json`, `dspack.json`, and anything else the pack carries for its own
					// sake are not part of the installation.
					continue;
				}

				DspackSafety.refuseIfUnsafe(name, target);
				Files.createDirectories(target.getParent());
				Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
				files++;
			}
		} catch (IOException e) {
			throw new DshException("Could not unpack " + archive + ": " + e.getMessage(), e);
		}
		return new Landed(files, overrides, fromHome, machine);
	}

	/// Finishes an install that a build-script question stopped, with the same promise as [#installNew].
	///
	/// [#installNew] keeps the instance when pnpm asks for permission to run a package's install
	/// scripts: the question is written into that instance's profile, and the answer has to be
	/// written to the same one. This is the other half — the pack is put into that instance once the
	/// answer is in — and a failure that is not another question takes the half-made instance away,
	/// exactly as the first attempt would have.
	///
	/// @param archive  the pack
	/// @param instance the instance the question was about
	/// @param report   receives progress lines, or `null`
	/// @return the instance, filled in
	/// @throws DshException when the pack cannot be installed
	public static DshInstance finish(Path archive, DshInstance instance, @Nullable Consumer<String> report)
	throws DshException {
		try {
			installInto(archive, instance, report);
			return instance;
		} catch (DshException | RuntimeException failed) {
			if (failed instanceof DshPluginInstaller.DshBuildScriptApprovalRequired) {
				// Another package is waiting; the instance stays for the same reason it stayed the
				// first time, and the next answer finishes it.
				throw failed;
			}
			Logger.LOG.warning("Could not finish the pack in " + instance.id() + "; removing it", failed);
			HarnessInstaller.discardPartial(instance);
			try {
				DshInstanceManager.delete(instance.id());
			} catch (DshException | RuntimeException cleanupFailure) {
				Logger.LOG.warning("Could not remove the half-made instance " + instance.id(), cleanupFailure);
			}
			throw failed;
		}
	}

	/// Puts a recognised pack into an instance that is already at the right version.
	///
	/// **The order is the specification's, and each step is in it for a reason** (§3.2.2):
	///
	/// 1. the pack's own machine files — without them there is no manifest to install from;
	/// 2. the dependency install, which is what turns a list of package names into a profile that
	///    boots;
	/// 3. `overrides/` and `home/`, **last**, so a `cordis.patch.yml` the pack carries lands on top of
	///    whatever the dependency install wrote rather than being replaced by it. Landing the
	///    overrides first — which is what a single pass does — leaves the pack's own patch layer
	///    overwritten by its own dependencies, and the pack then behaves as though the patch were not
	///    there.
	///
	/// @param archive  the pack
	/// @param instance the instance to fill
	/// @param report   receives progress lines, or `null`
	/// @return what was written
	/// @throws DshException when the archive cannot be read, or the install fails
	public static Landed installInto(Path archive, DshInstance instance, @Nullable Consumer<String> report)
	throws DshException {
		Path home = instance.homeDirectory();
		Path profiles = home.resolve("profiles").resolve(instance.profile());

		Landed machine = land(archive, profiles, home, EnumSet.of(Part.MACHINE));
		if (machine.machine() > 0) {
			say(report, "Installing the pack's dependencies");
		} else {
			// A pack with no machine files leaves the profile to the harness, whose own
			// `dsh plugin install` writes the manifest, the patch template and the package-manager
			// settings exactly as upstream writes them. Writing them here would be reproducing
			// upstream's file formats, which is the kind of guessing that has broken an instance
			// before.
			say(report, "Initializing profile " + instance.profile());
		}
		DshPluginInstaller.resolve(instance, report);

		Landed rest = land(archive, profiles, home, EnumSet.of(Part.OVERRIDES, Part.HOME));

		// A pack that both lists a plugin as a bundle and inserts it applies that plugin twice, and a
		// plugin applied twice cannot claim its routes the second time — the profile then fails to
		// load at all. The duplicate is taken out here: after the patch has landed, so it is the
		// pack's own file being repaired, and before anything tries to boot the profile.
		Path patch = profiles.resolve("cordis.patch.yml");
		List<String> keptWhole = DshProfilePatch.dropRedundantInserts(patch,
		                                                              DshPluginInstaller.readBundles(home,
		                                                                                             instance.profile()));
		if (!keptWhole.isEmpty()) {
			say(report,
			    "These are both a bundle and an insert, and carry settings that would be " +
			    "lost if the duplicate were dropped: " + keptWhole);
		}

		// And then the harness is asked what it makes of the profile, because a pack can be broken in
		// ways this cannot see — a plugin the patch inserts that nothing provides, a configuration
		// the schema refuses. Failing here means failing while the pack is still in hand.
		say(report, "Checking that the pack starts");
		verifyBoots(instance);

		return new Landed(machine.files() + rest.files(), rest.overrides(), rest.home(), machine.machine());
	}

	/// Starts the instance the way a launch does, and stops it again, to find out whether it starts
	/// at all.
	///
	/// **Not `--dump-config`, which was the first attempt at this and does not work.** The dump
	/// composes the patch layers and prints the tree; it resolves nothing and applies nothing, so it
	/// exits 0 for a pack whose patch inserts a package nobody provides and for one that applies a
	/// plugin twice — measured, all three of those cases green. What fails at boot is exactly those
	/// things: a module that cannot be found, a plugin claiming a route another copy already claimed.
	///
	/// So the check is a boot: the profile is started on its own port with nothing attached, waited
	/// for, and stopped — the same thing a person would do by hand to answer "will this pack run?".
	/// The instance was made a moment ago and has no state worth keeping yet, which is what makes
	/// booting it safe here.
	///
	/// @param instance the instance the pack was installed into
	/// @throws DshException carrying the harness's own output when the profile does not come up
	static void verifyBoots(DshInstance instance) throws DshException {
		DshLauncher.LaunchPlan plan = DshLauncher.plan(instance, null);
		StringBuilder output = new StringBuilder();
		DshProcess process = DshProcess.startPrepared(instance, plan);
		process.setLogSink(line -> output.append(line).append('\n'));
		try {
			long deadline = System.currentTimeMillis() + VERIFY_TIMEOUT.toMillis();
			while (System.currentTimeMillis() < deadline && process.state() == DshProcess.State.STARTING) {
				Thread.sleep(200);
			}
			if (process.state() != DshProcess.State.READY) {
				throw new DshException("The installed pack does not start:\n" + failureOf(output.toString()));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new DshException("Interrupted while starting the installed pack", e);
		} finally {
			process.stop();
			long gone = System.currentTimeMillis() + VERIFY_TIMEOUT.toMillis();
			while (process.isRunning() && System.currentTimeMillis() < gone) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}

	/// Returns the lines of a failed composition that say what went wrong.
	///
	/// The harness prints a plugin tree before it fails, so the whole output is a page of rows with
	/// one or two lines of reason in it. What a person needs is those lines: the first ones that name
	/// an error, and the ones under them, which carry the package or the route at fault.
	///
	/// @param output everything the harness printed
	/// @return the part worth showing somebody
	static String failureOf(String output) {
		List<String> lines = new ArrayList<>();
		boolean started = false;
		for (String line : output.split("\n")) {
			String trimmed = line.strip();
			if (!started) {
				// The harness tags its own lines — `[ERROR] Error: dsh: plugin tree failed to load: …`
				// — so the reason is not always the first thing on the line.
				started = trimmed.contains("Error") || trimmed.contains("Cannot find package");
			}
			if (started && !trimmed.isEmpty()) {
				lines.add(trimmed);
			}
			if (lines.size() >= FAILURE_LINES) {
				break;
			}
		}
		return lines.isEmpty() ? output.strip() : String.join("\n", lines);
	}

	/// Says something if anybody is listening.
	///
	/// @param report where to say it, or `null`
	/// @param line   what to say
	static void say(@Nullable Consumer<String> report, String line) {
		if (report != null) {
			report.accept(line);
		}
	}

	/// Makes an instance for a pack and installs it into that instance.
	///
	/// **One method rather than a sequence each caller repeats**, and the reason is a failure that was
	/// left on somebody's disk: the instance is created before the harness can be installed into it —
	/// it has to be, because the runtime goes inside it — so a failure between the two leaves an
	/// instance that is in the list and cannot start. The wizard has always cleaned that up; the two
	/// pages that install a pack from the market and from a file each wrote their own sequence and
	/// neither did, so a failed install left `pokemon` sitting in the list with no harness in it.
	///
	/// A sequence written twice is a sequence fixed once, so it is written here once instead.
	///
	/// @param archive the verified pack
	/// @param id      the instance id, which the caller has already made unique
	/// @param profile the profile the pack wants
	/// @param version the harness version the pack pins
	/// @param report  receives progress lines, or `null`
	/// @return the instance, filled in
	/// @throws DshException when the harness or the pack cannot be installed
	public static DshInstance installNew(Path archive,
	                                     String id,
	                                     String profile,
	                                     String version,
	                                     @Nullable Consumer<String> report) throws DshException {
		DshInstance instance = DshInstanceManager.create(id,
		                                                 version,
		                                                 profile,
		                                                 Path.of(System.getProperty("user.home")),
		                                                 DshHomeMode.ISOLATED,
		                                                 null,
		                                                 List.of(),
		                                                 Map.of());
		try {
			say(report, "Installing DeepSeek Harness " + version);
			HarnessInstaller.install(instance, version, report);

			say(report,
			    "Writing the pack's files into " +
			    instance.homeDirectory().resolve("profiles").resolve(instance.profile()));
			installInto(archive, instance, report);
			return instance;
		} catch (DshException | RuntimeException failed) {
			if (failed instanceof DshPluginInstaller.DshBuildScriptApprovalRequired) {
				// The one failure that must **keep** what it made. pnpm writes the packages it is
				// waiting to be told about into the profile's own settings and refuses the install;
				// the answer is written to that same profile by whoever asked the person, and the
				// install then finishes the instance it already has. Removing it here would take
				// the question away with it and the next attempt would ask it again from nothing.
				throw failed;
			}
			// Every other failure takes what was made for this attempt with it. A half-made instance
			// is worse than no instance: it appears in the list, it can be launched, and it fails
			// for a reason that is no longer on screen.
			Logger.LOG.warning("Could not install the pack into " + id + "; removing the instance made for it", failed);
			HarnessInstaller.discardPartial(instance);
			try {
				DshInstanceManager.delete(id);
			} catch (DshException | RuntimeException cleanupFailure) {
				Logger.LOG.warning("Could not remove the half-made instance " + id, cleanupFailure);
			}
			throw failed;
		}
	}

	/// Which of a pack's three sets of files to write.
	///
	/// They are separable because the specification's order interleaves them with work that is not
	/// file copying: the pack's dependencies are installed **between** its machine files and its
	/// overrides, so that a `cordis.patch.yml` the pack carries lands on top of whatever the
	/// dependency install wrote. Copying all three at once cannot express that order.
	public enum Part {
		/// The archive root's `package.json`, lock file and workspace file.
		MACHINE,
		/// `overrides/`, which lands at the destination root.
		OVERRIDES,
		/// `home/`, which lands at the `DSH_HOME` root.
		HOME
	}

	/// @param overrides how many came from `overrides/`
	/// @param home      how many came from `home/`
	/// @param machine   how many were the archive's own machine files
	public record Landed(int files, int overrides, int home, int machine) {
	}
}
