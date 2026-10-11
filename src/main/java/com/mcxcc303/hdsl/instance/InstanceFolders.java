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

import com.mcxcc303.hdsl.core.DshPaths;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The folders instances live in, and which of them the launcher is showing.
///
/// The answer comes from the launcher rather than being read here: `setFolders` is what
/// points this at the settings, and what a test uses in place of a settings file.  The folder being
/// shown comes first, because that is where a new instance goes and which of two namesakes wins.
@NotNullByDefault
public final class InstanceFolders {
	/// The folders the launcher looks for instances in, the one it is showing first.
	///
	/// Which folders exist is a question only the settings can answer, and the
	/// settings live a layer above this package — so the default answer is asked
	/// of them, every time. It is asked lazily rather than installed once,
	/// because the launcher has three ways in and only one of them starts an
	/// interface: a command-line run that adds a folder and then makes an
	/// instance in it is two processes, and the second one never touches the
	/// interface. Answering from the settings is what makes the folder the user
	/// chose mean the same thing in all three.
	///
	/// The one thing that keeps this from being a circular dependency is that
	/// the settings' own instance listing reads folders directly rather than
	/// through here.
	private static volatile Supplier<List<Path>> folders = InstanceFolders::foldersFromTheSettings;

	private InstanceFolders() {
	}

	/// Asks the settings which folders the launcher keeps instances in.
	///
	/// A launcher whose settings cannot be read — a build with none, a test that
	/// has not set any up — still has the folder it owns, which is where its
	/// instances were before folders could be added at all.
	///
	/// @return the folders, the one being shown first
	private static List<Path> foldersFromTheSettings() {
		try {
			return GameDirectoryManager.instanceFolders();
		} catch (RuntimeException | Error e) {
			LOG.warning("Could not InstanceStore.read the instance folders from the settings; " +
			            "using the folder the launcher owns", e);
			return List.of(DshPaths.INSTANCES);
		}
	}

	/// Points the manager at the folders the launcher knows about.
	///
	/// The settings are the answer by default; this is for a caller that wants a
	/// different one, and for a test, which has no settings to point at and
	/// folders of its own to use instead.
	///
	/// @param supplier the folders, the one being shown first
	/// @return the supplier that was in place, so a caller passing through can
	///         put it back
	public static Supplier<List<Path>> setFolders(Supplier<List<Path>> supplier) {
		Supplier<List<Path>> previous = folders;
		folders = supplier;
		return previous;
	}

	/// Returns the folders the launcher looks for instances in, the current one first.
	///
	/// @return the folders, absolute and without duplicates
	public static List<Path> folders() {
		List<Path> resolved = new ArrayList<>();
		for (Path folder : folders.get()) {
			if (folder == null) {
				continue;
			}
			Path normalized = folder.toAbsolutePath().normalize();
			if (!resolved.contains(normalized)) {
				resolved.add(normalized);
			}
		}
		if (resolved.isEmpty()) {
			resolved.add(DshPaths.INSTANCES.toAbsolutePath().normalize());
		}
		return List.copyOf(resolved);
	}

	/// Returns the folder a new instance is made in.
	///
	/// The one the interface is showing: adding a folder is how a person says
	/// where their instances belong, and installing one is the moment they mean
	/// it. Nothing is written anywhere else until they choose another folder.
	///
	/// @return the folder
	public static Path currentFolder() {
		return folders().get(0);
	}
}
