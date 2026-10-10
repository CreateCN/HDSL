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
package com.mcxcc303.hdsl.pack;

import com.mcxcc303.hdsl.core.DshException;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/// The `dspack` container: what a pack says about itself before anything is written.
///
/// The archive holds `dspack.json`, which names the parts it carries and the container
/// version it was written in.  Reading it is how the launcher decides what it is looking at, and
/// what it refuses: an archive that names a container newer than this launcher understands is not
/// guessed at.
@NotNullByDefault
public final class DspackContainer {
    /// The container's marker file, at the archive's root.
    private static final String MARKER = "dspack.json";

    /// The container format the marker must name.
    private static final String FORMAT = "dspack";

    /// The container version this launcher writes and reads.
    private static final int CURRENT_CONTAINER = 3;

    /// The container version still accepted, which carries manifest v4.
    private static final int OLD_CONTAINER = 2;

    /// What an archive turned out to be.
    ///
    /// @param containerVersion the container's own version
    /// @param manifestVersion  the manifest version the container carries
    /// @param manifest         the pack's manifest
    /// @param wholeHome        whether the pack replaces an entire `$DSH_HOME`
    public record Container(int containerVersion, int manifestVersion, JsonObject manifest,
                            boolean wholeHome) {

        /// Returns a string field of the manifest.
        ///
        /// @param key the field
        /// @return the text, or `null`
        public @Nullable String text(String key) {
            return manifest.has(key) && manifest.get(key).isJsonPrimitive()
                    ? manifest.get(key).getAsString() : null;
        }

        /// Returns what the pack is called.
        ///
        /// @param fallback what to answer when the manifest does not say
        /// @return the name
        public String name(String fallback) {
            String name = text("displayName");
            return name == null || name.isBlank() ? fallback : name;
        }

        /// Returns the profile the pack wants to be installed as.
        ///
        /// @param fallback what to answer when the manifest does not say
        /// @return the profile name
        public String profileName(String fallback) {
            String name = text("profileName");
            return name == null || name.isBlank() ? fallback : name;
        }
    }

    /// Works out what an archive is.
    ///
    /// The container is recognised by its marker at the archive root, and the container's own version
    /// is what decides which manifest versions are expected — **not** the manifest's own
    /// `manifestVersion` field, which an older writer set inconsistently. The specification's table:
    ///
    /// | marker | manifest | action |
    /// |---|---|---|
    /// | none | — | not a pack (a plain ZIP, or a broken file) |
    /// | `version` 3 | 5 | current |
    /// | `version` 2 | 4 | still read |
    /// | anything else | — | refused, by version |
    ///
    /// @param archive the file
    /// @return what it is
    /// @throws DshException when it is not a pack this launcher can install
    public static Container identify(Path archive) throws DshException {
        JsonObject marker;
        JsonObject manifest;
        int containerVersion;
        try (ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
            marker = readJson(zip, MARKER);
            if (marker == null) {
                // Not a refusal of the *format*: a ZIP with no marker is simply not a pack, and the
                // person may have picked the wrong file.
                throw new DshException("That archive is not a DeepSeek Harness pack: it has no "
                        + MARKER + " at its root", null);
            }
            if (!FORMAT.equals(text(marker, "format"))) {
                throw new DshException("That archive says it is a "
                        + text(marker, "format") + " archive, not a pack", null);
            }
            Integer version = number(marker, "version");
            if (version == null || (version != CURRENT_CONTAINER && version != OLD_CONTAINER)) {
                throw new DshException("That pack is container version " + version
                        + ", and this launcher reads versions " + OLD_CONTAINER + " and "
                        + CURRENT_CONTAINER, null);
            }
            containerVersion = version;
            manifest = readJson(zip, "manifest.json");
            if (manifest == null) {
                throw new DshException("That pack has no manifest.json", null);
            }
        } catch (IOException e) {
            throw new DshException("Could not read " + archive + ": " + e.getMessage(), e);
        }

        Integer manifestVersion = number(manifest, "manifestVersion");
        if (manifestVersion == null) {
            manifestVersion = 5;
        }
        // The two versions have to be a pair the specification names. A container 3 holding a
        // manifest 4 is a file assembled wrongly — the writer took one half from each format — and
        // installing it would mean guessing which half to believe.
        int expectedContainer = containerFor(manifestVersion);
        if (expectedContainer != containerVersion) {
            throw new DshException("That pack's container is version " + containerVersion
                    + " and its manifest is version " + manifestVersion + ", which do not go together",
                    null);
        }
        // `collection` is reserved by the specification and refused by its validator; the same is
        // done here rather than pretending to understand it.
        String type = text(manifest, "type");
        if ("collection".equalsIgnoreCase(type)) {
            throw new DshException("That pack is a collection, which this launcher cannot install", null);
        }
        return new Container(containerVersion, manifestVersion, manifest,
                "dshhome".equalsIgnoreCase(type));
    }

    /// Returns the container version that carries a manifest version.
    ///
    /// The specification's pair table, and only those two pairs: an unknown manifest version has no
    /// container, which is what makes a mismatch detectable rather than merely unlikely.
    ///
    /// @param manifestVersion the manifest's version
    /// @return the container version, or `-1` when no container carries that manifest
    static int containerFor(int manifestVersion) {
        return switch (manifestVersion) {
            case 4 -> OLD_CONTAINER;
            case 5 -> CURRENT_CONTAINER;
            default -> -1;
        };
    }

    /// Reads a JSON file out of an archive.
    ///
    /// @param zip the archive
    /// @param name the member's name
    /// @return the object, or `null` when it is not there or is not an object
    static @Nullable JsonObject readJson(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (InputStream stream = zip.getInputStream(entry)) {
            JsonObject object = JsonUtils.fromJson(new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8), JsonObject.class);
            return object;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /// Reads a number out of an object.
    ///
    /// @param object the object
    /// @param key    the field
    /// @return the number, or `null`
    static @Nullable Integer number(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isNumber()
                ? object.get(key).getAsInt() : null;
    }

    /// Reads a string out of an object.
    ///
    /// @param object the object
    /// @param key    the field
    /// @return the text, or `null`
    static @Nullable String text(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsString() : null;
    }

    /// Lists an archive's members, for a caller that wants to show what a pack holds.
    ///
    /// @param archive the file
    /// @return the names, in the archive's own order
    /// @throws DshException when it cannot be read
    public static List<String> list(Path archive) throws DshException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        } catch (IOException e) {
            throw new DshException("Could not read " + archive, e);
        }
        return List.copyOf(names);
    }

    private DspackContainer() {
    }
}
