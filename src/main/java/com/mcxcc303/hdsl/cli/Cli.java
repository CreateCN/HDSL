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

import com.mcxcc303.hdsl.pack.ModpackInstaller;
import com.mcxcc303.hdsl.pack.PackExporter;
import com.mcxcc303.hdsl.pack.PackManifest;

import com.mcxcc303.hdsl.instance.InstanceListing;
import com.mcxcc303.hdsl.instance.InstanceStore;

import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.harness.ReleaseQueries;

import com.mcxcc303.hdsl.plugin.CatalogSources;

import com.mcxcc303.hdsl.launch.DshAcpClient;
import com.mcxcc303.hdsl.plugin.DshBuildScripts;
import com.mcxcc303.hdsl.harness.DshDoctor;
import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.instance.DshHomeMode;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.instance.DshInstanceManager;
import com.mcxcc303.hdsl.launch.DshLaunchArguments;
import com.mcxcc303.hdsl.launch.DshLauncher;
import com.mcxcc303.hdsl.plugin.DshLocalPlugins;
import com.mcxcc303.hdsl.pack.DshPackForge;
import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import com.mcxcc303.hdsl.harness.DshPreset;
import com.mcxcc303.hdsl.launch.DshProcess;
import com.mcxcc303.hdsl.launch.DshProcessManager;
import com.mcxcc303.hdsl.harness.DshRelease;
import com.mcxcc303.hdsl.launch.DshSession;
import com.mcxcc303.hdsl.pack.DshSessionPacks;
import com.mcxcc303.hdsl.launch.DshSessions;
import com.mcxcc303.hdsl.runtime.NodeRelease;
import com.mcxcc303.hdsl.runtime.NodeRuntime;
import com.mcxcc303.hdsl.runtime.NodeRuntimeManager;
import com.mcxcc303.hdsl.runtime.NodeSource;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// A small headless command surface for version and instance management.
///
/// This exists so that the launcher's core can be driven, scripted and verified
/// without a display. The GUI is a second consumer of exactly the same
/// [HarnessInstaller] and [DshInstanceManager] calls.
@NotNullByDefault
public final class Cli {
    private Cli() {
    }

    /// The commands understood by the launcher.
    ///
    /// A command either works on something named on the command line or reports
    /// on the launcher itself; the second kind takes no argument and is marked
    /// as such, so adding one cannot be forgotten in a check elsewhere.
    public enum Command {
        /// Prints a full diagnostics report.
        DOCTOR(true),
        /// Prints the installed versions.
        LIST_INSTALLED(true),
        /// Prints the versions published to the npm registry.
        LIST_REMOTE(true),
        /// Installs one version.
        INSTALL(false),
        /// Removes one version.
        UNINSTALL(false),
        /// Prints the instances.
        LIST_INSTANCES(true),
        /// Creates an instance.
        CREATE_INSTANCE(false),
        /// Removes an instance.
        DELETE_INSTANCE(false),
        /// Launches an instance and blocks until it exits.
        LAUNCH(false),
        /// Prints the instances that are currently running.
        LIST_RUNNING(true),
        /// Stops a running instance.
        STOP(false),
        /// Lists the Node runtimes the launcher installed.
        LIST_RUNTIMES(true),
        PRINT_LAUNCH_ARGS(true),
        /// Lists the Node releases available for this platform.
        LIST_NODE_VERSIONS(true),
        /// Installs a Node runtime.
        INSTALL_NODE(false),
        /// Removes an installed Node runtime.
        UNINSTALL_NODE(false),
        /// Installs a plugin into an instance's profile.
        INSTALL_PLUGIN(false),

        /// Removes one or more plugins from an instance's profile.
        REMOVE_PLUGIN(false),

        /// Writes an instance's sessions into a pack.
        EXPORT_SESSIONS(false),

        /// Reads a pack of sessions into an instance.
        IMPORT_PACK(false),

        /// Installs a plugin from a file the user has.
        INSTALL_PLUGIN_FILE(false),

        /// Moves an instance to another harness version, and reinstalls its plugins.
        UPGRADE_INSTANCE(false),

