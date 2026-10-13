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

import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/*
 * Rows printed as columns under a header
 *
 * A column takes the width of its longest cell, so a table can be wider than the terminal
 */
@NotNullByDefault
final class DshTable {
	private final String[] headers;
	private final List<String[]> rows = new ArrayList<>();
	private final int[] widths;

	DshTable(String... headers) {
		this.headers = headers;
		this.widths = new int[headers.length];
		for (int column = 0; column < headers.length; column++) {
			widths[column] = headers[column].length();
		}
	}

	void row(String... cells) {
		for (int column = 0; column < widths.length; column++) {
			widths[column] = Math.max(widths[column], cells[column].length());
		}
		rows.add(cells);
	}

	void print(PrintStream out) {
		if (rows.isEmpty()) return;

		line(out, headers);
		for (String[] row : rows) {
			line(out, row);
		}
	}

	private void line(PrintStream out, String[] cells) {
		StringBuilder line = new StringBuilder();
		for (int column = 0; column < cells.length; column++) {
			String cell = cells[column];
			if (column > 0) line.append("  ");

			line.append(cell);
			if (column < cells.length - 1) {
				line.repeat(" ", widths[column] - cell.length());
			}
		}
		out.println(line);
	}
}