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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// What adding a folder to the list does to it.
///
/// The window and the command line both go through this, which is the point of it being one method:
/// the identity of a folder is its path, and the entry an id — so "the same folder twice" has one
/// answer, and the settings file's other members, which name a folder by that id, keep naming it.
class GameDirectoryEntryTest {

	@Test
	void aFolderThatIsNotThereIsAdded(@TempDir Path directory) {
		List<GameDirectory> entries = new ArrayList<>();

		GameDirectory added = GameDirectoryManager.entryFor(entries, directory, null);

		assertEquals(List.of(added), entries);
		assertEquals(directory.toAbsolutePath().normalize(), added.directory());
	}

	@Test
	void aFolderThatIsThereIsTheEntryAlreadyThere(@TempDir Path directory) {
		List<GameDirectory> entries = new ArrayList<>();
		GameDirectory first = GameDirectoryManager.entryFor(entries, directory, null);

		GameDirectory again = GameDirectoryManager.entryFor(entries, directory, "another name");

		assertSame(first, again, "the entry the rest of the file already names is the one that comes back");
		assertEquals(1, entries.size(), "and one folder is one entry");
	}

	@Test
	void aFolderWrittenTwoWaysIsOneFolder(@TempDir Path directory) {
		List<GameDirectory> entries = new ArrayList<>();
		GameDirectory first = GameDirectoryManager.entryFor(entries, directory, null);

		assertSame(first, GameDirectoryManager.entryFor(entries, directory.resolve("."), null));
		assertEquals(1, entries.size());
	}

	@Test
	void aNameOfItsOwnIsUsedForANewEntry(@TempDir Path directory) {
		List<GameDirectory> entries = new ArrayList<>();

		assertEquals("mine", GameDirectoryManager.entryFor(entries, directory, "mine").customName());
	}
}
