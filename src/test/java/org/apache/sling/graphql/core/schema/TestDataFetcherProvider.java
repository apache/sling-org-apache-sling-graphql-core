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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

class TestDataFetcherProvider implements DataFetcherProvider {
    private final String namespace;
    private final String name;
    private final int serviceRanking;

    static final int DEFAULT_SERVICE_RANKING = 451;

    TestDataFetcherProvider(String namespace, String name) {
        this(namespace, name, DEFAULT_SERVICE_RANKING);
    }

    TestDataFetcherProvider(String namespace, String name, int serviceRanking) {
        this.namespace = namespace;
        this.name = name;
        this.serviceRanking = serviceRanking;
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable DataFetcher<Object> createDataFetcher(@NotNull Resource r, @NotNull String name,
        @Nullable String options, @Nullable String source) throws IOException {

        DataFetcher<Object> result = null;

        if(this.name.equals(name)) {
            result = (DataFetcher<Object>) Mockito.mock(DataFetcher.class);
            final String stringValue = "DF#" + namespace + "#" + name 
                + (serviceRanking == DEFAULT_SERVICE_RANKING ? "" : "#" + serviceRanking);
            Mockito.when(result.toString()).thenReturn(stringValue);
        }
        return result;
    }

    public ServiceRegistration<?> register(BundleContext ctx) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("namespace", namespace);
        props.put(Constants.SERVICE_RANKING, serviceRanking);
        return ctx.registerService(DataFetcherProvider.class, this, props);
    }

    static void assertFetcher(DataFetcherSelector s, String nsAndName, String expected) throws IOException {
        final DataFetcher<Object> f = s.getDataFetcherForType(new DataFetcherDefinition(nsAndName, null, null), null);
        if(expected == null) {
            assertNull("Expected null DataFetcher for " + nsAndName, f);
        } else {
            assertNotNull("Expected non-null DataFetcher for " + nsAndName, f);
            assertEquals(expected, f.toString());
        }
    }
}
