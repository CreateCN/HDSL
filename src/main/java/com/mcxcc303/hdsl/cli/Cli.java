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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The command surface: what can be typed, what was typed, and running it.
///
/// This exists so that the launcher's core can be driven, scripted and verified without a display:
/// the interface is a second consumer of exactly the same [com.mcxcc303.hdsl.harness.HarnessInstaller]
/// and [com.mcxcc303.hdsl.instance.DshInstanceManager] calls.
///
/// A command line is a group, a command and parameters — `hdsl instance create --id mine
/// --dsh-version 0.2.0-rc.2`. [Commands] says what exists; this class reads a line into one of
/// those, prints what there is to type, and runs what was asked for. Nothing here names a command of
/// its own, which is what keeps the three in step: a command that is added to the table is one that
/// can be typed, explained and run in the same commit.
///
/// The flag-style spelling — `hdsl --create-instance mine --dsh-version 0.2.0-rc.2` — still runs and
/// still runs the same thing: [FlagStyleCommands] reads it into the command line it names, and the
/// command says what it is called now.
@NotNullByDefault
public final class Cli {

	/// The words that ask for help rather than name something.
	private static final Set<String> HELP = Set.of("--help", "-h");

	private Cli() {
	}

	/// Parses launcher arguments into an invocation.
	///
	/// @param args the raw command-line arguments
	/// @return the parsed invocation, or `null` when the arguments request the interface
	public static @Nullable Invocation parse(List<String> args) {
		// A flag-style command line names its command as surely as the current spelling does, so it is
		// read as that command: the line it means is worked out first and everything below reads it,
		// which is what keeps the two spellings from having one behaviour each. The line is carried on
		// the invocation, because the warning prints what was just run.
		List<String> deprecated = FlagStyleCommands.commandLine(args);
		if (deprecated != null) {
			return read(deprecated, deprecated);
		}

		if (args.isEmpty()) {
			return null;
		}
		if (HELP.contains(args.get(0))) {
			return new Invocation("", Map.of(), true, null, null);
		}
		if (!Commands.isGroup(args.get(0)) && Commands.find(args.get(0)) == null) {
			String gone = FlagStyleCommands.removed(args);
			if (gone != null) {
				// A flag whose command was removed outright. It is answered rather than left to the
				// interface, which would open a window over a word it does not know either.
				return new Invocation("", Map.of(), false, null, gone);
			}
			// A word this launcher does not answer to. What is left is the interface's, whose own
			// arguments — a folder to open, a file to install — are read there.
			return null;
		}
		return read(args, null);
	}

