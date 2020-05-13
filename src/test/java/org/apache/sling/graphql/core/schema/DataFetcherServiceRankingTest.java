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
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

import static org.apache.sling.graphql.core.schema.TestDataFetcherProvider.assertFetcher;

import java.io.IOException;

public class DataFetcherServiceRankingTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    @Test
    public void testDataFetcherRankings() throws IOException {
        final DataFetcherSelector s = new DataFetcherSelector(context.bundleContext());
        final String fetcherDef = "fetch:ns/name";
        final String ns = "ns";
        final String name = "name";

        // Verify that given the same namespace and name we always get 
        // the DataFetcher which has the lowest service ranking.
        assertFetcher(s, fetcherDef, null);

        final ServiceRegistration<?> reg42 = new TestDataFetcherProvider(ns, name, 42).register(context.bundleContext());
        assertFetcher(s, fetcherDef, "DF#ns#name#42");

        final ServiceRegistration<?> reg40 = new TestDataFetcherProvider(ns, name, 40).register(context.bundleContext());
        assertFetcher(s, fetcherDef, "DF#ns#name#40");

        final ServiceRegistration<?> reg43 = new TestDataFetcherProvider(ns, name, 43).register(context.bundleContext());
        assertFetcher(s, fetcherDef, "DF#ns#name#40");

        reg42.unregister();
        assertFetcher(s, fetcherDef, "DF#ns#name#40");

        reg40.unregister();
        assertFetcher(s, fetcherDef, "DF#ns#name#43");

        reg43.unregister();
        assertFetcher(s, fetcherDef, null);
    }
}
