/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.graphql.core.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LogSanitizerTest {
    private static final String UNCHANGED = "\u0451";
    private final LogSanitizer s = new LogSanitizer();
    private final String in;
    private final String out;

    @Parameters(name="{0}")
    public static Collection<Object[]> data() {
        final List<Object[]> result = new ArrayList<>();
        testCase(result, null, UNCHANGED);
        testCase(result, "nochange", UNCHANGED);
        testCase(result, "end\r\nof line", UNCHANGED);
        testCase(result, "with\ttab", "with_tab");
        testCase(result, "The quick brown fox jumps over the lazy dog!", UNCHANGED);
        testCase(result, "non-ASCII \u0041 z \u0080 \u0441", "non-ASCII A z _ _");
        testCase(result, "quotes \"ok\" and 'single'", UNCHANGED);
        testCase(result, "paren(theses)", UNCHANGED);
        testCase(result, "@fetcher('something')", UNCHANGED);
        return result;
    }

    private static void testCase(List<Object[]> result, String in, String out) {
        result.add(new Object[] { in, out});
    }

    public LogSanitizerTest(String in, String out) {
        this.in = in;
        this.out = out;
    }

    @Test
    public void verify() {
        if(out.equals(UNCHANGED)) {
            assertEquals(in, s.sanitize(in));
        } else {
            assertEquals(out, s.sanitize(in));
        }
    }
}