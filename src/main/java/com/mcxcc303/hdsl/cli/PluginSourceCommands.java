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

import org.jetbrains.annotations.NotNullByDefault;

import java.io.PrintStream;

/// The command about where plugins are read from: which markets this launcher knows, and which of
/// them it reads.
///
/// There is nothing to add or remove: a market is a place whose request shape this launcher knows,
/// so markets arrive with the launcher rather than from a settings file. What a person can choose is
/// which of them is read, and that is what this prints.
@NotNullByDefault
public final class PluginSourceCommands {
    /// Prints the plugin markets, and marks the one in use.
    ///
    /// The addresses under the market that is in use are printed too, in the order they are tried,
    /// because that is what somebody diagnosing a catalogue that will not load needs to see.
    ///
    /// @param out the stream for normal output
    /// @return the process exit code
    static int listPluginSources(PrintStream out) {
        MarketSource selected = CatalogSources.marketSource();
        for (MarketSource source : MarketSource.values()) {
            out.println((source == selected ? "* " : "  ") + source.id()
                    + "  " + source.label() + (source == selected ? "  [in use]" : ""));
        }
        for (String address : selected.addresses()) {
            out.println("    " + address);
        }
        String fallback = selected.fallback();
        if (fallback != null) {
            out.println("    " + fallback
                    + "  [npm package, read only when the address above answers nothing]");
        }
        return 0;
    }

    private PluginSourceCommands() {
    }
}