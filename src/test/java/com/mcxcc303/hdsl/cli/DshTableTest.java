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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/*
 * What a list printed as a table looks like
 *
 * Long paths stay whole, so a table can be wider than the terminal
 */
class DshTableTest {
	// The path a real `instance list` prints, which is longer than any terminal is wide
	private static final String PATH = "/home/thf/.local/share/hdsl/instances/0.2.0-rc.2/dsh";

	@Test
	void everyRowParsesBackIntoTheHeaderItWasWrittenUnder() {
		String printed = printed(table -> {
			table.row("0.2.0-rc.2", PATH, "ok");
			table.row("short", "/tmp/x", "INCOMPLETE");
		});

		List<String[]> parsed = parse(printed);
		assertEquals(3, parsed.size(), "a header and two rows:\n" + printed);
		assertArrayEquals(new String[]{"INSTANCE", "HOME", "STATE"}, parsed.get(0), printed);
		assertArrayEquals(new String[]{"0.2.0-rc.2", PATH, "ok"}, parsed.get(1), printed);
		assertArrayEquals(new String[]{"short", "/tmp/x", "INCOMPLETE"}, parsed.get(2), printed);
	}

	@Test
	void aTableWithNoRowPrintsNothing() {
		assertEquals("", printed(table -> {
		}), "a command that already said there is nothing here gets no header under it");
	}

	/**
	 * Returns what a table printed
	 *
	 * @param fill the rows to add
	 * @return what went to the stream
	 */
	private static String printed(Consumer<DshTable> fill) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		DshTable table = new DshTable("INSTANCE", "HOME", "STATE");
		fill.accept(table);
		table.print(new PrintStream(out, true, StandardCharsets.UTF_8));
		return out.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Splits what a table printed back into its cells, on the runs of spaces between the columns
	 *
	 * @param printed what the table printed
	 * @return one array of cells per line, the header first
	 */
	private static List<String[]> parse(String printed) {
		return printed.lines().map(line -> line.split(" {2,}")).toList();
	}
}
