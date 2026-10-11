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
import org.jetbrains.annotations.NotNullByDefault;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Every instance the launcher knows, across every folder it looks in.
///
/// One name is one instance, but a folder can be copied: what the listing does with a name two
/// folders both hold is keep the one in the folder being shown and say so once in the log.
@NotNullByDefault
public final class InstanceListing {
    public static List<DshInstance> list() {
        List<DshInstance> instances = new ArrayList<>();
        java.util.Map<String, Path> taken = new java.util.HashMap<>();
        for (Path root : InstanceFolders.folders()) {
            for (DshInstance instance : InstanceStore.listIn(root)) {
                Path directory;
                try {
                    directory = instance.instanceDirectory();
                } catch (DshException e) {
                    continue;
                }
                Path previous = taken.get(instance.id());
                if (previous == null) {
                    taken.put(instance.id(), directory);
                    instances.add(instance);
                } else if (!previous.equals(directory)) {
                    warnAboutASharedName(instance.id(), previous, directory);
                }
            }
        }
        instances.sort(Comparator.comparingLong(DshInstance::createdAt).reversed());
        return instances;
    }

    /// Says, once per run, that one name is held by two folders.
    ///
    /// A name is meant to be one instance — [#create] refuses a second one — but a folder can be
    /// copied, a backup can be unpacked beside the original, and a folder the user adds can hold a
    /// copy of an instance the launcher already knows. What that leaves is two folders answering to
    /// one name, and every lookup and every removal then acts on whichever folder comes first: the
    /// list would show the same instance twice while managing one of them.
    ///
    /// So the first folder wins — [#folders] puts the folder being shown first — and the copies it
    /// shadows are named in the log rather than in the list, because what a person needs is one row
    /// per name and one line saying which folder was left out and what to do about it.
    ///
    /// @param id      the name both folders answer to
    /// @param used    the folder the launcher uses
    /// @param ignored the folder it leaves alone
    static void warnAboutASharedName(String id, Path used, Path ignored) {
        if (!REPORTED_SHARED_NAMES.add(id)) {
            return;
        }
        LOG.warning("Instance \"" + id + "\" is in two InstanceFolders.folders: " + used
                + " is used, because the folder being shown comes first, and " + ignored
                + " is left alone. Rename or remove one of them: one name is one instance.");
    }

    /// The names whose two folders have already been reported, so a lookup that happens on every
    /// frame says it once rather than every time.
    private static final java.util.Set<String> REPORTED_SHARED_NAMES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();


    private InstanceListing() {
    }
}
