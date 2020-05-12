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

import graphql.schema.DataFetcher;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.graphqljava.DataFetcherProvider;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

public class DataFetcherSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    static class TestDataFetcherProvider implements DataFetcherProvider {
        private final String namespace;
        private final String name;

        TestDataFetcherProvider(String namespace, String name) {
            this.namespace = namespace;
            this.name = name;
        }

        @Override
        @SuppressWarnings("unchecked")
        public @Nullable DataFetcher<Object> createDataFetcher(@NotNull Resource r, @NotNull String name,
            @Nullable String options, @Nullable String source) throws IOException {

            DataFetcher<Object> result = null;

            if(this.name.equals(name)) {
                result = (DataFetcher<Object>) Mockito.mock(DataFetcher.class);
                Mockito.when(result.toString()).thenReturn("DF#" + namespace + "#" + name);
            }
            return result;
        }

        public void register(BundleContext ctx) {
            final Dictionary<String, Object> props = new Hashtable<>();
            props.put(DataFetcherProvider.NAMESPACE_SERVICE_PROPERTY, namespace);
            ctx.registerService(DataFetcherProvider.class, this, props);
        }
    }

    @Before
    public void setup() {
        new TestDataFetcherProvider("ns1", "name1").register(context.bundleContext());
        new TestDataFetcherProvider("ns1", "name2").register(context.bundleContext());
        new TestDataFetcherProvider("ns2", "name2").register(context.bundleContext());
    }

    private void assertFetcher(DataFetcherSelector s, String def, String expected) throws IOException {
        final DataFetcher<Object> f = s.getDataFetcherForType(new DataFetcherDefinition(def), null);
        if(expected == null) {
            assertNull("Expected null DataFetcher for " + def, f);
        } else {
            assertNotNull("Expected non-null DataFetcher for " + def, f);
            assertEquals(expected, f.toString());
        }
    }

    @Test
    public void testGetDataFetcher() throws IOException {
        DataFetcherSelector s = new DataFetcherSelector(context.bundleContext());
        assertFetcher(s, "fetch:ns1/name1", "DF#ns1#name1");
        assertFetcher(s, "fetch:ns1/name2", "DF#ns1#name2");
        assertFetcher(s, "fetch:ns2/name2", "DF#ns2#name2");
        assertFetcher(s, "fetch:ns2/othername", null);
        assertFetcher(s, "fetch:otherns/name2", null);
    }
}
