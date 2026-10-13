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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.ThemeReference;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Every setting survives a write to the settings file and a read back
 *
 * A setting omitted from the snapshot, or mapped to the wrong field, takes effect at runtime
 * and is lost at the next start.
 * A setting added later is covered without editing this file: the properties are found through
 * their accessors.
 */
class SettingsPersistenceTest {
	// The game folder the made-up list holds and the one it selects: the two are the same id
	private static final String DIRECTORY_ID = "test-directory-persistence";

	// The properties that are deliberately not stored, each with its reason
	private static final List<String> NOT_STORED = List.of();

	// What the settings held before this test wrote its own, by property name
	private final Map<String, Object> was = new LinkedHashMap<>();

	@Test
	void everySettingAPropertyExposesIsCapturedAndRestored() throws Exception {
		// Initializing subscribes the manager to these properties, so it has to happen before this test writes to them
		GameDirectoryManager.init();
		LauncherSettings settings = SettingsManager.settings();

		List<String> unmade = new ArrayList<>();
		Map<String, Object> written = new LinkedHashMap<>();
		for (Method accessor : propertyAccessors()) {
			String name = nameOf(accessor);
			if (NOT_STORED.contains(name)) continue;

			Object property = accessor.invoke(settings);
			was.put(name, get(property));

			Object made = madeUp(kindOf(accessor), name);
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

	private static Class<?> kindOf(Method accessor) {
		Class<?> type = accessor.getReturnType();
		if (StringProperty.class.isAssignableFrom(type)) return String.class;
		if (BooleanProperty.class.isAssignableFrom(type)) return Boolean.class;
		if (DoubleProperty.class.isAssignableFrom(type)) return Double.class;
		if (!ObjectProperty.class.isAssignableFrom(type)) return type;

		// ObjectProperty<T> exposes its value type through the generic argument
		Type heldType = ((ParameterizedType) accessor.getGenericReturnType()).getActualTypeArguments()[0];
		if (heldType instanceof Class<?> heldClass) return heldClass;
		if (heldType instanceof ParameterizedType parameterized) {
			// A parameterized value answers with its raw type
			return (Class<?>) parameterized.getRawType();
		}
		return Object.class;
	}

	// All a made-up value has to be is different from the one already there
	private static Object madeUp(Class<?> kind, String name) {
		// The value has to be the id the list holds, or the manager re-resolves the selection during the round trip
		if ("selectedGameDirectoryId".equals(name)) return DIRECTORY_ID;
		if (kind == String.class) return "persistence-test";
		if (kind == Boolean.class) return true;
		if (kind == Double.class || kind == double.class) return 42.5;
		if (kind == Integer.class || kind == int.class) return 42;
		if (kind == GameDirectory.class) return directory(DIRECTORY_ID);
		if (kind == ThemeReference.class) return new ThemeReference("test-pack-persistence", null);
		if (Map.class.isAssignableFrom(kind)) return new LinkedHashMap<>(Map.of("HDSL_PERSISTENCE_TEST", "1"));
		if (List.class.isAssignableFrom(kind)) return List.of(directory(DIRECTORY_ID));
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
		if (property instanceof StringProperty value) return value.get();
		if (property instanceof BooleanProperty value) return value.get();
		if (property instanceof DoubleProperty value) return value.get();
		if (property instanceof ObjectProperty<?> value) return value.get();
		throw new AssertionError(property + " holds nothing this test can read");
	}

	private static void set(Object property, Object value) {
		if (property instanceof StringProperty strings) {
			strings.set((String) value);
		} else if (property instanceof BooleanProperty booleans) {
			booleans.set((Boolean) value);
		} else if (property instanceof DoubleProperty numbers) {
			numbers.set((Double) value);
		} else if (property instanceof ObjectProperty<?> object) {
			@SuppressWarnings("unchecked")
			ObjectProperty<Object> mutable = (ObjectProperty<Object>) object;
			mutable.set(value);
		} else {
			throw new AssertionError(property + " takes nothing this test can write");
		}
	}
}
