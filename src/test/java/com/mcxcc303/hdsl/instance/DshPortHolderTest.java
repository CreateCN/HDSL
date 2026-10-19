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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies the process lookup behind the port-conflict dialog.
///
/// The parsing is tested on the output each platform really prints — including a machine whose
/// language is not English, which is where a parser that reads the state word breaks. The lookup
/// is tested against a real socket, because naming the wrong process is what would make the
/// dialog misleading.
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

	/// Names the process for the dialog, including a quoted path that contains spaces.
	@Test
	void namesTheProcessFromItsCommandLine() {
		assertEquals("node.exe",
		             DshPortHolder.nameOf("\"C:\\Program Files\\nodejs\\node.exe\" -e x"),
		             "a quoted Windows path is one token, not the word before the first space");
		assertEquals("node", DshPortHolder.nameOf("/usr/bin/node script.js"));
		assertEquals("python3", DshPortHolder.nameOf("/usr/bin/python3"));
		assertNull(DshPortHolder.nameOf(null));
		assertNull(DshPortHolder.nameOf("   "));
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
			assertNotNull(found.get().name(),
			              "the process's name should be readable for this JVM");
		}
	}
}
