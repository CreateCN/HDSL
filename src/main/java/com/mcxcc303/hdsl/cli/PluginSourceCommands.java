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

import com.mcxcc303.hdsl.plugin.CatalogSources;
import com.mcxcc303.hdsl.plugin.MarketSource;
import com.mcxcc303.hdsl.skill.DshSkillSource;
import com.mcxcc303.hdsl.skill.DshSkillSources;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;

/*
 * The command to show where the launcher reads what it installs from (plugin, skill, npm, etc.)
 */
@NotNullByDefault
public final class PluginSourceCommands {
	private PluginSourceCommands() {}

	/**
	 * @param out the stream for normal output
	 * @return the process exit code
	 */
	static int listSources(PrintStream out) {
		listPluginSources(out);
		out.println();

		listSkillSources(out);
		return 0;
	}

	/**
	 * Prints the plugin markets, and marks the one in use.
	 *
	 * @param out the stream for normal output
	 */
	static void listPluginSources(PrintStream out) {
		MarketSource selected = CatalogSources.marketSource();
		for (MarketSource source : MarketSource.values()) {
			out.println((source == selected ? "* " : "  ") + source.id() + "  " + source.label() +
			            (source == selected ? "  [in use]" : ""));
		}
		for (String address : selected.addresses()) {
			out.println("    " + address);
		}
		String fallback = selected.fallback();
		if (fallback != null) {
			out.println("    " + fallback + "  [npm package, read only when the address above answers nothing]");
		}
	}

	/**
	 * Prints the skill catalogues, and marks the one in use.
	 *
	 * @param out the stream for normal output
	 */
	static void listSkillSources(PrintStream out) {
		DshSkillSources selected = DshSkillSource.selectedSource();
		for (DshSkillSources source : DshSkillSources.values()) {
			out.println((source == selected ? "* " : "  ") + source.id() + "  " + source.label() +
			            (source == selected ? "  [in use]" : ""));
		}
		out.println("    the two index different repositories, and a pack is fetched from GitHub either way");
	}
}
