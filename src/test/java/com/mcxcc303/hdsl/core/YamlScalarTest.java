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
package com.mcxcc303.hdsl.core;

import com.mcxcc303.hdsl.core.YamlScalar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// What a value looks like once it is written into a settings file.
///
/// The rule is that a value is quoted unless it is provably safe as a plain scalar, because the
/// failure it prevents is silent in the other direction: `@scope/name` written plain is not a
/// package name with an at sign in front of it, it is a file the harness cannot parse — and the
/// harness reads these files while it starts, so the launch dies rather than the value being wrong.
class YamlScalarTest {
    @Test
    void aValueYamlWouldReadAsSomethingElseIsQuoted() {
        // The case this exists for is a package name written into an injected setting:
        // `@deepseek-ai/…` in plain text is not a name with an at sign in front of it, it is a file
        // the harness cannot parse — and the harness reads these files as it starts, so the launch
        // dies rather than a value being wrong.
        assertEquals("\"@deepseek-ai/dsh-web-search-deepseek\"",
                YamlScalar.of("@deepseek-ai/dsh-web-search-deepseek"));
        assertEquals("\"true\"", YamlScalar.of("true"));
        // And the other half of the rule, which is what keeps this from rewriting a file the
        // launcher does not own: what YAML already reads as the same text is left alone.
        assertEquals("0.1.7-rc.1", YamlScalar.of("0.1.7-rc.1"));
        assertEquals("a@b", YamlScalar.of("a@b"), "the rule is about the first character");
    }
}
