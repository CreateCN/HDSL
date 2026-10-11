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
import com.mcxcc303.hdsl.harness.DshRelease;
import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.harness.ReleaseQueries;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.instance.InstanceListing;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
import java.util.List;

/// The commands about the harness itself: the versions the instances run, and the versions there
/// are to run.
@NotNullByDefault
public final class HarnessCommands {

	private HarnessCommands() {
	}

	/// Runs the `harness list` command.
	///
	/// A runtime belongs to the instance that runs it, so what there is to list is the instances and
	/// the harness each of them carries.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int list(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		List<DshInstance> instances = InstanceListing.list();
		if (instances.isEmpty()) {
			out.println("(no instances)");
		}
		for (DshInstance instance : instances) {
			out.println(instance.id() + "\t" + instance.version() + "\t" + instance.dshDirectory() +
			            (HarnessInstaller.isInstalled(instance) ? "" : "\t[INCOMPLETE]"));
		}
		return 0;
	}

	/// Runs the `harness versions` command: the versions the registry publishes.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int versions(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		for (DshRelease release : ReleaseQueries.fetchReleases()) {
			String tag = release.primaryTag();
			out.println(release.version() + "\t" + release.type().id() + "\t" +
			            (release.publishedAt() == null ? "-" : release.publishedAt()) +
			            (tag == null ? "" : "\t" + tag));
		}
		return 0;
	}
}
