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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

public class RankedSchemaProvidersTest {
    private final String DEFAULT_SCHEMA_PROVIDER_OUTPUT = "";
    private final int DEFAULT_SERVICE_RANKING = DefaultSchemaProvider.SERVICE_RANKING;
    private ResourceResolver resourceResolver;

    @Rule
    public final OsgiContext context = new OsgiContext();

    @Before
    public void setup() {
        final ServletResolver sr = Mockito.mock(ServletResolver.class);
        context.bundleContext().registerService(ServletResolver.class, sr, null);
        context.registerInjectActivateService(new DefaultSchemaProvider(), Constants.SERVICE_RANKING,
                DefaultSchemaProvider.SERVICE_RANKING);
        context.registerInjectActivateService(new RankedSchemaProviders());
        resourceResolver = Mockito.mock(ResourceResolver.class);
    }

    private void assertProvider(String info, String expected) throws IOException {
        final RankedSchemaProviders sp = context.getService(RankedSchemaProviders.class);
        final Resource r = Mockito.mock(Resource.class);
        Mockito.when(r.getResourceResolver()).thenReturn(resourceResolver);
        Mockito.when(r.getPath()).thenReturn("/421");
        assertEquals(info, expected, sp.getSchema(r, null));
    }

    private ServiceRegistration<?> registerProvider(String name, int serviceRanking) throws IOException {
        final SchemaProvider sp = Mockito.mock(SchemaProvider.class);
        Mockito.when(sp.toString()).thenReturn(name);
        Mockito.when(sp.getSchema(Mockito.any(), Mockito.any())).thenReturn(name);
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, serviceRanking);
        return context.bundleContext().registerService(SchemaProvider.class, sp, props);
    }

    @Test
    public void defaultProviderActive() throws IOException {
        assertProvider("Default", DEFAULT_SCHEMA_PROVIDER_OUTPUT);
    }

    @Test
    public void providerPriorities() throws IOException {
        assertProvider("Beginning", DEFAULT_SCHEMA_PROVIDER_OUTPUT);

        registerProvider("Y", DEFAULT_SERVICE_RANKING + 1);
        assertProvider("After Y", DEFAULT_SCHEMA_PROVIDER_OUTPUT);

        final ServiceRegistration<?> z = registerProvider("Z", DEFAULT_SERVICE_RANKING - 1);
        assertProvider("After Z", "Z");

        final ServiceRegistration<?> a = registerProvider("A", 1);
        assertProvider("After A", "A");

        final ServiceRegistration<?> b = registerProvider("B", 2);
        assertProvider("After B", "A");

        a.unregister();
        assertProvider("After removing A", "B");

        b.unregister();
        assertProvider("After removing B", "Z");

        z.unregister();
        assertProvider("After removing Z", DEFAULT_SCHEMA_PROVIDER_OUTPUT);
    }

    @Test
    public void nullProviderResultIgnored() throws IOException {
        assertProvider("Beginning", DEFAULT_SCHEMA_PROVIDER_OUTPUT);
        registerProvider(null, 1);
        assertProvider("After null", DEFAULT_SCHEMA_PROVIDER_OUTPUT);
        registerProvider("A", 1);
        assertProvider("After A", "A");
    }
}