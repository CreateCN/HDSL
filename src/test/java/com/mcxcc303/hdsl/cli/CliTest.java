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

import com.mcxcc303.hdsl.plugin.MarketSource;
import com.mcxcc303.hdsl.skill.DshSkillSources;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/*
 * What a command line turns into, and what it says when it cannot be read.
 *
 * The parser is the one part of the launcher that runs before anything else exists — no settings,
 * no toolkit, no instance — so it is worth pinning on its own. What is pinned here is what the
 * table decides: which words name a command, what a command takes, what a line that leaves a
 * parameter out is told, and that the flag-style spelling of a command is read as that command
 * rather than as a second implementation of it.
 *
 * Nothing here runs a command that touches an instance, with one exception that is deliberately
 * read-only: a flag-style line has to be run to see the warning printed after it, and it names an
 * instance that does not exist.
 */
class CliTest {

	// What a command line that cannot be read returns.
	private static final int USAGE_ERROR = 2;

	@Test
	void aCommandLineWithNoWordOfOursMeansTheInterface() {
		assertNull(Cli.parse(List.of()), "no arguments at all is the launcher opening its window");
		assertNull(Cli.parse(List.of("--something-else")), "an argument this launcher does not know is not a command");
		assertNull(Cli.parse(List.of("/tmp/some-pack.hdslp")),
		           "what the interface was asked to open is its argument, not a command");

		Cli.Invocation asked = Cli.parse(List.of("not-a-command", "--help"));
		assertNotNull(asked, "a word this launcher does not know, asking for help, is answered as help");
		assertTrue(asked.showHelp());
	}

	@Test
	void aGroupAndACommandAreReadAsOne() {
		Cli.Invocation invocation = Cli.parse(List.of("instance", "create", "--id", "mine",
		                                              "--dsh-version", "0.2.0-rc.2"));
		assertNotNull(invocation);
		assertEquals("instance create", invocation.command());
		assertEquals("mine", invocation.option("id"));
		assertEquals("0.2.0-rc.2", invocation.option("dsh-version"));
		assertNull(invocation.error());
	}

	@Test
	void aParameterCanBeWrittenWithItsValueAttached() {
		Cli.Invocation attached = Cli.parse(List.of("instance", "delete", "--id=mine"));
		Cli.Invocation apart = Cli.parse(List.of("instance", "delete", "--id", "mine"));
		assertNotNull(attached);
		assertNotNull(apart);
		assertEquals(apart.options(), attached.options(), "--id=mine and --id mine are one parameter given once");
	}

	@Test
	void aLineThatLeavesAParameterOutNamesIt() {
		Cli.Invocation invocation = Cli.parse(List.of("instance", "create", "--id", "mine"));
		assertNotNull(invocation);
		assertEquals("instance create needs --dsh-version", invocation.error());
		assertEquals(USAGE_ERROR, run(invocation).code(), "a line that cannot be read is not a success");
	}

	@Test
	void aParameterNobodyDeclaredIsRefused() {
		Cli.Invocation invocation = Cli.parse(List.of("instance", "list", "--running", "--nope"));
		assertNotNull(invocation);
		assertEquals("instance list does not take --nope", invocation.error());
	}

	@Test
	void aParameterGivenTwiceIsRefused() {
		Cli.Invocation invocation = Cli.parse(List.of("instance", "delete", "--id", "one", "--id", "two"));
		assertNotNull(invocation);
		assertEquals("instance delete --id was given more than once", invocation.error());
	}

	@Test
	void aFlagTakesNoValue() {
		Cli.Invocation invocation = Cli.parse(List.of("instance", "list", "--running=yes"));
		assertNotNull(invocation);
		assertEquals("instance list --running is a flag and takes no value", invocation.error());
	}

	@Test
	void aParameterThatCanBeGivenMoreThanOnceKeepsEveryValue() {
		Cli.Invocation invocation = Cli.parse(List.of("plugin", "remove", "--id", "mine",
		                                              "--spec", "dsh-a@1.0.0", "--spec", "dsh-b"));
		assertNotNull(invocation);
		assertEquals(List.of("dsh-a@1.0.0", "dsh-b"), invocation.values("spec"));
		assertEquals("dsh-a@1.0.0", invocation.option("spec"), "the first of them, where one is wanted");

		Cli.Invocation none = Cli.parse(List.of("plugin", "remove", "--id", "mine"));
		assertNotNull(none);
		assertEquals("plugin remove needs --spec", none.error(), "one of them is required, unlike --package");
	}