        /// Lists or answers the install scripts a profile is waiting to be told about.
        BUILD_SCRIPTS(false),

        /// Writes an instance's configuration into a pack.
        EXPORT_MODPACK(false),

        /// Writes an instance as a DSH-PackForge pack.
        EXPORT_PACKFORGE(false),

        /// Builds an instance from a pack.
        INSTALL_MODPACK(false),

        /// Puts a pack's profile into an instance that exists.
        RESTORE_PROFILE(false),
        /// Sends one prompt over the Agent Client Protocol and prints the reply.
        ACP_PROMPT(false),
        /// Prints the sessions of an instance.
        LIST_SESSIONS(false),
        /// Moves a session to another instance.
        MIGRATE_SESSION(false),
        /// Launches an instance and prints its output.
        TEST_LAUNCH(false),
        /// Copies sessions from another home into an instance.
        IMPORT_SESSIONS(false),
        /// Prints the folders the launcher looks for instances in.
        LIST_DIRECTORIES(true),
        /// Adds a folder to that list.
        ADD_DIRECTORY(false),

        /// Prints the places the plugin catalogue is read from.
        LIST_PLUGIN_SOURCES(true),

        /// Adds a place for the plugin catalogue to be read from.
        ADD_PLUGIN_SOURCE(false),

        /// Removes one, and puts the published pair back when the last is gone.
        REMOVE_PLUGIN_SOURCE(false)
        ;

        /// Whether this command reports on the launcher itself rather than on something named.
        private final boolean acceptsNoSubject;

        Command(boolean acceptsNoSubject) {
            this.acceptsNoSubject = acceptsNoSubject;
        }

        /// Reports whether this command works without a named subject.
        ///
        /// @return whether the command takes no argument
        public boolean acceptsNoSubject() {
            return acceptsNoSubject;
        }
    }

    /// A parsed command line.
    ///
    /// @param command    the requested command
    /// @param arguments  positional arguments, the first being the subject
    /// @param options    the `--key value` options
    /// @param showHelp   whether help was requested
    public record Invocation(Command command, List<String> arguments,
                             Map<String, String> options, boolean showHelp) {
        /// Returns the first positional argument.
        ///
        /// @return the subject, or `null` when none was given
        public @Nullable String subject() {
            return arguments.isEmpty() ? null : arguments.get(0);
        }

        /// Returns an option value.
        ///
        /// @param name the option name without leading dashes
        /// @return the value, or `null` when the option was not given
        public @Nullable String option(String name) {
            return options.get(name);
        }
    }

