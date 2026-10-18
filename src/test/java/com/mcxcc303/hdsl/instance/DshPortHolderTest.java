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

import com.mcxcc303.hdsl.runtime.DshNodeRuntime;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Verifies the process lookup and the stopping rules behind the port-conflict prompt.
///
/// The parsing is tested on the output each platform really prints — including a machine whose
/// language is not English, which is where a parser that reads the state word breaks. The lookup
/// and the stop are tested against real sockets and a real process, because that is the part whose
/// failure would show up as a wrong process being killed.
class DshPortHolderTest {
	/// Reads a pid out of `netstat -ano` output the way Windows prints it.
	@Test
	void readsThePidOutOfNetstatRows() {
		List<String> rows = List.of(
				"活动连接",
				"",
				"  协议  本地地址          外部地址        状态            PID",
				// A German Windows writes the state word in German, and only the shape of the row
				// says it is a listener.
				"  TCP    0.0.0.0:3081           0.0.0.0:0              ABHÖREN         4321",
				"  TCP    127.0.0.1:3081         127.0.0.1:52344        HERGESTELLT     9999",
				"  TCP    [::]:3081              [::]:0                 LISTENING       4321",
				"  TCP    0.0.0.0:3082           0.0.0.0:0              LISTENING       1111",
				"  UDP    0.0.0.0:3081           *:*                                    7777");

		assertEquals(OptionalLong.of(4321), DshPortHolder.pidFromNetstat(rows, 3081),
		             "the listening row is the one whose foreign address is the wildcard");
		assertEquals(OptionalLong.of(1111), DshPortHolder.pidFromNetstat(rows, 3082));
		assertTrue(DshPortHolder.pidFromNetstat(rows, 3083).isEmpty());
		assertTrue(DshPortHolder.pidFromNetstat(List.of(), 3081).isEmpty());
	}

	/// Reads the socket inodes out of the two `/proc/net` tables.
	@Test
	void readsTheInodesOutOfProcRows() {
		String header = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt" +
		                "   uid  timeout inode";
		// 0x0C61 is 3169.
		String listening = "   0: 0100007F:0C61 00000000:0000 0A 00000000:00000000 00:00000000" +
		                   " 00000000  1000        0 123456 1 0000000000000000 100 0 0 10 0";
		String established = "   1: 0100007F:0C61 0100007F:1F90 01 00000000:00000000 00:00000000" +
		                     " 00000000  1000        0 654321 1 0000000000000000 20 4 30 10 -1";
		String otherPort = "   2: 0100007F:0C62 00000000:0000 0A 00000000:00000000 00:00000000" +
		                   " 00000000  1000        0 999999 1 0000000000000000 100 0 0 10 0";
		String listening6 = "   3: 00000000000000000000000001000000:0C61 00000000000000000000000000000000:0000" +
		                    " 0A 00000000:00000000 00:00000000 00000000  1000        0 777777 1" +
		                    " 0000000000000000 100 0 0 10 0";

		assertEquals(Set.of("123456"), DshPortHolder.inodesFromProc(
				List.of(header, listening, established, otherPort), List.of(), 3169),
		             "an established row does not own the listening socket");
		assertEquals(Set.of("777777"), DshPortHolder.inodesFromProc(List.of(), List.of(listening6), 3169));
		assertTrue(DshPortHolder.inodesFromProc(List.of(header), List.of(), 3169).isEmpty());
	}

	/// Reads the pids `lsof -t` prints.
	@Test
	void readsThePidsOutOfLsofOutput() {
		assertEquals(List.of(1234L, 5678L),
		             DshPortHolder.pidsFromLsof(List.of("1234", " 5678 ", "", "lsof: warning")));
		assertTrue(DshPortHolder.pidsFromLsof(List.of()).isEmpty());
	}

	/// Matches a path as a whole path, in either separator spelling.
	@Test
	void recognisesDeepSeekHarness() {
		assertTrue(DshPortHolder.looksLikeDsh(
				"/usr/bin/node /opt/hdsl/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js --profile web --port 3081"));
		assertTrue(DshPortHolder.looksLikeDsh(
				"\"C:\\Program Files\\nodejs\\node.exe\" \"C:\\hdsl\\dsh\\node_modules\\@deepseek-ai\\dsh\\lib\\bin.js\""));
		assertFalse(DshPortHolder.looksLikeDsh("/usr/bin/node /home/someone/dsh/server.js"),
		            "a path that merely contains the word dsh is not the package");
		assertFalse(DshPortHolder.looksLikeDsh("/usr/bin/python3 -m http.server 3081"));
		assertFalse(DshPortHolder.looksLikeDsh(null));
	}

	/// Matches a path as a whole path, in either separator spelling.
	@Test
	void matchesAWholePathOnly() throws IOException {
		Path directory = Files.createTempDirectory("port-path").toRealPath();

		assertTrue(DshPortHolder.mentionsPath("node " + directory.resolve("dsh") + "/lib/bin.js", directory));
		assertTrue(DshPortHolder.mentionsPath("node " + directory.toString().replace('/', '\\') + "\\dsh\\lib",
		                                      directory),
		           "the same path spelled with backslashes is still the same path");
		assertFalse(DshPortHolder.mentionsPath("node " + directory + "x/dsh/lib/bin.js", directory),
		            "a longer name that starts the same way is another path");
		assertFalse(DshPortHolder.mentionsPath(null, directory));
	}