	@Test
	void aGroupWithNothingAfterItAsksForACommand() {
		Cli.Invocation invocation = Cli.parse(List.of("instance"));
		assertNotNull(invocation);
		assertEquals("instance needs one of its commands", invocation.error());
		assertEquals(USAGE_ERROR, run(invocation).code());
	}

	@Test
	void aFlagStyleCommandLineIsTheCommandItNames() {
		Cli.Invocation deprecated = Cli.parse(List.of("--create-instance", "mine", "--dsh-version", "0.2.0"));
		Cli.Invocation written = Cli.parse(List.of("instance", "create", "--id", "mine", "--dsh-version", "0.2.0"));
		assertNotNull(deprecated);
		assertNotNull(written);
		assertEquals(written.command(), deprecated.command(), "the two spellings name one command");
		assertEquals(written.options(), deprecated.options(), "and one set of parameters");
		assertNotNull(deprecated.deprecated(), "the flag-style line is carried, because the warning prints it");
		assertNull(written.deprecated());
	}

	@Test
	void aFlagStyleCommandSaysWhatItIsCalledNowAfterItHasRun() {
		Cli.Invocation invocation = Cli.parse(List.of("--get-key", "no-such-instance-for-a-test"));
		assertNotNull(invocation);
		Run run = run(invocation);
		assertEquals("  hdsl instance launch --id no-such-instance-for-a-test --key", lastLine(run.out()),
		           "the warning is the last thing printed, and it prints the line that just ran");
	}

	@Test
	void aFlagWhoseCommandIsGoneSaysWhatToWriteInstead() {
		Cli.Invocation invocation = Cli.parse(List.of("--install", "0.1.0"));
		assertNotNull(invocation, "it is answered rather than handed to the interface, which would open a window");
		assertNotNull(invocation.error());
		assertTrue(invocation.error().contains("--install is gone"), invocation.error());
	}

	@Test
	void theHelpIsWrittenFromTheSameTableTheCommandsAre() {
		for (Commands.Command command : Commands.all()) {
			List<String> words = new ArrayList<>(List.of(command.spelling().split(" ")));
			words.add("--help");
			Cli.Invocation invocation = Cli.parse(words);
			assertNotNull(invocation, command.spelling());

			Run run = run(invocation);
			assertEquals(0, run.code(), command.spelling());
			assertTrue(run.out().contains("usage: hdsl " + command.spelling()),
			           command.spelling() + " is not in the help that is written from the table");
			assertTrue(run.out().contains(command.description()), command.spelling());
			for (Commands.Parameter parameter : command.parameters()) {
				assertTrue(run.out().contains("--" + parameter.name()),
				           command.spelling() + " does not say it takes --" + parameter.name());
			}
		}
	}

	@Test
	void theSourcesInUseArePrintedForPluginsAndForSkills() {
		Run run = run(Cli.parse(List.of("source", "list")));
		assertEquals(0, run.code(), run.err());
		assertTrue(run.out().contains(MarketSource.AWESOME_DSH_PLUGIN.id()),
		           "the plugin market is not in the answer:\n" + run.out());
		for (DshSkillSources source : DshSkillSources.values()) {
			assertTrue(run.out().contains(source.id()), source.id() + " is not in the answer:\n" + run.out());
		}
		assertEquals(2, run.out().lines().filter(line -> line.startsWith("* ")).count(),
		           "one source of each kind is in use, and the answer says which:\n" + run.out());
	}

	@Test
	void theTwoListSourceFlagsAreOneCommand() {
		Cli.Invocation plugins = Cli.parse(List.of("--list-plugin-sources"));
		Cli.Invocation skills = Cli.parse(List.of("--list-skill-sources"));
		assertNotNull(plugins);
		assertNotNull(skills);
		assertEquals(plugins.command(), skills.command());
		assertEquals("source list", skills.command());
		assertEquals(plugins.options(), skills.options());
	}

	// Runs an invocation and keeps what it printed.
	private static Run run(Cli.Invocation invocation) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		int code = Cli.run(invocation,
		                   new PrintStream(out, true, StandardCharsets.UTF_8),
		                   new PrintStream(err, true, StandardCharsets.UTF_8));
		return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
	}

	// Returns the last line that is not blank.
	private static String lastLine(String text) {
		List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
		return lines.isEmpty() ? "" : lines.get(lines.size() - 1);
	}

	// What a command line printed, and what it returned.
	private record Run(int code, String out, String err) {
	}
}
