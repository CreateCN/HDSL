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

import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.harness.DshDoctor;
import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.instance.DshInstanceManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
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

	/// Parses launcher arguments into an invocation.
	///
	/// @param args the raw command-line arguments
	/// @return the parsed invocation, or `null` when the arguments request the GUI
	public static @Nullable Invocation parse(List<String> args) {
		if (!args.contains("--doctor") && !args.contains("--list-installed") && !args.contains("--list-versions") &&
		    !args.contains("--list-instances") && !args.contains("--install") && !args.contains("--uninstall") &&
		    !args.contains("--create-instance") && !args.contains("--delete-instance") && !args.contains("--launch") &&
		    !args.contains("--list-running") && !args.contains("--get-url") && !args.contains("--get-key") &&
		    !args.contains("--stop") && !args.contains("--list-runtimes") && !args.contains("--print-launch-args") &&
		    !args.contains("--list-node-versions") && !args.contains("--install-node") &&
		    !args.contains("--uninstall-node") && !args.contains("--install-plugin") &&
		    !args.contains("--remove-plugin") && !args.contains("--export-sessions") &&
		    !args.contains("--export-modpack") && !args.contains("--export-packforge") &&
		    !args.contains("--upgrade-instance") && !args.contains("--build-scripts") &&
		    !args.contains("--install-plugin-file") && !args.contains("--install-modpack") &&
		    !args.contains("--restore-profile") && !args.contains("--import-pack") && !args.contains("--acp-prompt") &&
		    !args.contains("--list-sessions") && !args.contains("--migrate-session") &&
		    !args.contains("--test-launch") && !args.contains("--import-sessions") &&
		    !args.contains("--list-directories") && !args.contains("--add-directory") &&
		    !args.contains("--list-plugin-sources") && !args.contains("--help") && !args.contains("-h")) {
			return null;
		}

		// The command line these arguments name under the spelling that replaces this one. Nothing
		// here reads it: the command is run from what the flag-style parse below makes of them, and
		// the line is only what the command says it was written as, once it has run.
		List<String> deprecated = FlagStyleCommands.commandLine(args);

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
				case "--get-url" -> command = Command.GET_URL;
				case "--get-key" -> command = Command.GET_KEY;
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

		return new Invocation(command, List.copyOf(positional), Map.copyOf(options), help, deprecated);
	}

	/// Runs a parsed command.
	///
	/// @param invocation the command to run
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	public static int run(Invocation invocation, PrintStream out, PrintStream err) {
		int code = runCommand(invocation, out, err);
		// Last, so that what a command said is what a reader sees first: the warning is about how
		// the command was written rather than about what it did.
		if (invocation.deprecated() != null) {
			FlagStyleCommands.warn(out, invocation.deprecated());
		}
		return code;
	}

	/// Runs the command an invocation names.
	///
	/// @param invocation the command to run
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	private static int runCommand(Invocation invocation, PrintStream out, PrintStream err) {
		// Whether a command needs an argument is a property of the command, not
		// a list kept here: the list was a growing whitelist that silently
		// rejected every new command added without it.
		if (invocation.showHelp() || (!invocation.command().acceptsNoSubject() && invocation.subject() == null)) {
			printUsage(out);
			return invocation.showHelp() ? 0 : 1;
		}

		try {
			switch (invocation.command()) {
				case DOCTOR -> {
					return DshDoctor.report(out);
				}
				case LIST_INSTALLED -> {
					return PluginCommands.listInstalled(invocation, out, err);
				}
				case LIST_REMOTE -> {
					return PluginCommands.listRemote(invocation, out, err);
				}
				case INSTALL, UNINSTALL -> {
					return PluginCommands.installOrUninstall(invocation, out, err);
				}
				case LIST_INSTANCES -> {
					return InstanceCommands.listInstances(invocation, out, err);
				}
				case CREATE_INSTANCE -> {
					return InstanceCommands.createInstance(invocation, out, err);
				}
				case DELETE_INSTANCE -> {
					return InstanceCommands.deleteInstance(invocation, out, err);
				}
				case LAUNCH -> {
					return InstanceCommands.launchInstance(invocation, out, err);
				}
				case GET_URL -> {
					return InstanceCommands.getUrl(invocation, out, err);
				}
				case GET_KEY -> {
					return InstanceCommands.getKey(invocation, out, err);
				}
				case LIST_RUNNING -> {
					return InstanceCommands.listRunning(invocation, out, err);
				}
				case PRINT_LAUNCH_ARGS -> {
					return InstanceCommands.printLaunchArguments(invocation, out, err);
				}
				case LIST_RUNTIMES -> {
					return NodeCommands.listRuntimes(invocation, out, err);
				}
				case LIST_NODE_VERSIONS -> {
					return NodeCommands.listNodeVersions(invocation, out, err);
				}
				case INSTALL_NODE -> {
					return NodeCommands.installNode(invocation, out, err);
				}
				case UNINSTALL_NODE -> {
					return NodeCommands.uninstallNode(invocation, out, err);
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
					return PluginCommands.installPlugin(invocation, out, err);
				}
				case BUILD_SCRIPTS -> {
					return PluginCommands.buildScripts(invocation, out, err);
				}
				case UPGRADE_INSTANCE -> {
					return InstanceCommands.upgradeInstance(invocation, out, err);
				}
				case INSTALL_PLUGIN_FILE -> {
					return PluginCommands.installPluginFile(invocation, out, err);
				}
				case EXPORT_PACKFORGE -> {
					return PackCommands.exportPackforge(invocation, out, err);
				}
				case EXPORT_MODPACK -> {
					return PackCommands.exportModpack(invocation, out, err);
				}
				case INSTALL_MODPACK -> {
					return PackCommands.installModpack(invocation, out, err);
				}
				case RESTORE_PROFILE -> {
					return PackCommands.restoreProfile(invocation, out, err);
				}
				case EXPORT_SESSIONS -> {
					return SessionCommands.exportSessions(invocation, out, err);
				}
				case IMPORT_PACK -> {
					return PackCommands.importPack(invocation, out, err);
				}
				case REMOVE_PLUGIN -> {
					return PluginCommands.removePlugin(invocation, out, err);
				}
				case STOP -> {
					return InstanceCommands.stop(invocation, out, err);
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
				              --get-url <id>                   print the address a running instance serves
				              --get-key <id>                   print the key a running instance asks for
				              --stop <id>                      stop a running instance
				            
				            diagnostics:
				              --doctor                         print a full diagnostics report
				              --version                        print the launcher version
				            """);
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
		LIST_RUNTIMES(true), PRINT_LAUNCH_ARGS(true),
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

		/// Prints the address a running instance serves.
		GET_URL(false),

		/// Prints the key a running instance asks for.
		GET_KEY(false);

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
	/// @param command   the requested command
	/// @param arguments positional arguments, the first being the subject
	/// @param options   the `--key value` options
	/// @param showHelp  whether help was requested
	/// @param deprecated the command line these arguments name under the spelling that replaces
	///                   this one, or `null` when this is that spelling already
	public record Invocation(Command command, List<String> arguments, Map<String, String> options, boolean showHelp,
	                         @Nullable List<String> deprecated) {
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
}