	/// Identifies who holds a port against the instances the launcher knows.
	@Test
	void identifiesTheProgramHoldingAPort() throws Exception {
		Path root = Files.createTempDirectory("port-origin");
		DshInstance target = instanceAt("target", root.resolve("target"));
		DshInstance other = instanceAt("other", root.resolve("other"));

		assertEquals(DshPortHolder.Origin.THIS_INSTANCE,
		             DshPortHolder.identify("node " + target.dshEntryPoint() + " --profile web", target,
		                                    List.of(target, other)).origin());
		DshPortHolder.Identification byOther = DshPortHolder.identify(
				"node " + other.dshEntryPoint(), target, List.of(target, other));
		assertEquals(DshPortHolder.Origin.OTHER_INSTANCE, byOther.origin());
		assertEquals("other", byOther.instanceId());
		assertEquals(DshPortHolder.Origin.DSH_UNMANAGED,
		             DshPortHolder.identify("/usr/bin/node /usr/lib/node_modules/@deepseek-ai/dsh/lib/bin.js web",
		                                    target, List.of(target, other)).origin());
		assertEquals(DshPortHolder.Origin.NOT_DSH,
		             DshPortHolder.identify("/usr/bin/python3 -m http.server", target,
		                                    List.of(target, other)).origin());
		assertEquals(DshPortHolder.Origin.NOT_DSH,
		             DshPortHolder.identify("/usr/bin/python3 " + target.dshDirectory() + "/notes.txt",
		                                    target, List.of(target, other)).origin(),
		             "naming an instance directory is not evidence of running DeepSeek Harness");
		assertEquals(DshPortHolder.Origin.UNKNOWN,
		             DshPortHolder.identify(null, target, List.of(target, other)).origin(),
		             "a command line that could not be read is not evidence that it is not DSH");
		assertEquals(DshPortHolder.Origin.UNKNOWN,
		             DshPortHolder.identify("/usr/bin/node", target, List.of(target, other)).origin(),
		             "an executable without its arguments says nothing about what it runs");
	}

	/// Builds an instance whose own directory is a chosen one.
	private static DshInstance instanceAt(String id, Path directory) {
		return new DshInstance(id,
		                       "0.2.1-rc.2",
		                       "web",
		                       directory.resolve("workspace").toString(),
		                       DshNodeRuntime.SYSTEM,
		                       DshHomeMode.ISOLATED,
		                       null,
		                       List.of(),
		                       Map.of(),
		                       DshInstanceIcon.DEFAULT.id(),
		                       null,
		                       DshPortMode.AUTO,
		                       3081,
		                       0L,
		                       directory.toString());
	}

	/// Finds the listener this very test opened.
	@Test
	void findsTheProcessThatBindsAPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			int port = socket.getLocalPort();

			Optional<DshPortHolder.Holder> found = DshPortHolder.find(port);