	/// Reads a command line into the command it names and the parameters it was given.
	///
	/// @param args       the words of the command line
	/// @param deprecated the flag-style line they came from, or `null` when they are the line
	/// @return the invocation, which carries what was wrong with the line when something was
	private static Invocation read(List<String> args, @Nullable List<String> deprecated) {
		String first = args.get(0);
		String spelling = first;
		int from = 1;
		if (Commands.find(first) == null && args.size() > 1 && !isHelp(args.get(1)) &&
		    !args.get(1).startsWith("-")) {
			spelling = first + " " + args.get(1);
			from = 2;
		}

		Commands.Command command = Commands.find(spelling);
		if (command == null) {
			String message = Commands.isGroup(spelling)
			                 ? spelling + " needs one of its commands"
			                 : "there is no command called '" + spelling + "'";
			return new Invocation(spelling, Map.of(), false, deprecated, message);
		}

		Map<String, List<String>> options = new LinkedHashMap<>();
		boolean help = false;

		for (int i = from; i < args.size(); i++) {
			String token = args.get(i);
			if (isHelp(token)) {
				help = true;
				continue;
			}
			if (!token.startsWith("--")) {
				return error(spelling, "takes options only, and '" + token + "' is not one", deprecated);
			}

			String given = token.substring(2);
			String value = null;
			int equals = given.indexOf('=');
			if (equals >= 0) {
				value = given.substring(equals + 1);
				given = given.substring(0, equals);
			}

			Commands.Parameter parameter = command.parameter(given);
			if (parameter == null) {
				return error(spelling, "does not take --" + given, deprecated);
			}
			if (parameter.kind() == Commands.Kind.FLAG) {
				if (value != null) {
					return error(spelling, "--" + given + " is a flag and takes no value", deprecated);
				}
				options.computeIfAbsent(given, name -> new ArrayList<>()).add("true");
				continue;
			}
			if (value == null) {
				if (i + 1 >= args.size()) {
					return error(spelling, "--" + given + " needs a value", deprecated);
				}
				value = args.get(++i);
			}

			List<String> values = options.computeIfAbsent(given, name -> new ArrayList<>());
			boolean many = parameter.kind() == Commands.Kind.REQUIRED_MANY ||
			               parameter.kind() == Commands.Kind.OPTIONAL_MANY;
			if (!values.isEmpty() && !many) {
				return error(spelling, "--" + given + " was given more than once", deprecated);
			}
			values.add(value);
		}

		if (help) {
			return new Invocation(spelling, Map.copyOf(options), true, deprecated, null);
		}
		List<String> missing = command.parameters()
		                              .stream()
		                              .filter(parameter -> parameter.kind() == Commands.Kind.REQUIRED ||
		                                                   parameter.kind() == Commands.Kind.REQUIRED_MANY)
		                              .filter(parameter -> !options.containsKey(parameter.name()))
		                              .map(parameter -> "--" + parameter.name())
		                              .toList();
		if (!missing.isEmpty()) {
			return error(spelling, "needs " + String.join(", ", missing), deprecated);
		}
		return new Invocation(spelling, Map.copyOf(options), false, deprecated, null);
	}

	/// Returns an invocation that says what was wrong with the line.
	private static Invocation error(String spelling, String message, @Nullable List<String> deprecated) {
		return new Invocation(spelling, Map.of(), false, deprecated, spelling + " " + message);
	}

	private static boolean isHelp(String token) {
		return HELP.contains(token);
	}

	/// Runs a parsed command.
	///
	/// @param invocation the command to run
	/// @param out        the stream for normal output
	/// @param err        the stream for error output
	/// @return the process exit code
	public static int run(Invocation invocation, PrintStream out, PrintStream err) {
		int code = runCommand(invocation, out, err);
		// Last, so that what a command said is what a reader sees first: the warning is about how the
		// command was written rather than about what it did.
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
		if (invocation.showHelp()) {
			help(invocation.command(), out);
			return 0;
		}

		Commands.Command command = Commands.find(invocation.command());
		if (command == null || invocation.error() != null) {
			if (invocation.error() != null) {
				err.println("error: " + invocation.error());
			}
			// What there is to type at the level the words reached: for a group that was not told
			// which of its commands to run that is the group, and for a word that named nothing it is
			// everything. A line that could not be read is not a success.
			help(invocation.command(), err);
			return 2;
		}

		try {
			return command.action().run(invocation, out, err);
		} catch (DshException e) {
			err.println("error: " + e.getMessage());
			return 1;
		}
	}

	/// Prints what there is to type: every command, the commands of one group, or one command's
	/// parameters.
	///
	/// @param spelling the words that were typed, which decide how much is printed
	/// @param out      the stream to print to
	private static void help(String spelling, PrintStream out) {
		Commands.Command command = Commands.find(spelling);
		if (command != null) {
			printCommand(command, out);
			return;
		}

		String group = Commands.isGroup(spelling) ? spelling : "";
		out.println("HDSL command line");
		out.println();
		out.println("usage: hdsl <group> <command> [options]");
		if (!group.isEmpty()) {
			printGroup(Commands.in(group), out);
			out.println();
			out.println("`hdsl " + group + " <command> --help` says what one command takes.");
			return;
		}
		for (String name : Commands.groups()) {
			printGroup(Commands.in(name), out);
		}
		printAlone(out);
		out.println();
		out.println("`hdsl <group> <command> --help` says what one command takes.");
	}

