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
package com.mcxcc303.hdsl.instance;

import com.mcxcc303.hdsl.core.DshException;
import com.mcxcc303.hdsl.core.DshFiles;
import com.mcxcc303.hdsl.core.DshPaths;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Reading and writing one instance's manifest.
///
/// An instance is a directory holding `instance.json`, and the folder it was read out of
/// is stamped onto what comes back — so an instance in a folder the user added is managed where it
/// is, not where the launcher owns.
@NotNullByDefault
public final class InstanceStore {
    /// The manifest file inside an instance directory.
    public static final String MANIFEST_NAME = "instance.json";

    /// Reads the manifest inside an instance directory.
    ///
    /// The directory that was read is stamped onto the instance, and it wins
    /// over whatever the manifest records: the manifest is a file, and it can be
    /// copied to another folder, carried to another machine, or written by hand
    /// — while the folder the manifest was found in is a fact about right now.
    /// An instance read out of a folder the user added is in that folder, and
    /// every later operation on it — installing, launching, renaming, removing —
    /// has to name that folder rather than the one the launcher owns.
    ///
    /// @param directory the candidate instance directory
    /// @return the instance, or `null` when there is no readable manifest
    private static @Nullable DshInstance read(Path directory) {
        Path manifest = directory.resolve(MANIFEST_NAME);
        if (!Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            DshInstance instance = JsonUtils.fromJsonFile(manifest, DshInstance.class);
            if (instance == null || instance.id() == null || instance.version() == null) {
                return null;
            }
            return instance.withDirectory(directory);
        } catch (Exception e) {
            LOG.warning("Failed to read instance manifest " + manifest, e);
            return null;
        }
    }

    /// Writes an instance manifest.
    ///
    /// @param instance the instance to persist
    /// @throws DshException when the manifest cannot be written
    static void write(DshInstance instance) throws DshException {
        try {
            Path directory = instance.instanceDirectory();
            Files.createDirectories(directory);
            JsonUtils.writeToJsonFile(directory.resolve(MANIFEST_NAME), instance);
        } catch (IOException e) {
            throw new DshException("Failed to write the instance manifest for " + instance.id(), e);
        }
    }
    /// Lists every readable instance, newest first.
    ///
    /// Directories without a manifest, or with an unreadable one, are skipped
    /// rather than failing the whole listing.
    ///
    /// @return the known instances
    public static List<DshInstance> listIn(java.nio.file.Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<DshInstance> instances = new ArrayList<>();
        try (var children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                DshInstance instance = read(child);
                if (instance != null) {
                    instances.add(instance);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to read instances in " + root, e);
            return List.of();
        }
        instances.sort(java.util.Comparator.comparing(DshInstance::createdAt).reversed());
        return List.copyOf(instances);
    }

    /// Finds an instance by id.
    ///
    /// Looked for in every folder the launcher knows about: an id names one
    /// instance, and a person who sees it in the list has to be able to open,
    /// launch or remove it without knowing which folder it came from. The folder
    /// being shown is searched first, so the common case reads one directory —
    /// and it is also the folder that wins when the name is held twice, which is
    /// the order [#list] keeps a row for.
    ///
    /// @param id the instance id
    /// @return the instance, or `null` when it does not exist or is unreadable
    public static @Nullable DshInstance find(String id) {
        try {
            String segment = DshPaths.segment(id, "instance id");
            DshInstance found = null;
            Path foundIn = null;
            for (Path root : InstanceFolders.folders()) {
                DshInstance instance = read(root.resolve(segment));
                if (instance == null) {
                    continue;
                }
                if (found == null) {
                    found = instance;
                    foundIn = instance.instanceDirectory();
                } else {
                    // The rest of the folders are still looked in, so that a name held twice is said
                    // out loud rather than being a coin toss nobody can see.
                    InstanceListing.warnAboutASharedName(id, foundIn, instance.instanceDirectory());
                }
            }
            return found;
        } catch (DshException e) {
            return null;
        }
    }

    /// Deletes a path, ignoring anything that goes wrong.
    ///
    /// Used for the copies a rename or delete leaves behind, where a failure to
    /// tidy up must not fail the operation that already succeeded.
    ///
    /// @param path the path to delete
    static void deleteQuietly(Path path) {
        if (path != null) {
            DshFiles.deleteTreeQuietly(path);
        }
    }


    private InstanceStore() {
    }
}
