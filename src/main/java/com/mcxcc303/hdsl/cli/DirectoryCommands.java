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
package com.mcxcc303.hdsl.cli;

import com.mcxcc303.hdsl.cli.Cli.Invocation;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.LauncherLock;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/// The commands that manage the folders instances live in: which ones the launcher looks in, and
/// adding one.
///
/// Adding a folder also selects it, because a person who added one meant for their instances to go
/// there.
@NotNullByDefault
public final class DirectoryCommands {
	private DirectoryCommands() {
	}

	/// Runs the `directory list` command: the folders the launcher looks for instances in.
	///
	/// @param out the stream for normal output
	/// @return the process exit code
	static int listDirectories(PrintStream out) {
		var selected = GameDirectoryManager.selected();
		for (var directory : GameDirectoryManager.directories()) {
			out.println((directory.id().equals(selected.id()) ? "* " : "  ") + directory.displayName() +
			            (directory.isDefault() ? "  [default]" : "") + "  " + directory.path() + "  " +
			            GameDirectoryManager.countInstances(directory) + " instance(s)");
		}
		return 0;
	}

	/// Runs the `directory add` command: adds a folder to the list and reports what it holds.
	///
	/// The folder is selected as well as added, which is what the page that adds one does and what a
	/// person means by it: from here on the instances in this folder are the ones being worked on,
	/// and — with no instance named on the command line — this is where a new one is made.
	///
	/// The write goes through the launcher's lock, because the settings file is written whole out of
	/// the copy of it a process read when it started. Writing that copy here would undo whatever the
	/// window has changed since, and the window's next save would drop the folder in turn; so when
	/// the window holds the lock this says so instead, and the file is read and written with nothing
	/// else in between.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int addDirectory(Invocation invocation, PrintStream out, PrintStream err) {
		Path path = Path.of(invocation.option("path"));
		if (!Files.isDirectory(path)) {
			err.println("error: " + path + " is not a directory");
			return 1;
		}

		LauncherLock lock = LauncherLock.shared();
		if (!lock.acquire()) {
			err.println("error: another launcher owns the settings" +
			            lock.holder().map(holder -> " (pid " + holder.pid() + ")").orElse("") +
			            "; add the folder in its window, or close it");
			return 1;
		}
		try {
			GameDirectory added = SettingsManager.addGameDirectory(path);
			int count = GameDirectoryManager.countInstances(added);
			out.println("Added " + added.displayName() + " (" + added.path() + ")");
			out.println("Found " + count + " instance(s); nothing was copied or moved");
			return 0;
		} finally {
			lock.release();
		}
	}
}
