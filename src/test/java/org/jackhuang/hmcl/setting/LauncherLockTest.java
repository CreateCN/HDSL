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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// The lock that keeps one launcher to one home.
///
/// What is pinned here is the part one process cannot see for itself: that the lock a launcher takes
/// is one another process cannot take, and that it is handed back when that process ends. The rest —
/// the window taking it at start-up, a command taking it to write — is wiring around these answers.
class LauncherLockTest {

	/// The line the child process prints its answer on.
	private static final String ANSWER = "LOCK:";

	@Test
	void oneProcessAtATime(@TempDir Path directory) {
		LauncherLock first = new LauncherLock(directory.resolve("launcher.lock"));
		LauncherLock second = new LauncherLock(directory.resolve("launcher.lock"));

		assertTrue(first.acquire());
		assertTrue(first.held());
		assertFalse(second.acquire(), "a lock already held is not handed out twice");
		assertFalse(second.held());

		first.release();
		assertFalse(first.held());
		assertTrue(second.acquire(), "and it is handed out once it is let go");
		second.release();
	}

	@Test
	void takingItTwiceIsTheSameAsTakingItOnce(@TempDir Path directory) {
		LauncherLock lock = new LauncherLock(directory.resolve("launcher.lock"));
		assertTrue(lock.acquire());
		assertTrue(lock.acquire(), "the process holding it is not refused by itself");
		lock.release();
		assertFalse(lock.held());
	}

	@Test
	void anotherProcessIsRefusedWhileItIsHeld(@TempDir Path directory) throws Exception {
		Path file = directory.resolve("launcher.lock");
		LauncherLock lock = new LauncherLock(file);

		assertTrue(lock.acquire());
		assertEquals("held:" + ProcessHandle.current().pid(), tryInAnotherProcess(file),
		             "the lock is not one process's private idea, and the refusal can name its holder");
		lock.release();
		assertEquals("taken", tryInAnotherProcess(file), "and a lock nobody holds is nobody's");
	}

	/// Who holds the lock is written beside it, not in it.
	///
	/// The distinction is invisible here and fatal on Windows, where a lock on a file is mandatory:
	/// any other handle that reads the locked range is refused, so a note inside the lock could be
	/// written by its holder and read by nobody. This test is the one that fails there if the note
	/// ever moves back in.
	@Test
	void whoHoldsTheLockIsWrittenBesideIt(@TempDir Path directory) throws IOException {
		Path file = directory.resolve("launcher.lock");
		LauncherLock lock = new LauncherLock(file);
		assertTrue(lock.acquire());

		Optional<LauncherLock.Holder> holder = lock.holder();
		assertTrue(holder.isPresent(), "a refusal has to be able to name the process that owns it");
		assertEquals(ProcessHandle.current().pid(), holder.get().pid());
		assertEquals(0, Files.size(file),
		             "the locked file itself says nothing: a reader of it is refused on Windows");

		lock.release();
		assertTrue(lock.holder().isEmpty(), "and a lock nobody holds names nobody");
	}

	/// Runs the lock in a process of its own and returns what it made of it.
	///
	/// A process of its own is the whole point of the test: a lock that only this JVM respected would
	/// be a flag, and the window and the command line are two JVMs.
	private static String tryInAnotherProcess(Path file) throws IOException, InterruptedException {
		List<String> command = new ArrayList<>(javaCommand());
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(TryLock.class.getName());
		command.add(file.toString());

		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int code = process.waitFor();
		assertEquals(0, code, output);

		return output.lines()
		             .filter(line -> line.startsWith(ANSWER))
		             .map(line -> line.substring(ANSWER.length()).trim())
		             .findFirst()
		             .orElseThrow(() -> new AssertionError("the child said nothing: " + output));
	}

	/// Builds the command that runs a JVM on this test's own classpath.
	///
	/// @return the command
	private static List<String> javaCommand() {
		return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString());
	}

	/// Takes the lock, or does not, and says which.
	public static final class TryLock {
		private TryLock() {
		}

		/// Runs in a process of its own.
		///
		/// @param args the lock file to try
		public static void main(String[] args) {
			LauncherLock lock = new LauncherLock(Path.of(args[0]));
			if (lock.acquire()) {
				System.out.println(ANSWER + "taken");
				return;
			}
			// Refused: what it can still do is say who refused it, which is what the note beside the
			// lock is for.
			String holder = lock.holder().map(who -> Long.toString(who.pid())).orElse("nobody");
			System.out.println(ANSWER + "held:" + holder);
		}
	}
}
