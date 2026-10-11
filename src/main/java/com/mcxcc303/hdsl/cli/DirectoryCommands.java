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
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
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

	/// Prints the folders the launcher looks for instances in.
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

	/// Adds a folder to the list and reports what it holds.
	///
	/// The folder is selected as well as added, which is what the page that adds
	/// one does and what a person means by it: from here on the instances in
	/// this folder are the ones being worked on, and — with no instance named on
	/// the command line — this is where a new one is made.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int addDirectory(Invocation invocation, PrintStream out, PrintStream err) {
		if (invocation.arguments().isEmpty()) {
			err.println("error: --add-directory needs a path");
			return 1;
		}
		try {
			var added = GameDirectoryManager.add(Path.of(invocation.arguments().get(0)));
			int count = GameDirectoryManager.countInstances(added);
			GameDirectoryManager.select(added.id());
			out.println("Added " + added.displayName() + " (" + added.path() + ")");
			out.println("Found " + count + " instance(s); nothing was copied or moved");
			return 0;
		} catch (IllegalArgumentException e) {
			err.println("error: " + e.getMessage());
			return 1;
		}
	}
}
