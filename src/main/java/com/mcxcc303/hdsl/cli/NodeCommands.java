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

import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.runtime.NodeRelease;
import com.mcxcc303.hdsl.runtime.NodeRuntime;
import com.mcxcc303.hdsl.runtime.NodeRuntimeManager;
import com.mcxcc303.hdsl.runtime.NodeSource;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
import java.util.List;

/// The commands about the Node toolchain: which runtimes the machine and the launcher have, which
/// versions can be installed, and installing or removing one.
@NotNullByDefault
public final class NodeCommands {

	private NodeCommands() {
	}

	/// Runs the `LIST_RUNTIMES` command.
	static int listRuntimes(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		List<NodeRuntime> runtimes = NodeRuntimeManager.listInstalled();
		if (runtimes.isEmpty()) {
			out.println("(no managed Node runtimes)");
		}
		for (NodeRuntime runtime : runtimes) {
			out.println(runtime.version() + "\t" + runtime.directory());
		}
		return 0;
	}

	/// Runs the `LIST_NODE_VERSIONS` command.
	static int listNodeVersions(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		for (NodeRelease release : NodeRuntimeManager.fetchReleases(NodeSource.of(invocation.options()
		                                                                                    .get("node-source")))) {
			out.println(release.version() + (release.isLts() ? "\t" + release.label() : "") +
			            (release.isSupported() ? "" : "\tunsupported"));
		}
		return 0;
	}

	/// Runs the `INSTALL_NODE` command.
	static int installNode(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		out.println("Installing Node.js " + invocation.subject() + " ...");
		NodeRuntime runtime = NodeRuntimeManager.install(invocation.subject(),
		                                                 NodeSource.of(invocation.options().get("node-source")),
		                                                 out::println);
		out.println("Installed Node.js " + runtime.version() + " into " + runtime.directory());
		return 0;
	}

	/// Runs the `UNINSTALL_NODE` command.
	static int uninstallNode(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		NodeRuntimeManager.uninstall(invocation.subject());
		out.println("Removed Node.js " + invocation.subject());
		return 0;
	}
}
