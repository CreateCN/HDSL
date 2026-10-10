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

import com.mcxcc303.hdsl.cli.Cli.Invocation;

import com.mcxcc303.hdsl.plugin.CatalogSources;
import com.mcxcc303.hdsl.core.DshException;
import org.jetbrains.annotations.NotNullByDefault;
import java.io.PrintStream;
import java.util.List;

/// The commands that manage where the plugin catalogue is read from: listing the sources, adding
/// one and taking one away.
@NotNullByDefault
public final class PluginSourceCommands {
    /// Prints the places the plugin catalogue is read from.
    ///
    /// Every source that answers contributes plugins, so what this prints is the
    /// order of authority as well as the list: the first source that lists a
    /// plugin is where its description, version and download count come from.
    ///
    /// @param out the stream for normal output
    /// @return the process exit code
    static int listPluginSources(PrintStream out) {
        List<String> configured = CatalogSources.configuredSources();
        List<String> resolved = CatalogSources.sources();
        for (String source : resolved) {
            boolean builtIn = !configured.contains(source);
            out.println((builtIn ? "  " : "* ") + source
                    + (builtIn ? "  [built in]" : "")
                    + (CatalogSources.isPackageSource(source) ? "  [npm package]" : ""));
        }
        out.println("  " + CatalogSources.fallbackSource()
                + "  [npm package, read only when none of the above answers]");
        if (configured.isEmpty()) {
            out.println("(nothing configured; the address above is what the launcher publishes with)");
        }
        return 0;
    }

    /// Adds a place for the plugin catalogue to be read from.
    ///
    /// @param invocation the parsed invocation
    /// @param out        the stream for normal output
    /// @param err        the stream for error output
    /// @return the process exit code
    static int addPluginSource(Invocation invocation, PrintStream out, PrintStream err) {
        if (invocation.arguments().isEmpty()) {
            err.println("error: --add-plugin-source needs an address or an npm package name");
            return 1;
        }
        String source = invocation.arguments().get(0);
        try {
            CatalogSources.addSource(source);
            out.println("Added " + source);
            return listPluginSources(out);
        } catch (DshException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }

    /// Removes a place the plugin catalogue was read from.
    ///
    /// Removing the last one puts the published pair back, which is said out loud
    /// rather than left to be discovered.
    ///
    /// @param invocation the parsed invocation
    /// @param out        the stream for normal output
    /// @param err        the stream for error output
    /// @return the process exit code
    static int removePluginSource(Invocation invocation, PrintStream out, PrintStream err) {
        if (invocation.arguments().isEmpty()) {
            err.println("error: --remove-plugin-source needs an address or an npm package name");
            return 1;
        }
        String source = invocation.arguments().get(0);
        try {
            CatalogSources.removeSource(source);
            out.println("Removed " + source);
            if (CatalogSources.configuredSources().isEmpty()) {
                out.println("That was the last one, so the launcher is back to the published pair");
            }
            return listPluginSources(out);
        } catch (DshException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }


    private PluginSourceCommands() {
    }
}
