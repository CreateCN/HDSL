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

import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.instance.InstanceListing;

import com.mcxcc303.hdsl.cli.Cli.Invocation;

import java.util.ArrayList;

import com.mcxcc303.hdsl.instance.InstanceStore;
import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.instance.DshHomeMode;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.instance.DshInstanceManager;
import com.mcxcc303.hdsl.launch.DshLaunchArguments;
import com.mcxcc303.hdsl.launch.DshLauncher;
import com.mcxcc303.hdsl.launch.DshProcess;
import com.mcxcc303.hdsl.launch.DshProcessManager;
import org.jetbrains.annotations.NotNullByDefault;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// The commands that work one instance: creating it, launching it, printing the command line a
/// launch would use, and starting it once to see what it says.
@NotNullByDefault
public final class InstanceCommands {
    /// Creates an instance from the command line.
    ///
    /// @param invocation the parsed invocation
    /// @param out        the stream for normal output
    /// @param err        the stream for error output
    /// @return the process exit code
    /// Prints the address a running instance serves.
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
        String id = invocation.subject();
        if (id == null || id.isBlank()) {
            err.println("error: --get-url needs an instance");
            return 1;
        }
        DshInstance instance = InstanceStore.find(id);
        if (instance == null) {
            err.println("error: there is no instance called " + id);
            return 1;
        }
        java.util.Optional<java.net.URI> address = servedAddress(instance);
        if (address.isEmpty()) {
            err.println("error: " + id
                    + " is not running, or its harness has not said where it serves");
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
        String id = invocation.subject();
        if (id == null || id.isBlank()) {
            err.println("error: --get-key needs an instance");
            return 1;
        }
        DshInstance instance = InstanceStore.find(id);
        if (instance == null) {
            err.println("error: there is no instance called " + id);
            return 1;
        }
        java.util.Optional<String> key = servedAddress(instance)
                .flatMap(com.mcxcc303.hdsl.instance.DshPorts::launchToken);
        if (key.isEmpty()) {
            err.println("error: " + id
                    + " is not running, or its harness printed no key");
            return 1;
        }
        out.println(key.get());
        return 0;
    }

    /// Returns the address the instance's harness printed, or empty when it has printed none.
    ///
    /// @param instance the instance
    /// @return the address
    private static java.util.Optional<java.net.URI> servedAddress(DshInstance instance) {
        return DshProcessManager.find(instance.id()).flatMap(DshProcess::webUrl);
    }

    static int createInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        String id = invocation.subject();
        String version = invocation.option("dsh-version");
        if (version == null) {
            // There is nothing installed to fall back on: the runtime is fetched
            // when the instance that runs it is made, so the version has to be
            // named here.
            err.println("error: --dsh-version is required; "
                    + "a DeepSeek Harness version belongs to the instance that runs it");
            return 1;
        }

        String profile = invocation.option("profile");
        if (profile == null) {
            profile = DshInstance.DEFAULT_PROFILE;
        }

        String workspaceOption = invocation.option("workspace");
        Path workspace = Path.of(workspaceOption != null ? workspaceOption : System.getProperty("user.home"))
                .toAbsolutePath().normalize();

        DshHomeMode mode = DshHomeMode.ISOLATED;
        String modeOption = invocation.option("home-mode");
        if (modeOption != null) {
            mode = DshHomeMode.valueOf(modeOption.toUpperCase(java.util.Locale.ROOT));
        }
        String nodeRuntime = invocation.option("runtime");
        Path customHome = invocation.option("home") == null
                ? null
                : Path.of(invocation.option("home")).toAbsolutePath().normalize();

