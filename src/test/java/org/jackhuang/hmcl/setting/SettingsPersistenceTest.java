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

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.ThemeReference;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Every setting survives a write and a read
 *
 * A setting may take effect at runtime but be lost after restart if it is omitted from the mirror or mapped to the wrong field
 * Seventeen were in that state when the general, download-source and global instance-settings tabs were reworked
 * The properties are found through their accessors, so a setting added tomorrow is covered without this file being edited
 */
class SettingsPersistenceTest {
	// The properties that are deliberately not stored, each with its reason
	private static final List<String> NOT_STORED = List.of();

	// What the settings held before this test wrote its own, by property name
	private final Map<String, Object> was = new LinkedHashMap<>();

	@Test
	void everySettingAPropertyExposesIsCapturedAndRestored() throws Exception {
		LauncherSettings settings = SettingsManager.settings();

		List<String> unmade = new ArrayList<>();
		Map<String, Object> written = new LinkedHashMap<>();
		for (Method accessor : propertyAccessors()) {
			String name = nameOf(accessor);
			if (NOT_STORED.contains(name)) continue;

			Object property = accessor.invoke(settings);
			was.put(name, get(property));

			Object made = madeUp(kindOf(accessor));
			if (made == null) {
				unmade.add(name);
				continue;
			}
			set(property, made);
			written.put(name, made);
		}

		assertTrue(unmade.isEmpty(), "this test can make up no value for: " + unmade);
		SettingsManager.save();

		LauncherSettings read = SettingsManager.load();
		List<String> lost = new ArrayList<>();
		for (Map.Entry<String, Object> entry : written.entrySet()) {
			Object back = get(accessorOf(entry.getKey()).invoke(read));
			if (!entry.getValue().equals(back)) {
				lost.add(entry.getKey() + ": wrote " + entry.getValue() + ", read " + back);
			}
		}
		assertTrue(lost.isEmpty(), "these settings do not come back from the settings file: " + lost);
	}

	@AfterEach
	void putBack() throws Exception {
		LauncherSettings settings = SettingsManager.settings();
		for (Map.Entry<String, Object> entry : was.entrySet()) {
			try {
				set(accessorOf(entry.getKey()).invoke(settings), entry.getValue());
			} catch (RuntimeException | AssertionError ignored) {
				// A setting that cannot be put back is still better left alone than left written
			}
		}
		SettingsManager.save();
		was.clear();
	}

	private static List<Method> propertyAccessors() {
		List<Method> accessors = new ArrayList<>();
		for (Method method : LauncherSettings.class.getMethods()) {
			if (method.getName().endsWith("Property") && method.getParameterCount() == 0) {
				accessors.add(method);
			}
		}
		assertTrue(!accessors.isEmpty(), "found no property accessors in LauncherSettings");
		return accessors;
	}

	private static Method accessorOf(String name) throws NoSuchMethodException {
		return LauncherSettings.class.getMethod(name + "Property");
	}

	private static String nameOf(Method accessor) {
		return accessor.getName().substring(0, accessor.getName().length() - "Property".length());
	}

	// Read from the accessor rather than from the value: a setting that holds null has no value to read a kind from
	// The property interfaces answer with themselves, so they are spelled out; a generic value answers with its raw type
	private static Class<?> kindOf(Method accessor) {
		Class<?> type = accessor.getReturnType();
		if (javafx.beans.property.StringProperty.class.isAssignableFrom(type)) return String.class;
		if (javafx.beans.property.BooleanProperty.class.isAssignableFrom(type)) return Boolean.class;
		if (javafx.beans.property.DoubleProperty.class.isAssignableFrom(type)) return Double.class;
		if (!javafx.beans.property.ObjectProperty.class.isAssignableFrom(type)) return type;

		java.lang.reflect.Type held = ((java.lang.reflect.ParameterizedType) accessor.getGenericReturnType())
				.getActualTypeArguments()[0];
		if (held instanceof Class<?> heldClass) return heldClass;
		if (held instanceof java.lang.reflect.ParameterizedType parameterized) {
			return (Class<?>) parameterized.getRawType();
		}
		return Object.class;
	}

	// All a made-up value has to be is different from the one already there
	private static Object madeUp(Class<?> kind) {
		if (kind == String.class) return "persistence-test";
		if (kind == Boolean.class) return true;
		if (kind == Double.class || kind == double.class) return 42.5;
		if (kind == Integer.class || kind == int.class) return 42;
		if (kind == GameDirectory.class) return directory("test-directory-persistence");
		if (kind == ThemeReference.class) return new ThemeReference("test-pack-persistence", null);
		if (Map.class.isAssignableFrom(kind)) return new LinkedHashMap<>(Map.of("HDSL_PERSISTENCE_TEST", "1"));
		if (List.class.isAssignableFrom(kind)) return List.of(directory("test-directory-persistence"));
		if (Paint.class.isAssignableFrom(kind)) return Color.RED;
		if (ThemeColor.class == kind) return ThemeColor.of(Color.RED);
		if (SupportedLocale.class == kind) {
			return SupportedLocale.getSupportedLocales()
			                      .stream()
			                      .filter(locale -> !locale.isDefault())
			                      .findFirst()
			                      .orElse(null);
		}
		if (kind.isEnum()) return kind.getEnumConstants()[0];
		return null;
	}

	private static GameDirectory directory(String id) {
		return new GameDirectory(id, "/tmp/" + id, null);
	}

	private static Object get(Object property) {
		if (property instanceof javafx.beans.property.StringProperty value) return value.get();
		if (property instanceof javafx.beans.property.BooleanProperty value) return value.get();
		if (property instanceof javafx.beans.property.DoubleProperty value) return value.get();
		if (property instanceof javafx.beans.property.ObjectProperty<?> value) return value.get();
		throw new AssertionError(property + " holds nothing this test can read");
	}

	private static void set(Object property, Object value) {
		if (property instanceof javafx.beans.property.StringProperty strings) {
			strings.set((String) value);
		} else if (property instanceof javafx.beans.property.BooleanProperty booleans) {
			booleans.set((Boolean) value);
		} else if (property instanceof javafx.beans.property.DoubleProperty numbers) {
			numbers.set((Double) value);
		} else if (property instanceof javafx.beans.property.ObjectProperty<?> object) {
			@SuppressWarnings("unchecked")
			javafx.beans.property.ObjectProperty<Object> mutable =
					(javafx.beans.property.ObjectProperty<Object>) object;
			mutable.set(value);
		} else {
			throw new AssertionError(property + " takes nothing this test can write");
		}
	}
}
