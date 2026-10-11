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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// What a command line turns into.
///
/// The parser is the one part of the launcher that runs before anything else exists — no settings,
/// no toolkit, no instance — so it is worth pinning on its own: which flag is which command, what
/// counts as a subject, and what an unrecognised option is taken for.
class CliTest {

    @Test
    void aCommandLineWithNoFlagOfOursMeansTheInterface() {
        assertNull(Cli.parse(List.of()), "no arguments at all is the launcher opening its window");
        assertNull(Cli.parse(List.of("--something-else")),
                "an argument this launcher does not know is not a command");
    }

    @Test
    void aFlagChoosesTheCommand() {
        assertEquals(Cli.Command.DOCTOR, Cli.parse(List.of("--doctor")).command());
        assertEquals(Cli.Command.LIST_RUNNING, Cli.parse(List.of("--list-running")).command());
        assertEquals(Cli.Command.LIST_REMOTE, Cli.parse(List.of("--list-versions")).command(),
                "the flag a person types and the command it means are spelled differently on purpose");
    }

    @Test
    void theSubjectFollowsTheFlag() {
        Cli.Invocation invocation = Cli.parse(List.of("--launch", "my-instance"));
        assertNotNull(invocation);
        assertEquals(Cli.Command.LAUNCH, invocation.command());
        assertEquals("my-instance", invocation.subject());
    }

    @Test
    void anUnknownOptionIsAnOption() {
        Cli.Invocation invocation = Cli.parse(List.of("--install", "0.1.7-rc.1", "--runtime", "managed"));
        assertNotNull(invocation);
        assertEquals(Cli.Command.INSTALL, invocation.command());
        assertEquals("0.1.7-rc.1", invocation.subject());
        assertEquals("managed", invocation.option("runtime"));
    }

    @Test
    void anOptionWithNothingAfterItIsTrue() {
        Cli.Invocation invocation = Cli.parse(List.of("--doctor", "--verbose"));
        assertNotNull(invocation);
        assertEquals("true", invocation.option("verbose"));
    }

    @Test
    void helpIsRememberedRatherThanBeingACommandOfItsOwn() {
        Cli.Invocation invocation = Cli.parse(List.of("--help"));
        assertNotNull(invocation);
        assertTrue(invocation.showHelp());
    }

    @Test
    void theCommandsThatNameTwoThingsTakeTwo() {
        Cli.Invocation invocation = Cli.parse(List.of("--export-modpack", "my-instance", "/tmp/pack.hdslp"));
        assertNotNull(invocation);
        assertEquals(Cli.Command.EXPORT_MODPACK, invocation.command());
        assertEquals(List.of("my-instance", "/tmp/pack.hdslp"), invocation.arguments());
    }

    @Test
    void removingAPluginTakesEverySpecificationUpToTheNextOption() {
        Cli.Invocation invocation = Cli.parse(
                List.of("--remove-plugin", "my-instance", "dsh-a@1.0.0", "dsh-b", "--dry-run"));
        assertNotNull(invocation);
        assertEquals(Cli.Command.REMOVE_PLUGIN, invocation.command());
        assertEquals(List.of("my-instance", "dsh-a@1.0.0", "dsh-b"), invocation.arguments());
        assertEquals("true", invocation.option("dry-run"),
                "the specifications stop at the option that follows them");
    }

    @Test
    void sessionsAreCarriedByAWordOfItsOwn() {
        Cli.Invocation invocation = Cli.parse(
                List.of("--export-modpack", "my-instance", "/tmp/pack.hdslp", "--with-sessions"));
        assertNotNull(invocation);
        assertTrue(invocation.arguments().contains("--with-sessions"),
                "a pack that carries sessions says so among the arguments it was given");
    }
    @Test
    void theAddressAndTheKeyOfARunningInstanceAreCommandsOfTheirOwn() {
        Cli.Invocation url = Cli.parse(List.of("--get-url", "my-instance"));
        assertNotNull(url);
        assertEquals(Cli.Command.GET_URL, url.command());
        assertEquals("my-instance", url.subject(), "the instance is the subject, as everywhere else");

        Cli.Invocation key = Cli.parse(List.of("--get-key", "my-instance"));
        assertNotNull(key);
        assertEquals(Cli.Command.GET_KEY, key.command());
        assertEquals("my-instance", key.subject());
    }
}