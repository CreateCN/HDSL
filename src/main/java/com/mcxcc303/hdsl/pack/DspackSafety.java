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

import com.mcxcc303.hdsl.core.DshException;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/// What a pack is not allowed to write.
///
/// A pack is an archive somebody else made, so each member's name is resolved against the
/// destination and refused when it leaves it, and the machine-owned files a pack must never
/// overwrite — credentials, keys, the harness's own install — are named here.
@NotNullByDefault
public final class DspackSafety {
	/// Names a pack may not write, whatever it says about itself.
	///
	/// The same list the export side refuses, kept here as well: the two sides guard different
	/// things — the exporter guards the author's secrets from being published, and this guards the
	/// person's machine from being written to — so neither can rely on the other having run.
	private static final List<String> DENY_NAMES = List.of(".env",
	                                                       ".netrc",
	                                                       ".pypirc",
	                                                       ".npmrc",
	                                                       ".yarnrc",
	                                                       ".yarnrc.yml",
	                                                       "credentials.yaml",
	                                                       ".credentials.yaml",
	                                                       "id_rsa",
	                                                       "id_ed25519",
	                                                       "id_ecdsa",
	                                                       "id_ed448");

	/// Extensions a pack may not write.
	private static final List<String> DENY_EXTENSIONS = List.of(".key", ".pem", ".p12", ".pfx", ".jks", ".keystore");

	private DspackSafety() {
	}

	/// Returns where a member lands, refusing anything that would leave the destination.
	///
	/// The check is on the **resolved** path rather than on the name, because a name is what an
	/// attacker controls: `a/../../b` and `a/./../../b` are the same escape written two ways, and
	/// both resolve outside. Normalising first and comparing after is what catches both.
	///
	/// @param root the directory the member must stay inside
	/// @param name the member's name, already stripped of its prefix
	/// @return where it lands
	/// @throws DshException when it would land outside
	static Path resolve(Path root, String name) throws DshException {
		Path base = root.toAbsolutePath().normalize();
		Path target = base.resolve(name).normalize();
		if (!target.startsWith(base)) {
			throw new DshException(
					"That pack tries to write outside the folder it is installing " + "into (" + name + ")", null);
		}
		return target;
	}

	/// Refuses a member that names something a pack must never write.
	///
	/// @param name   the member's name
	/// @param target where it would land
	/// @throws DshException when it may not be written
	static void refuseIfUnsafe(String name, Path target) throws DshException {
		String file = target.getFileName().toString();
		String lower = file.toLowerCase(Locale.ROOT);
		if (DENY_NAMES.contains(lower) || lower.startsWith("id_rsa") || lower.startsWith("id_ed25519") ||
		    lower.startsWith("id_ecdsa") || lower.startsWith("id_ed448")) {
			throw new DshException(
					"That pack tries to write " + file + ", which holds credentials and is never installed from a pack",
					null);
		}
		for (String extension : DENY_EXTENSIONS) {
			if (lower.endsWith(extension)) {
				throw new DshException(
						"That pack tries to write " + file + ", which is a key file and is never installed from a pack",
						null);
			}
		}
		// A nested archive is refused too: it is the one member whose contents cannot be checked
		// before they are on the disk, and nothing a pack needs is stored as one.
		for (String extension : List.of(".zip", ".tar", ".tgz", ".gz", ".bz2", ".xz", ".7z", ".rar")) {
			if (lower.endsWith(extension) && !name.startsWith("overrides/plugins/")) {
				throw new DshException(
						"That pack holds a nested archive (" + name + "), which is never installed from a pack", null);
			}
		}
	}

	/// Reports whether a name at the archive's root is one of the pack's machine files.
	///
	/// The exporter writes by the same rule, through this same method: a file that describes the
	/// profile belongs at the archive root and is copied to the destination **before** the
	/// dependencies are resolved, and everything else belongs under `overrides/` and lands after.
	/// A pack that put its `package.json` under `overrides/` installs and then boots with none of
	/// its plugins, because the resolve ran while the profile was still empty.
	///
	/// @param name the member's name
	/// @return whether it is copied to the destination
	static boolean isMachineFile(String name) {
		return name.equals("package.json") || name.equals("pnpm-lock.yaml") || name.equals("pnpm-workspace.yaml");
	}
}