    /// Parses launcher arguments into an invocation.
    ///
    /// @param args the raw command-line arguments
    /// @return the parsed invocation, or `null` when the arguments request the GUI
    public static @Nullable Invocation parse(List<String> args) {
        if (!args.contains("--doctor")
                && !args.contains("--list-installed")
                && !args.contains("--list-versions")
                && !args.contains("--list-instances")
                && !args.contains("--install")
                && !args.contains("--uninstall")
                && !args.contains("--create-instance")
                && !args.contains("--delete-instance")
                && !args.contains("--launch")
                && !args.contains("--list-running")
                && !args.contains("--stop")
                && !args.contains("--list-runtimes")
                && !args.contains("--print-launch-args")
                && !args.contains("--list-node-versions")
                && !args.contains("--install-node")
                && !args.contains("--uninstall-node")
                && !args.contains("--install-plugin")
                && !args.contains("--remove-plugin")
                && !args.contains("--export-sessions")
                && !args.contains("--export-modpack")
                && !args.contains("--export-packforge")
                && !args.contains("--upgrade-instance")
                && !args.contains("--build-scripts")
                && !args.contains("--install-plugin-file")
                && !args.contains("--install-modpack")
                && !args.contains("--restore-profile")
                && !args.contains("--import-pack")
                && !args.contains("--acp-prompt")
                && !args.contains("--list-sessions")
                && !args.contains("--migrate-session")
                && !args.contains("--test-launch")
                && !args.contains("--import-sessions")
                && !args.contains("--list-directories")
                && !args.contains("--add-directory")
                && !args.contains("--list-plugin-sources")
                && !args.contains("--add-plugin-source")
                && !args.contains("--remove-plugin-source")
                && !args.contains("--help")
                && !args.contains("-h")) {
            return null;
        }

        Command command = Command.DOCTOR;
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        boolean help = false;

        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            switch (token) {
                case "--doctor" -> command = Command.DOCTOR;
                case "--list-installed" -> command = Command.LIST_INSTALLED;
                case "--list-versions" -> command = Command.LIST_REMOTE;
                case "--list-instances" -> command = Command.LIST_INSTANCES;
                case "--help", "-h" -> help = true;
                case "--install" -> {
                    command = Command.INSTALL;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--uninstall" -> {
                    command = Command.UNINSTALL;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--create-instance" -> {
                    command = Command.CREATE_INSTANCE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--delete-instance" -> {
                    command = Command.DELETE_INSTANCE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--launch" -> {
                    command = Command.LAUNCH;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--list-running" -> command = Command.LIST_RUNNING;
                case "--list-runtimes" -> command = Command.LIST_RUNTIMES;
                case "--print-launch-args" -> command = Command.PRINT_LAUNCH_ARGS;
                case "--list-node-versions" -> command = Command.LIST_NODE_VERSIONS;
                case "--install-node" -> {
                    command = Command.INSTALL_NODE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--uninstall-node" -> {
                    command = Command.UNINSTALL_NODE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--list-directories" -> command = Command.LIST_DIRECTORIES;
                case "--add-directory" -> {
                    command = Command.ADD_DIRECTORY;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--list-plugin-sources" -> command = Command.LIST_PLUGIN_SOURCES;
                case "--add-plugin-source" -> {
                    command = Command.ADD_PLUGIN_SOURCE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--remove-plugin-source" -> {
                    command = Command.REMOVE_PLUGIN_SOURCE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--import-sessions" -> {
                    command = Command.IMPORT_SESSIONS;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--from" -> {
                    if (i + 1 < args.size()) options.put("from", args.get(++i));
                }
                case "--test-launch" -> {
                    command = Command.TEST_LAUNCH;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--list-sessions" -> {
                    command = Command.LIST_SESSIONS;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--migrate-session" -> {
                    command = Command.MIGRATE_SESSION;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--acp-prompt" -> {
                    command = Command.ACP_PROMPT;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--install-plugin" -> {
                    command = Command.INSTALL_PLUGIN;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--install-plugin-file" -> {
                    command = Command.INSTALL_PLUGIN_FILE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--upgrade-instance" -> {
                    command = Command.UPGRADE_INSTANCE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--build-scripts" -> {
                    command = Command.BUILD_SCRIPTS;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--allow" -> positional.add("--allow");
                case "--deny" -> positional.add("--deny");
                case "--export-packforge" -> {
                    command = Command.EXPORT_PACKFORGE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--export-modpack" -> {
                    command = Command.EXPORT_MODPACK;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--with-sessions" -> positional.add("--with-sessions");
                case "--install-modpack" -> {
                    command = Command.INSTALL_MODPACK;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--restore-profile" -> {
                    command = Command.RESTORE_PROFILE;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--export-sessions" -> {
                    command = Command.EXPORT_SESSIONS;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--import-pack" -> {
                    command = Command.IMPORT_PACK;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                case "--remove-plugin" -> {
                    command = Command.REMOVE_PLUGIN;
                    // The instance, then every specification up to the next option.
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                    while (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                        positional.add(args.get(++i));
                    }
                }
                case "--stop" -> {
                    command = Command.STOP;
                    if (i + 1 < args.size()) positional.add(args.get(++i));
                }
                default -> {
                    if (token.startsWith("--")) {
                        String name = token.substring(2);
                        if (i + 1 < args.size() && !args.get(i + 1).startsWith("--")) {
                            options.put(name, args.get(++i));
                        } else {
                            options.put(name, "true");
                        }
                    } else {
                        positional.add(token);
                    }
                }
            }
        }

        return new Invocation(command, List.copyOf(positional), Map.copyOf(options), help);
    }

    /// Runs a parsed command.
    ///
    /// @param invocation the command to run
    /// @param out        the stream for normal output
    /// @param err        the stream for error output
    /// @return the process exit code
    public static int run(Invocation invocation, PrintStream out, PrintStream err) {
        // Whether a command needs an argument is a property of the command, not
        // a list kept here: the list was a growing whitelist that silently
        // rejected every new command added without it.
        if (invocation.showHelp()
                || (!invocation.command().acceptsNoSubject() && invocation.subject() == null)) {
            printUsage(out);
            return invocation.showHelp() ? 0 : 1;
        }

        try {
            switch (invocation.command()) {
                case DOCTOR -> {
                    return DshDoctor.report(out);
                }
                case LIST_INSTALLED -> {
                    // A runtime belongs to the instance that runs it, so what
                    // there is to list is the instances and their runtimes.
                    List<DshInstance> instances = InstanceListing.list();
                    if (instances.isEmpty()) {
                        out.println("(no instances)");
                    }
                    for (DshInstance instance : instances) {
                        out.println(instance.id() + "\t" + instance.version()
                                + "\t" + instance.dshDirectory()
                                + (HarnessInstaller.isInstalled(instance) ? "" : "\t[INCOMPLETE]"));
                    }
                    return 0;
                }
                case LIST_REMOTE -> {
                    for (DshRelease release : ReleaseQueries.fetchReleases()) {
                        String tag = release.primaryTag();
                        out.println(release.version()
                                + "\t" + release.type().id()
                                + "\t" + (release.publishedAt() == null ? "-" : release.publishedAt())
                                + (tag == null ? "" : "\t" + tag));
                    }
                    return 0;
                }
                case INSTALL, UNINSTALL -> {
                    // A runtime is not installed on its own any more: it is
                    // installed into the instance that runs it, when that
                    // instance is made.
                    err.println("error: DeepSeek Harness is installed into an instance; "
                            + "use --create-instance with --dsh-version");
                    return 2;
                }
                case LIST_INSTANCES -> {
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
                case CREATE_INSTANCE -> {
                    return InstanceCommands.createInstance(invocation, out, err);
                }
                case DELETE_INSTANCE -> {
                    DshInstanceManager.delete(invocation.subject());
                    out.println("Removed instance " + invocation.subject());
                    return 0;
                }
                case LAUNCH -> {
                    return InstanceCommands.launchInstance(invocation, out, err);
                }
                case LIST_RUNNING -> {
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
                case PRINT_LAUNCH_ARGS -> {
                    return InstanceCommands.printLaunchArguments(invocation, out, err);
                }
                case LIST_RUNTIMES -> {
                    List<NodeRuntime> runtimes = NodeRuntimeManager.listInstalled();
                    if (runtimes.isEmpty()) {
                        out.println("(no managed Node runtimes)");
                    }
                    for (NodeRuntime runtime : runtimes) {
                        out.println(runtime.version() + "\t" + runtime.directory());
                    }
                    return 0;
                }
                case LIST_NODE_VERSIONS -> {
                    for (NodeRelease release : NodeRuntimeManager.fetchReleases(
                            NodeSource.of(invocation.options().get("node-source")))) {
                        out.println(release.version()
                                + (release.isLts() ? "\t" + release.label() : "")
                                + (release.isSupported() ? "" : "\tunsupported"));
                    }
                    return 0;
                }
                case INSTALL_NODE -> {
                    out.println("Installing Node.js " + invocation.subject() + " ...");
                    NodeRuntime runtime = NodeRuntimeManager.install(invocation.subject(),
                            NodeSource.of(invocation.options().get("node-source")), out::println);
                    out.println("Installed Node.js " + runtime.version() + " into " + runtime.directory());
                    return 0;
                }
                case UNINSTALL_NODE -> {
                    NodeRuntimeManager.uninstall(invocation.subject());
                    out.println("Removed Node.js " + invocation.subject());
                    return 0;
                }
                case LIST_DIRECTORIES -> {
                    return DirectoryCommands.listDirectories(out);
                }
                case ADD_DIRECTORY -> {
                    return DirectoryCommands.addDirectory(invocation, out, err);
                }
                case LIST_PLUGIN_SOURCES -> {
                    return PluginSourceCommands.listPluginSources(out);
                }
                case ADD_PLUGIN_SOURCE -> {
                    return PluginSourceCommands.addPluginSource(invocation, out, err);
                }
                case REMOVE_PLUGIN_SOURCE -> {
                    return PluginSourceCommands.removePluginSource(invocation, out, err);
                }
                case IMPORT_SESSIONS -> {
                    return SessionCommands.importSessions(invocation, out, err);
                }
                case TEST_LAUNCH -> {
                    return InstanceCommands.testLaunch(invocation, out, err);
                }
                case LIST_SESSIONS -> {
                    return SessionCommands.listSessions(invocation, out, err);
                }
                case MIGRATE_SESSION -> {
                    return SessionCommands.migrateSession(invocation, out, err);
                }
                case ACP_PROMPT -> {
                    return SessionCommands.acpPrompt(invocation, out, err);
                }
                case INSTALL_PLUGIN -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --install-plugin needs an instance and a package spec");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    String spec = invocation.arguments().get(1);
                    out.println("Installing " + spec + " into " + instance.id() + " ...");
                    DshPluginInstaller.install(instance,
                            List.of(new DshPreset(spec, spec, spec, "", false, true)),
                            out::println);
                    out.println("Installed " + spec);
                    for (String bundle : DshPluginInstaller.readBundles(instance.homeDirectory(), instance.profile())) {
                        out.println("  bundle: " + bundle);
                    }
                    return 0;
                }
                case BUILD_SCRIPTS -> {
                    if (invocation.arguments().isEmpty()) {
                        err.println("error: --build-scripts needs an instance");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }

                    List<String> flags = invocation.arguments().stream()
                            .filter(value -> value.equals("--allow") || value.equals("--deny")).toList();
                    List<String> names = invocation.arguments().stream()
                            .filter(value -> !value.equals(instance.id()) && !value.startsWith("--")).toList();

                    if (flags.isEmpty()) {
                        for (DshBuildScripts.Pending entry : DshBuildScripts.pending(instance)) {
                            out.println(entry.name() + ": " + (entry.allowed() == null
                                    ? "waiting for an answer" : entry.allowed()));
                        }
                        return 0;
                    }

                    boolean allow = flags.contains("--allow");
                    List<String> targets = names.isEmpty() ? DshBuildScripts.unanswered(instance) : names;
                    int written = DshBuildScripts.answer(instance, targets, allow);
                    out.println((allow ? "Allowed" : "Refused") + " install scripts for " + written
                            + " package(s) in " + instance.id());
                    return 0;
                }
                case UPGRADE_INSTANCE -> {
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
                case INSTALL_PLUGIN_FILE -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --install-plugin-file needs an instance and a plugin file");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    java.nio.file.Path file = java.nio.file.Path.of(invocation.arguments().get(1));
                    DshLocalPlugins.Result result = DshLocalPlugins.install(instance, file, out::println);
                    out.println("Installed " + result.pkg().name() + " " + result.pkg().version()
                            + " from the instance's own copy at " + result.installed());
                    out.println("  bundle patch: " + (result.pkg().isBundle() ? result.pkg().bundlePatch() : "(none)"));
                    for (String bundle : DshPluginInstaller.readBundles(instance.homeDirectory(), instance.profile())) {
                        out.println("  bundle: " + bundle);
                    }
                    return 0;
                }
                case EXPORT_PACKFORGE -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --export-packforge needs an instance and a file to write");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    java.nio.file.Path target = java.nio.file.Path.of(invocation.arguments().get(1));
                    DshPackForge.Options options = new DshPackForge.Options(
                            DshPackForge.Options.kebab(instance.id()), "1.0.0", instance.id(), "", "");
                    DshPackForge.Result exported = DshPackForge.export(instance, target, options, out::println);
                    out.println("Wrote " + exported.files() + " file(s), left out " + exported.excluded()
                            + ", sha256 " + exported.sha256());
                    return 0;
                }
                case EXPORT_MODPACK -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --export-modpack needs an instance and a file to write");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    java.nio.file.Path target = java.nio.file.Path.of(invocation.arguments().get(1));
                    boolean withSessions = invocation.arguments().contains("--with-sessions");
                    // Everything a pack carries by default is what a person asked for by not being
                    // asked: the plugins' own settings travel, because a pack that named a sidebar but
                    // not the stylesheet somebody configured it with is the difference nobody notices
                    // until they open it. The conversations are the one thing that is opt-in, because
                    // they are the one thing that is somebody's own.
                    PackManifest.Options defaults = PackManifest.Options.of(instance);
                    PackManifest.Options options = new PackManifest.Options(instance.id(), "1.0", "", "", "", "",
                            withSessions, java.util.Set.of(), defaults.settings(), defaults.skills());
                    PackExporter.ExportResult exported = PackExporter.export(instance, target, options, out::println);
                    out.println("Wrote " + exported.plugins() + " plugin(s), " + exported.bytes() + " byte(s)");
                    return 0;
                }
                case INSTALL_MODPACK -> {
                    if (invocation.arguments().isEmpty()) {
                        err.println("error: --install-modpack needs a pack to read and an instance id");
                        return 1;
                    }
                    java.nio.file.Path pack = java.nio.file.Path.of(invocation.arguments().get(0));
                    String id = invocation.arguments().size() > 1
                            ? invocation.arguments().get(1)
                            : PackManifest.readManifest(pack).instanceId();
                    ModpackInstaller.InstallResult result = ModpackInstaller.install(pack, id,
                            java.nio.file.Path.of(System.getProperty("user.home")), out::println);
                    out.println("Instance " + result.instance().id() + " is ready: "
                            + (result.installed() ? "harness installed, " : "harness already present, ")
                            + result.plugins() + " plugin(s), " + result.bundles() + " bundle(s)");
                    return 0;
                }
                case RESTORE_PROFILE -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --restore-profile needs an instance and a pack");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    java.nio.file.Path pack = java.nio.file.Path.of(invocation.arguments().get(1));
                    PackManifest.Manifest manifest = PackManifest.readManifest(pack);
                    int plugins = ModpackInstaller.restoreProfile(instance, manifest, pack, out::println);
                    out.println("Restored " + plugins + " plugin(s) and " + manifest.bundles().size()
                            + " bundle(s) into " + instance.id());
                    for (String bundle : DshPluginInstaller.readBundles(instance.homeDirectory(), instance.profile())) {
                        out.println("  bundle: " + bundle);
                    }
                    return 0;
                }
                case EXPORT_SESSIONS -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --export-sessions needs an instance and a file to write");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    java.nio.file.Path target = java.nio.file.Path.of(invocation.arguments().get(1));
                    List<DshSession> sessions = DshSessions.list(instance.homeDirectory());
                    out.println("Writing " + sessions.size() + " session(s) to " + target + " ...");
                    DshSessionPacks.ExportResult exported = DshSessionPacks.export(instance, sessions, target, out::println);
                    out.println("Wrote " + exported.sessions() + " session(s), "
                            + exported.attachments() + " attachment(s), " + exported.bytes() + " byte(s)");
                    return 0;
                }
                case IMPORT_PACK -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --import-pack needs an instance and a pack to read");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    java.nio.file.Path pack = java.nio.file.Path.of(invocation.arguments().get(1));
                    DshSessionPacks.ImportResult imported =
                            DshSessionPacks.importFrom(instance.homeDirectory(), pack, out::println);
                    out.println("Imported " + imported.imported() + ", skipped " + imported.skipped()
                            + ", attachments " + imported.attachments());
                    for (DshSession session : DshSessions.list(instance.homeDirectory())) {
                        out.println("  " + session.id() + "  " + session.workspaceSlug()
                                + "  " + (session.title() == null ? "(no title)" : session.title()));
                    }
                    return 0;
                }
                case REMOVE_PLUGIN -> {
                    if (invocation.arguments().size() < 2) {
                        err.println("error: --remove-plugin needs an instance and at least one package spec");
                        return 1;
                    }
                    DshInstance instance = InstanceStore.find(invocation.arguments().get(0));
                    if (instance == null) {
                        err.println("error: instance " + invocation.arguments().get(0) + " does not exist");
                        return 1;
                    }
                    List<String> specs = List.copyOf(invocation.arguments().subList(1, invocation.arguments().size()));
                    out.println("Removing " + String.join(", ", specs) + " from " + instance.id() + " ...");
                    DshPluginInstaller.removeSpecs(instance, specs, out::println);
                    out.println("Removed " + specs.size() + " package(s)");
                    for (String bundle : DshPluginInstaller.readBundles(instance.homeDirectory(), instance.profile())) {
                        out.println("  bundle: " + bundle);
                    }
                    return 0;
                }
                case STOP -> {
                    out.println(DshProcessManager.stop(invocation.subject())
                            ? "Stopped " + invocation.subject()
                            : "Instance " + invocation.subject() + " is not running");
                    return 0;
                }
                default -> {
                    err.println("Unhandled command: " + invocation.command());
                    return 2;
                }
            }
        } catch (DshException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }

    /// Prints the command list.
    ///
    /// @param out the stream to print to
    private static void printUsage(PrintStream out) {
        out.println("""
                HDSL command line

                usage: hdsl <command> [options]

                versions:
                  --list-installed                 list instances and the runtime each carries
                  --list-versions                  list versions published to the npm registry

                instances:
                  --list-instances                 list launcher instances
                  --create-instance <id>           create an instance
                      --dsh-version <version>        version to pin (required)
                      --profile <name>               profile to boot (default: web)
                      --workspace <path>             session working directory (default: $HOME)
                      --home-mode <mode>             isolated | version_shared | custom
                      --home <path>                  required for --home-mode custom
                      --runtime <version>            Node runtime to pin (default: system)
                  --delete-instance <id>           remove an instance

                sessions:
                  --acp-prompt <id> <text>         send one prompt over ACP
                  --list-sessions <id>             list an instance's sessions
                  --list-directories               the folders instances are looked for in
                  --add-directory <path>            add one, scanning it for instances
                  --import-sessions <id> [--from <home>]
                                                   copy sessions in from another home
                  --test-launch <id>               launch and print its output
                  --migrate-session <src> <sid> <dst>
                                                   move a session to another instance

                plugins:
                  --list-plugin-sources           the places the plugin catalogue is read from
                  --add-plugin-source <address>   add one: an address or an npm package name
                  --remove-plugin-source <address>  remove one
                  --install-plugin <id> <spec>     install a plugin into an instance profile
                  --remove-plugin <id> <spec>...   remove one or more plugins from an instance profile
                  --export-sessions <id> <file>    write the instance's sessions into a pack
                  --import-pack <id> <file>        read a pack of sessions into an instance
                  --build-scripts <id> [--allow|--deny] [pkg...]  read or answer what may build
                  --upgrade-instance <id> <version>  move an instance to another harness version
                  --install-plugin-file <id> <file>  install a plugin from a packed file
                  --export-modpack <id> <file>     write an instance's configuration into a pack
                  --export-packforge <id> <file>   write an instance as a DSH-PackForge pack
                      --with-sessions                also carry the instance's conversations
                  --install-modpack <file> [<id>]  build an instance from a pack
                  --restore-profile <id> <file>    put a pack's profile into an existing instance

                node runtimes:
                  --list-runtimes                  list installed Node runtimes
                  --list-node-versions             list Node releases for this platform
                  --install-node <version>         install a Node runtime
                  --uninstall-node <version>       remove a Node runtime

                running:
                  --launch <id>                    start an instance and block until it exits
                  --list-running                   list running instances
                  --stop <id>                      stop a running instance

                diagnostics:
                  --doctor                         print a full diagnostics report
                  --version                        print the launcher version
                """);
    }
}
