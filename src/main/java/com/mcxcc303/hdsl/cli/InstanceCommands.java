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
import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.instance.*;
import com.mcxcc303.hdsl.launch.DshLaunchArguments;
import com.mcxcc303.hdsl.launch.DshLauncher;
import com.mcxcc303.hdsl.launch.DshProcess;
import com.mcxcc303.hdsl.launch.DshProcessManager;
import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/// The commands that work one instance: creating it, launching it, printing the command line a
/// launch would use, and starting it once to see what it says.
@NotNullByDefault
public final class InstanceCommands {
	private InstanceCommands() {
	}

	/// Prints the address a running instance serves, which is what `instance launch --url` asks for.
	///
	/// The address is the one the harness printed when it came up, and it carries the key in its
	/// query: that is what makes it usable as it stands, by a person opening it and by a script
	/// calling it.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int getUrl(Invocation invocation, PrintStream out, PrintStream err) {
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: there is no instance called " + id);
			return 1;
		}
		Optional<URI> address = servedAddress(instance);
		if (address.isEmpty()) {
			err.println("error: " + id + " is not running, or its harness has not said where it serves");
			return 1;
		}
		out.println(address.get());
		return 0;
	}

	/// Prints the key a running instance asks for.
	///
	/// The key is the \`token\` of the address above, alone: it is what another client needs when the
	/// address is already known, and it is the half of that address a person should not paste into a
	/// chat by accident.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int getKey(Invocation invocation, PrintStream out, PrintStream err) {
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: there is no instance called " + id);
			return 1;
		}
		Optional<String> key = servedAddress(instance).flatMap(DshPorts::launchToken);
		if (key.isEmpty()) {
			err.println("error: " + id + " is not running, or its harness printed no key");
			return 1;
		}
		out.println(key.get());
		return 0;
	}

	/// Returns the address the instance's harness printed, or empty when it has printed none.
	///
	/// The address is read from wherever it is known: the handle this launcher holds, or — when the
	/// instance was started by another launcher, which is the case a command line run from a
	/// terminal is in — the record that launcher left.
	///
	/// @param instance the instance
	/// @return the address
	private static Optional<URI> servedAddress(DshInstance instance) {
		return DshProcessManager.findRunning(instance).flatMap(DshProcessManager.Running::webUrl);
	}

	/// Runs the `instance create` command.
	///
	/// The version is required and there is nothing to fall back on: a runtime belongs to the
	/// instance that runs it, and it is fetched when that instance is made.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int createInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		String id = invocation.option("id");
		String version = invocation.option("dsh-version");
		String profile = invocation.option("profile");
		if (profile == null) {
			profile = DshInstance.DEFAULT_PROFILE;
		}

		String workspaceOption = invocation.option("workspace");
		Path workspace = Path.of(workspaceOption != null ? workspaceOption : System.getProperty("user.home"))
		                     .toAbsolutePath()
		                     .normalize();

		DshHomeMode mode = DshHomeMode.ISOLATED;
		String modeOption = invocation.option("home-mode");
		if (modeOption != null) {
			mode = DshHomeMode.valueOf(modeOption.toUpperCase(Locale.ROOT));
		}
		String nodeRuntime = invocation.option("runtime");
		Path customHome = invocation.option("home") == null
		                  ? null
		                  : Path.of(invocation.option("home")).toAbsolutePath().normalize();

		DshInstance instance = DshInstanceManager.create(id,
		                                                 version,
		                                                 profile,
		                                                 workspace,
		                                                 nodeRuntime,
		                                                 mode,
		                                                 customHome,
		                                                 List.of(),
		                                                 Map.of());
		out.println("Created instance " + instance.id() + " (dsh " + instance.version() + ", profile " +
		            instance.profile() + ", home " + instance.homeDirectory() + ")");
		return 0;
	}

	/// Runs the `instance launch` command: starts an instance and blocks until it exits.
	///
	/// With `--url` or `--key` nothing is started: what the harness printed when it came up is
	/// printed instead, which is the answer a script wants and the one a person copying an address
	/// into a browser wants.
	///
	/// The launcher keeps child processes in its own process group: the JVM
	/// shutdown hook stops them, so reattaching or interrupting this command
	/// never leaves an orphaned `dsh` behind.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	static int launchInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		if (invocation.flag("url") && invocation.flag("key")) {
			err.println("error: instance launch prints the address or the key, and was asked for both");
			return 2;
		}
		if (invocation.flag("url")) {
			return getUrl(invocation, out, err);
		}
		if (invocation.flag("key")) {
			return getKey(invocation, out, err);
		}
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: instance " + id + " does not exist");
			return 1;
		}
		if (RunningInstances.isUp(instance, err)) {
			return 1;
		}

		// What is about to run, before it runs: the argument list is assembled from the version's
		// own help, the instance's settings and the launcher's defaults, and when a launch goes
		// wrong the first question is which of those decided the flags. Reading it off the process
		// table afterwards is not always possible.
		DshLauncher.LaunchPlan plan = DshLauncher.plan(instance);
		out.println("command: " + plan.commandLine());

		DshProcess process = DshProcessManager.launch(instance, null, plan);
		process.setLogSink(line -> System.out.println("[" + instance.id() + "] " + line));

		long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();
		while (process.state() == DshProcess.State.STARTING && process.isRunning() &&
		       System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		if (process.webUrl().isPresent()) {
			out.println("ready: " + process.webUrl().get());
		} else {
			out.println("state: " + process.state() + " (exit " + process.exitCode().orElse(-1) + ")");
		}
		out.println("Press Ctrl-C to stop.");

		while (process.isRunning()) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		process.stop();
		return process.exitCode().orElse(0);
	}

	/// Launches an instance and prints the output it produces.
	///
	/// This is the headless half of test launch: it runs the same path the
	/// interface does and drains the same buffer the log window is given, so the
	/// capture can be checked without a display.
	///
	/// Runs the `instance print-launch-args` command: what a typed argument line comes to, without
	/// launching anything.
	///
	/// The split between the launcher's flags and the app's, the profile a line names, and the
	/// arguments that are refused are all decided by reading the line, so they can be shown without
	/// starting a process. That makes the rules checkable, which matters because a wrong split
	/// produces a command that either fails to start or starts the wrong thing.
	///
	/// @param invocation the parsed invocation
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the exit code
	static int printLaunchArguments(Invocation invocation, PrintStream out, PrintStream err) {
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: instance " + id + " does not exist");
			return 1;
		}
		if (RunningInstances.isUp(instance, err)) {
			return 1;
		}
		DshLaunchArguments.Parsed parsed = DshLaunchArguments.parseArguments(instance.extraArguments());
		out.println("instance   : " + instance.id() + " (profile " + instance.profile() + ")");
		out.println("typed      : " + instance.extraArguments());
		out.println("launcher   : " + parsed.launcherArguments());
		out.println("app        : " + parsed.appArguments());
		out.println("profile    : " + (parsed.profile() != null ? parsed.profile() : instance.profile()));
		out.println("no-open    : " + parsed.asksNoOpen());
		for (String refusal : parsed.refusals()) {
			out.println("refused    : " + refusal);
		}
		return 0;
	}

	/// Runs the `instance test-launch` command.
	///
	/// An instance that is up is not launched a second time: this would be that second harness on
	/// the same home, which is what the launcher keeps one process per instance to avoid.
	static int testLaunch(Invocation invocation, PrintStream out, PrintStream err) {
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: instance " + id + " does not exist");
			return 1;
		}
		if (RunningInstances.isUp(instance, err)) {
			return 1;
		}
		try {
			// What is about to run, before it runs: the argument list is assembled from the
			// version's own help, the instance's settings and the launcher's defaults, and when a
			// launch goes wrong the first question is which of those decided the flags. Reading it
			// off the process table afterwards is not always possible — the child exits, or the
			// tooling cannot see another process's arguments.
			// Built once and handed on. Building it is what writes the account's overlay, so building
			// a second one — which is what printing and then launching used to do — leaves the first
			// one behind for good.
			DshLauncher.LaunchPlan plan = DshLauncher.plan(instance);
			out.println("command: " + plan.commandLine());

			DshProcess process = DshProcessManager.launch(instance, null, plan);
			for (int i = 0; i < 120 && process.state() == DshProcess.State.STARTING; i++) {
				Thread.sleep(500);
				if (!process.isRunning()) {
					break;
				}
			}
			out.println("state: " + process.state());
			out.println("captured " + process.windowLogs().size() + " log line(s)");
			for (var line : process.windowLogs()) {
				out.println("  [" + line.getLevel() + "] " + line.getLog());
			}
			DshProcessManager.stop(instance.id());
			return 0;
		} catch (DshException e) {
			err.println("error: " + e.getMessage());
			return 1;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return 1;
		}
	}

	/// Runs the `instance list` command, which lists the instances, or the instances that are
	/// running when `--running` is given.
	static int listInstances(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		if (invocation.flag("running")) {
			return listRunning(out);
		}
		List<DshInstance> instances = InstanceListing.list();
		if (instances.isEmpty()) {
			out.println("(no instances)");
		}
		for (DshInstance instance : instances) {
			// The resolved values are printed, not the stored ones:
			// an instance that follows the launcher stores "global"
			// or nothing, and the answer a reader wants is what it
			// will actually run with.
			out.println(instance.id() + "\tdsh " + instance.version() + "\tprofile " + instance.profile() + "\tnode " +
			            instance.nodeRuntimeOrDefault() + "\t" + instance.homeMode() + "\thome " +
			            instance.homeDirectory() + "\tworkspace " + instance.workspacePath());
		}
		return 0;
	}

	/// Runs the `instance delete` command.
	///
	/// An instance that is up is not removed: the harness is still writing to the home that would be
	/// deleted. Stopping it first is one command, and its name is part of the refusal.
	static int deleteInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: instance " + id + " does not exist");
			return 1;
		}
		if (RunningInstances.isUp(instance, err)) {
			return 1;
		}
		DshInstanceManager.delete(id);
		out.println("Removed instance " + id);
		return 0;
	}

	/// Lists the instances that are up, which is what `instance list --running` asks for.
	///
	/// Whoever started them: an instance another launcher is running is up as surely as one of ours,
	/// and the pid is printed with it because that is the thing a reader can act on from here.
	private static int listRunning(PrintStream out) {
		List<DshProcessManager.Running> running = DshProcessManager.running();
		if (running.isEmpty()) {
			out.println("(nothing running)");
		}
		for (DshProcessManager.Running instance : running) {
			out.println(instance.instanceId() + "\t" + DshProcessManager.LaunchState.RUNNING + "\t" +
			            instance.surface() + "\t" + instance.webUrl().map(Object::toString).orElse("-") +
			            "\tup " + instance.uptime().toSeconds() + "s\tpid " + instance.pid());
		}
		return 0;
	}

	/// Runs the `instance upgrade` command.
	static int upgradeInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		String id = invocation.option("id");
		DshInstance instance = InstanceStore.find(id);
		if (instance == null) {
			err.println("error: instance " + id + " does not exist");
			return 1;
		}
		if (RunningInstances.isUp(instance, err)) {
			return 1;
		}
		String version = invocation.option("dsh-version");

		// What the profile holds, read before the install rewrites the
		// manifest it is read from.
		Map<String, String> dependencies = DshPluginInstaller.readDependencies(instance.homeDirectory(),
		                                                                       instance.profile());
		List<String> specs = new ArrayList<>();
		for (Map.Entry<String, String> entry : dependencies.entrySet()) {
			String declared = entry.getValue() == null ? "" : entry.getValue();
			boolean local = declared.startsWith("file:") || declared.startsWith("link:");
			specs.add(local || declared.isBlank() ? entry.getKey() : entry.getKey() + "@" + declared);
		}

		out.println("Moving " + instance.id() + " from " + instance.version() + " to " + version);
		HarnessInstaller.install(instance, version, out::println);
		// Rebuilt from the instance rather than from the fields the
		// command happens to care about: everything the upgrade does
		// not change — the folder it lives in, its icon, its port —
		// has to survive being recorded again.
		DshInstanceManager.update(instance.withVersion(version));

		if (!specs.isEmpty()) {
			out.println("Reinstalling " + specs.size() + " plugin(s)");
			DshInstance moved = InstanceStore.find(instance.id());
			DshPluginInstaller.installSpecs(moved, specs, out::println);
		}
		out.println("Instance " + instance.id() + " now records " + version);
		return 0;
	}

	/// Runs the `instance stop` command.
	static int stop(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
		String id = invocation.option("id");
		out.println(DshProcessManager.stop(id) ? "Stopped " + id : "Instance " + id + " is not running");
		return 0;
	}
}
