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

import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.launch.DshProcessManager;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
import java.util.Optional;

/// The question a command that changes an instance has to ask before it changes it.
///
/// A running harness is reading the very files these commands write: the profile pnpm installs into,
/// the home a pack restores, the runtime an upgrade replaces, the sessions an import adds to. The
/// launcher has kept one process per instance for exactly that reason — and what it could not do
/// until the record existed was see a process another launcher had started, so a command typed in a
/// terminal beside a running window went ahead and wrote under a live server.
@NotNullByDefault
final class RunningInstances {

	private RunningInstances() {
	}

	/// Tells the caller whether the instance is up, and what to do about it.
	///
	/// @param instance the instance
	/// @param err      the stream for error output
	/// @return whether it is running, in which case the reason has been printed
	static boolean isUp(DshInstance instance, PrintStream err) {
		Optional<DshProcessManager.Running> running = DshProcessManager.findRunning(instance);
		if (running.isEmpty()) {
			return false;
		}
		err.println("error: instance " + instance.id() + " is running (pid " + running.get().pid() +
		            "); stop it first: hdsl instance stop --id " + instance.id());
		return true;
	}
}
