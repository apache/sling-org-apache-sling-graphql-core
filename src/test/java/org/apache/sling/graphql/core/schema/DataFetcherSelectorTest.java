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

package org.apache.sling.graphql.core.schema;

import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.apache.sling.graphql.core.schema.TestDataFetcherProvider.assertFetcher;

import java.io.IOException;

public class DataFetcherSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    @Before
    public void setup() {
        new TestDataFetcherProvider("ns1", "name1").register(context.bundleContext());
        new TestDataFetcherProvider("ns1", "name2").register(context.bundleContext());
        new TestDataFetcherProvider("ns2", "name2").register(context.bundleContext());
    }

    @Test
    public void testGetDataFetcher() throws IOException {
        final DataFetcherSelector s = new DataFetcherSelector(context.bundleContext());
        assertFetcher(s, "ns1/name1", "DF#ns1#name1");
        assertFetcher(s, "ns1/name2", "DF#ns1#name2");
        assertFetcher(s, "ns2/name2", "DF#ns2#name2");
        assertFetcher(s, "ns2/othername", null);
        assertFetcher(s, "otherns/name2", null);
    }
}