			assertTrue(found.isPresent(), "the test's own listener should be found on port " + port);
			assertEquals(ProcessHandle.current().pid(), found.get().pid(),
			             "the process holding the port is this JVM");
		}
	}

	/// Refuses to stop a program that is not DeepSeek Harness, whatever port it holds.
	@Test
	void refusesToStopAProcessThatIsNotDeepSeekHarness() throws Exception {
		int port = freePort();
		Process child = startBinder(port, false);
		try {
			assertTrue(await(() -> !DshPorts.isFree(port), 15_000), "the child never bound port " + port);
			DshPortHolder.Holder found = DshPortHolder.find(port).orElseThrow();

			assertEquals(DshPortHolder.KillResult.REFUSED_NOT_DSH, DshPortHolder.kill(found),
			             "a port occupier the launcher cannot identify as DSH is never stopped");
			assertTrue(child.isAlive(), "the ordinary program still holds its port");
		} finally {
			stopChild(child);
		}
		assertEquals(DshPortHolder.KillResult.REFUSED_NOT_DSH,
		             DshPortHolder.kill(new DshPortHolder.Holder(Integer.MAX_VALUE - 1L, null, null)),
		             "a process whose command line cannot be read is not confirmed either");
	}

	/// Refuses to stop the launcher's own process.
	@Test
	void refusesToStopTheLauncherItself() {
		DshPortHolder.Holder self = new DshPortHolder.Holder(
				ProcessHandle.current().pid(),
				"node /somewhere/node_modules/@deepseek-ai/dsh/lib/bin.js",
				Instant.now());

		assertEquals(DshPortHolder.KillResult.REFUSED_SELF, DshPortHolder.kill(self));
		assertTrue(ProcessHandle.current().isAlive(), "refusing means not being stopped");
	}

	/// Reports a process that is already gone instead of failing.
	@Test
	void reportsAProcessThatIsGone() {
		DshPortHolder.Holder gone = new DshPortHolder.Holder(
				Integer.MAX_VALUE - 1L,
				"node /somewhere/node_modules/@deepseek-ai/dsh/lib/bin.js",
				null);

		assertEquals(DshPortHolder.KillResult.NOT_RUNNING, DshPortHolder.kill(gone));
	}

	/// Finds a real child process and stops it, and the port is free again.
	@Test
	void stopsAChildProcessAndFreesItsPort() throws Exception {
		int port = freePort();
		Process child = startBinder(port, true);
		try {
			assertTrue(await(() -> !DshPorts.isFree(port), 15_000), "the child never bound port " + port);

			Optional<DshPortHolder.Holder> found = DshPortHolder.find(port);
			assertTrue(found.isPresent(), "the child should be found on port " + port);
			assertEquals(child.pid(), found.get().pid(), "the process found should be the child");

			assertEquals(DshPortHolder.KillResult.KILLED, DshPortHolder.kill(found.get()));
			assertTrue(child.waitFor(10, TimeUnit.SECONDS), "the child should exit once it is stopped");
			assertTrue(DshPortHolder.awaitFree(port, Duration.ofSeconds(10)),
			           "port " + port + " should be free once the child is stopped");
		} finally {
			stopChild(child);
		}
	}

	/// Refuses to stop a process whose command line changed since it was shown.
	@Test
	void refusesWhenTheCommandLineNoLongerMatches() throws Exception {
		int port = freePort();
		Process child = startBinder(port, true);
		try {
			assertTrue(await(() -> !DshPorts.isFree(port), 15_000), "the child never bound port " + port);
			DshPortHolder.Holder found = DshPortHolder.find(port).orElseThrow();

			DshPortHolder.Holder spoofed = new DshPortHolder.Holder(
					found.pid(),
					"node /elsewhere/node_modules/@deepseek-ai/dsh/lib/bin.js",
					found.startedAt());

			assertEquals(DshPortHolder.KillResult.NOT_RUNNING, DshPortHolder.kill(spoofed),
			             "a pid whose command line no longer matches is not the process that was shown");
			assertTrue(child.isAlive(), "the process that could not be confirmed is still alive");
		} finally {
			stopChild(child);
		}
	}

	/// Refuses to stop a process whose start time no longer matches, which is what pid reuse looks like.
	@Test
	void refusesWhenTheStartTimeNoLongerMatches() throws Exception {
		int port = freePort();
		Process child = startBinder(port, true);
		try {
			assertTrue(await(() -> !DshPorts.isFree(port), 15_000), "the child never bound port " + port);
			DshPortHolder.Holder found = DshPortHolder.find(port).orElseThrow();
			assumeTrue(found.startedAt() != null, "this platform does not report a start time");

			DshPortHolder.Holder reused = new DshPortHolder.Holder(
					found.pid(),
					found.commandLine(),
					found.startedAt().plus(Duration.ofHours(1)));

			assertEquals(DshPortHolder.KillResult.NOT_RUNNING, DshPortHolder.kill(reused),
			             "a pid that began at another time is not the process that was shown");
			assertTrue(child.isAlive(), "the process behind the reused pid is still alive");
		} finally {
			stopChild(child);
		}
	}

	/// Waits for a port with a bound, and gives up when it stays taken.
	@Test
	void waitsForThePortWithABound() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			int port = socket.getLocalPort();

			assertFalse(DshPortHolder.awaitFree(port, Duration.ofMillis(300)));
			socket.close();
			assertTrue(DshPortHolder.awaitFree(port, Duration.ofSeconds(5)));
		}
	}

	/// Starts a child that binds a port, optionally naming the DSH package in its command line as
	/// the launcher's own `node .../@deepseek-ai/dsh/lib/bin.js` does.
	private static Process startBinder(int port, boolean markedAsDsh) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(javaBinary(), "-cp", System.getProperty("java.class.path"),
		                                            PortBinder.class.getName(), Integer.toString(port));
		if (markedAsDsh) {
			builder.command().add("@deepseek-ai/dsh/lib/bin.js");
		}
		return builder.redirectErrorStream(true)
		              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
		              .start();
	}

	/// Stops a child the test started, whether it is still running or not.
	private static void stopChild(Process child) throws InterruptedException {
		child.destroyForcibly();
		child.waitFor(10, TimeUnit.SECONDS);
	}

	/// Returns a port nothing is listening on right now.
	private static int freePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			return socket.getLocalPort();
		}
	}

	/// Returns the java command of the runtime running the tests, so the child is the same runtime.
	private static String javaBinary() {
		String name = OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "java.exe" : "java";
		return Path.of(System.getProperty("java.home"), "bin", name).toString();
	}

	/// Waits for a condition, bounded.
	private static boolean await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
		long deadline = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < deadline) {
			if (condition.getAsBoolean()) {
				return true;
			}
			Thread.sleep(50);
		}
		return condition.getAsBoolean();
	}
}