        DshInstance instance = DshInstanceManager.create(id, version, profile, workspace,
                nodeRuntime, mode, customHome, List.of(), Map.of());
        out.println("Created instance " + instance.id() + " (dsh " + instance.version()
                + ", profile " + instance.profile() + ", home " + instance.homeDirectory() + ")");
        return 0;
    }

    /// Launches an instance and blocks until it exits.
    ///
    /// The launcher keeps child processes in its own process group: the JVM
    /// shutdown hook stops them, so reattaching or interrupting this command
    /// never leaves an orphaned `dsh` behind.
    ///
    /// @param invocation the parsed invocation
    /// @param out        the stream for normal output
    /// @param err        the stream for error output
    /// @return the process exit code
    static int launchInstance(Invocation invocation, PrintStream out, PrintStream err)
            throws DshException {
        DshInstance instance = InstanceStore.find(invocation.subject());
        if (instance == null) {
            err.println("error: instance " + invocation.subject() + " does not exist");
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

        long deadline = System.currentTimeMillis() + java.time.Duration.ofSeconds(90).toMillis();
        while (process.state() == DshProcess.State.STARTING
                && process.isRunning()
                && System.currentTimeMillis() < deadline) {
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
    /// @param invocation the parsed invocation
    /// @param out        the stream for normal output
    /// @param err        the stream for error output
    /// @return the process exit code
    /// Prints what a typed argument line comes to, without launching anything.
    ///
    /// The split between the launcher's flags and the app's, the profile a line names, and the
    /// arguments that are refused are all decided by reading the line, so they can be shown without
    /// starting a process. That makes the rules checkable, which matters because a wrong split
    /// produces a command that either fails to start or starts the wrong thing.
    ///
    /// @param invocation the parsed command line
    /// @param out        where the report goes
    /// @param err        where an error goes
    /// @return the exit code
    static int printLaunchArguments(Invocation invocation, PrintStream out, PrintStream err) {
        if (invocation.arguments().isEmpty()) {
            err.println("error: --print-launch-args needs an instance");
            return 1;
        }
        DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
        if (instance == null) {
            err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
            return 1;
        }
        DshLaunchArguments.Parsed parsed =
                DshLaunchArguments.parseArguments(instance.extraArguments());
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

    static int testLaunch(Invocation invocation, PrintStream out, PrintStream err) {
        if (invocation.arguments().isEmpty()) {
            err.println("error: --test-launch needs an instance");
            return 1;
        }
        DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
        if (instance == null) {
            err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
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


    /// Runs the `LIST_INSTANCES` command.
    static int listInstances(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        List<DshInstance> instances = InstanceListing.list();
        if (instances.isEmpty()) {
            out.println("(no instances)");
        }
        for (DshInstance instance : instances) {
            // The resolved values are printed, not the stored ones:
            // an instance that follows the launcher stores "global"
            // or nothing, and the answer a reader wants is what it
            // will actually run with.
            out.println(instance.id()
                    + "\tdsh " + instance.version()
                    + "\tprofile " + instance.profile()
                    + "\tnode " + instance.nodeRuntimeOrDefault()
                    + "\t" + instance.homeMode()
                    + "\thome " + instance.homeDirectory()
                    + "\tworkspace " + instance.workspacePath());
        }
        return 0;
    }

    /// Runs the `DELETE_INSTANCE` command.
    static int deleteInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        DshInstanceManager.delete(invocation.subject());
        out.println("Removed instance " + invocation.subject());
        return 0;
    }

    /// Runs the `LIST_RUNNING` command.
    static int listRunning(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        List<DshProcess> running = DshProcessManager.running();
        if (running.isEmpty()) {
            out.println("(nothing running)");
        }
        for (DshProcess process : running) {
            out.println(process.instance().id()
                    + "\t" + process.state()
                    + "\t" + process.plan().surface()
                    + "\t" + process.webUrl().map(Object::toString).orElse("-")
                    + "\tup " + process.uptime().toSeconds() + "s");
        }
        return 0;
    }

    /// Runs the `UPGRADE_INSTANCE` command.
    static int upgradeInstance(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        if (invocation.arguments().size() < 2) {
            err.println("error: --upgrade-instance needs an instance and a version");
            return 1;
        }
        DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
        if (instance == null) {
            err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
            return 1;
        }
        String version = invocation.arguments().get(1);

        // What the profile holds, read before the install rewrites the
        // manifest it is read from.
        Map<String, String> dependencies =
                DshPluginInstaller.readDependencies(instance.homeDirectory(), instance.profile());
        List<String> specs = new ArrayList<>();
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String declared = entry.getValue() == null ? "" : entry.getValue();
            boolean local = declared.startsWith("file:") || declared.startsWith("link:");
            specs.add(local || declared.isBlank()
                    ? entry.getKey() : entry.getKey() + "@" + declared);
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

    /// Runs the `STOP` command.
    static int stop(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        out.println(DshProcessManager.stop(invocation.subject())
                ? "Stopped " + invocation.subject()
                : "Instance " + invocation.subject() + " is not running");
        return 0;
    }

    private InstanceCommands() {
    }
}
