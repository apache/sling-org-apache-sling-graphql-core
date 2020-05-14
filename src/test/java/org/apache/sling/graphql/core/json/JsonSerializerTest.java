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
package org.apache.sling.graphql.core.json;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

import graphql.ExecutionResult;

public class JsonSerializerTest {
    private final JsonSerializer serializer = new JsonSerializer();

    private static final Map<String, Object> TEST_MAP = toMap("A", "bc", "farenheit", 451, "really", true, "itsNothing",
            null, "map", toMap("one", 1, "two", toMap("three", 3)));

    private static Map<String, Object> toMap(Object... keyValuePairs) {
        final Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            result.put(keyValuePairs[i].toString(), keyValuePairs[i + 1]);
        }
        return result;
    }

    private void assertHasTestData(String json) {
        assertThat(json, hasJsonPath("A", equalTo("bc")));
        assertThat(json, hasJsonPath("farenheit", equalTo(451)));
        assertThat(json, hasJsonPath("really", equalTo(true)));
        assertThat(json, hasJsonPath("itsNothing", equalTo(null)));
        assertThat(json, hasJsonPath("map.one", equalTo(1)));
        assertThat(json, hasJsonPath("map.two.three", equalTo(3)));
    }

    @Test
    public void testSendJSON() throws IOException {
        final ExecutionResult r = Mockito.mock(ExecutionResult.class);
        Mockito.when(r.toSpecification()).thenReturn(TEST_MAP);
        final StringWriter w = new StringWriter();
        serializer.sendJSON(w, r);
        assertHasTestData(w.toString());
    }

    @Test
    public void testToJSON() {
        assertHasTestData(serializer.toJSON(TEST_MAP));
    }

    @Test
    public void testToMap() throws UnsupportedEncodingException {
        final String json = serializer.toJSON(TEST_MAP);
        final Map<String, Object> map = serializer.JSONtoMaps(new ByteArrayInputStream(json.getBytes("UTF-8")));
        assertThat(map.get("A"), is("bc"));
        assertThat(map.get("farenheit"), is(451L));
        assertThat(map.get("really"), is(true));
        assertThat(map.get("itsNothing"), nullValue());
        assertThat(map.get("map").toString(), equalTo("{one=1, two={three=3}}"));
    }
}