	/// Prints one group of commands.
	private static void printGroup(List<Commands.Command> commands, PrintStream out) {
		if (commands.isEmpty()) {
			return;
		}
		out.println();
		out.println(commands.get(0).group() + ":");
		int width = commands.stream().mapToInt(command -> command.name().length()).max().orElse(0);
		for (Commands.Command command : commands) {
			out.println("  " + pad(command.name(), width) + "  " + command.description());
		}
	}

	/// Prints the commands that stand alone, which are typed without a group.
	private static void printAlone(PrintStream out) {
		List<Commands.Command> alone = Commands.alone();
		if (alone.isEmpty()) {
			return;
		}
		out.println();
		int width = alone.stream().mapToInt(command -> command.name().length()).max().orElse(0);
		for (Commands.Command command : alone) {
			out.println(pad(command.name(), width) + "  " + command.description());
		}
	}

	/// Prints one command: how it is typed, what it does, and what each parameter is.
	private static void printCommand(Commands.Command command, PrintStream out) {
		out.println("usage: hdsl " + usage(command));
		out.println();
		out.println(command.description());
		if (command.parameters().isEmpty()) {
			return;
		}
		out.println();
		out.println("parameters:");
		int width = command.parameters()
		                   .stream()
		                   .mapToInt(parameter -> written(parameter).length())
		                   .max()
		                   .orElse(0);
		for (Commands.Parameter parameter : command.parameters()) {
			boolean needed = parameter.kind() == Commands.Kind.REQUIRED ||
			                 parameter.kind() == Commands.Kind.REQUIRED_MANY;
			out.println("  " + pad(written(parameter), width) + "  " + parameter.description() +
			            (needed ? " (required)" : ""));
		}
	}

	/// Returns how a command is typed, with every parameter and the ones that can be left out.
	private static String usage(Commands.Command command) {
		StringBuilder line = new StringBuilder(command.spelling());
		for (Commands.Parameter parameter : command.parameters()) {
			boolean needed = parameter.kind() == Commands.Kind.REQUIRED ||
			                 parameter.kind() == Commands.Kind.REQUIRED_MANY;
			String written = written(parameter);
			line.append(' ').append(needed ? written : "[" + written + "]");
			if (parameter.kind() == Commands.Kind.REQUIRED_MANY ||
			    parameter.kind() == Commands.Kind.OPTIONAL_MANY) {
				line.append("...");
			}
		}
		return line.toString();
	}

	/// Returns one parameter as it is typed.
	private static String written(Commands.Parameter parameter) {
		return parameter.kind() == Commands.Kind.FLAG
		       ? "--" + parameter.name()
		       : "--" + parameter.name() + " <" + parameter.placeholder() + ">";
	}

	/// Returns one column of text padded to the width the column needs.
	private static String pad(String text, int width) {
		return text.length() >= width ? text : text + " ".repeat(width - text.length());
	}

	/// A parsed command line.
	///
	/// @param command    the words that name the command; the empty string for the launcher's own
	///                   help, and a group alone when the line did not say which of its commands to run
	/// @param options    the parameters that were given, by name, with every value each was given
	/// @param showHelp   whether help was asked for
	/// @param deprecated the flag-style line these words came from, or `null` when they are the line
	/// @param error      what was wrong with the line, or `null` when nothing was
	public record Invocation(String command, Map<String, List<String>> options, boolean showHelp,
	                         @Nullable List<String> deprecated, @Nullable String error) {

		/// Returns the first value a parameter was given.
		///
		/// @param name the parameter's spelling, without the leading dashes
		/// @return the value, or `null` when the parameter was not given
		public @Nullable String option(String name) {
			List<String> values = options.get(name);
			return values == null || values.isEmpty() ? null : values.get(0);
		}

		/// Returns every value a parameter was given.
		///
		/// @param name the parameter's spelling, without the leading dashes
		/// @return the values, empty when the parameter was not given
		public List<String> values(String name) {
			List<String> values = options.get(name);
			return values == null ? List.of() : List.copyOf(values);
		}

		/// Reports whether a flag was given.
		///
		/// @param name the flag's spelling, without the leading dashes
		/// @return whether it is there
		public boolean flag(String name) {
			return options.containsKey(name);
		}
	}
}
