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

import com.mcxcc303.hdsl.instance.InstanceListing;
import com.mcxcc303.hdsl.instance.InstanceStore;
import com.mcxcc303.hdsl.harness.HarnessInstaller;
import com.mcxcc303.hdsl.harness.ReleaseQueries;
import com.mcxcc303.hdsl.plugin.DshBuildScripts;
import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.instance.DshInstance;
import com.mcxcc303.hdsl.plugin.DshLocalPlugins;
import com.mcxcc303.hdsl.plugin.DshPluginInstaller;
import com.mcxcc303.hdsl.harness.DshPreset;
import com.mcxcc303.hdsl.harness.DshRelease;
import org.jetbrains.annotations.NotNullByDefault;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/// The commands about the harness's own packages: which versions are installed, which the registry
/// publishes, installing and removing one, adding a plugin from a name or a file, and answering the
/// question pnpm asks about a package's install scripts.
@NotNullByDefault
public final class PluginCommands {

    /// Runs the `LIST_INSTALLED` command.
    static int listInstalled(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
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

    /// Runs the `LIST_REMOTE` command.
    static int listRemote(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        for (DshRelease release : ReleaseQueries.fetchReleases()) {
            String tag = release.primaryTag();
            out.println(release.version()
                    + "\t" + release.type().id()
                    + "\t" + (release.publishedAt() == null ? "-" : release.publishedAt())
                    + (tag == null ? "" : "\t" + tag));
        }
        return 0;
    }

    /// Runs the `INSTALL` and `UNINSTALL` command.
    static int installOrUninstall(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
        // A runtime is not installed on its own any more: it is
        // installed into the instance that runs it, when that
        // instance is made.
        err.println("error: DeepSeek Harness is installed into an instance; "
                + "use --create-instance with --dsh-version");
        return 2;
    }

    /// Runs the `INSTALL_PLUGIN` command.
    static int installPlugin(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
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

    /// Runs the `BUILD_SCRIPTS` command.
    static int buildScripts(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
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

    /// Runs the `INSTALL_PLUGIN_FILE` command.
    static int installPluginFile(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
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

    /// Runs the `REMOVE_PLUGIN` command.
    static int removePlugin(Invocation invocation, PrintStream out, PrintStream err) throws DshException {
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

    private PluginCommands() {
    }
}
