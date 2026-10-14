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
package org.jackhuang.hmcl.setting;

import com.google.gson.JsonObject;
import com.mcxcc303.hdsl.instance.DshHomeMode;
import com.mcxcc303.hdsl.instance.DshIsolationPolicy;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstanceIsolationDefaultsTest {
    @Test
    void newSettingsAndNullPolicyDefaultToIsolation() {
        LauncherSettings settings = new LauncherSettings();
        assertEquals(DshIsolationPolicy.ALWAYS, settings.isolationPolicy());
        settings.isolationPolicyProperty().set(null);
        assertEquals(DshIsolationPolicy.ALWAYS, settings.isolationPolicy());
    }

    @Test
    void missingPolicyDefaultsToIsolation() throws Exception {
        assertEquals(DshIsolationPolicy.ALWAYS, restore("{}").isolationPolicy());
    }

    @Test
    void legacyAutomaticDefaultDoesNotKeepNewInstancesShared() throws Exception {
        LauncherSettings settings = restore("""
                {"isolationPolicy":"modded", "defaultHomeMode":"VERSION_SHARED"}
                """);
        assertEquals(DshIsolationPolicy.ALWAYS, settings.isolationPolicy());
        assertEquals(DshHomeMode.VERSION_SHARED, settings.defaultHomeModeProperty().get(),
                "the runtime default used by existing GLOBAL instances must not change");
    }

    @Test
    void legacyExplicitSharingRemainsAvailable() throws Exception {
        assertEquals(DshIsolationPolicy.NEVER,
                restore("{\"isolationPolicy\":\"never\"}").isolationPolicy());
    }

    @Test
    void newExplicitPolicyTakesPrecedenceOverLegacyDefault() throws Exception {
        assertEquals(DshIsolationPolicy.WITH_PLUGINS, restore("""
                {"isolationPolicy":"always", "newInstanceIsolationPolicy":"modded"}
                """).isolationPolicy());
    }

    @Test
    void invalidNewPolicyFallsBackToIsolation() throws Exception {
        assertEquals(DshIsolationPolicy.ALWAYS, restore("""
                {"isolationPolicy":"never", "newInstanceIsolationPolicy":"invalid"}
                """).isolationPolicy());
    }

    @Test
    void explicitPoliciesSurviveSavingAndReloading() throws Exception {
        for (DshIsolationPolicy policy : DshIsolationPolicy.values()) {
            LauncherSettings settings = new LauncherSettings();
            settings.isolationPolicyProperty().set(policy);
            settings.defaultHomeModeProperty().set(DshHomeMode.VERSION_SHARED);

            String json = capture(settings);
            assertEquals(policy.id(), JsonUtils.GSON.fromJson(json, JsonObject.class)
                    .get("newInstanceIsolationPolicy").getAsString());
            LauncherSettings restored = restore(json);
            assertEquals(policy, restored.isolationPolicy());
            assertEquals(DshHomeMode.VERSION_SHARED, restored.defaultHomeModeProperty().get());
        }
    }

    // Exercise the actual persisted schema without loading or saving the user's settings file.
    private static Class<?> snapshotType() {
        return Arrays.stream(SettingsManager.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Snapshot"))
                .findFirst().orElseThrow();
    }

    private static LauncherSettings restore(String json) throws Exception {
        Class<?> type = snapshotType();
        Object snapshot = JsonUtils.GSON.fromJson(json, type);
        Method apply = type.getDeclaredMethod("applyTo", LauncherSettings.class);
        apply.setAccessible(true);
        LauncherSettings settings = new LauncherSettings();
        apply.invoke(snapshot, settings);
        return settings;
    }

    private static String capture(LauncherSettings settings) throws Exception {
        Method capture = snapshotType().getDeclaredMethod("of", LauncherSettings.class);
        capture.setAccessible(true);
        return JsonUtils.GSON.toJson(capture.invoke(null, settings));
    }
}
