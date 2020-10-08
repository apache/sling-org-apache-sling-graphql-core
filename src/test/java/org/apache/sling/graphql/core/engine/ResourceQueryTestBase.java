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
package org.apache.sling.graphql.core.engine;

import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.UUID;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.apache.sling.graphql.core.json.JsonSerializer;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.MockScriptServlet;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mockito;

import graphql.ExecutionResult;

public abstract class ResourceQueryTestBase {
    protected SchemaProvider schemaProvider;
    protected SlingDataFetcherSelector dataFetchersSelector;
    protected SlingTypeResolverSelector typeResolverSelector;
    protected SlingScalarsProvider scalarsProvider;
    protected Resource resource;

    @Rule
    public final OsgiContext context = new OsgiContext();

    @Before
    public void setup() {
        schemaProvider = new MockSchemaProvider(getTestSchemaName());
        final String resourceType = "RT-" + UUID.randomUUID();
        final String path = "/some/path/" + UUID.randomUUID();
        resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getPath()).thenReturn(path);
        Mockito.when(resource.getResourceType()).thenReturn(resourceType);

        setupAdditionalServices();

        // Our MockScriptServlet to simulates a script for unit tests, for the
        // integration tests we use a real script
        final MockScriptServlet mss = new MockScriptServlet();
        final ServletResolver servletResolver = Mockito.mock(ServletResolver.class);
        Mockito.when(servletResolver.resolveServlet(Mockito.any(Resource.class), Mockito.any(String.class))).thenReturn(mss);
        context.bundleContext().registerService(ServletResolver.class, servletResolver, null);

        context.registerInjectActivateService(new ScriptedDataFetcherProvider());
        context.registerInjectActivateService(new SlingDataFetcherSelector());
        context.registerInjectActivateService(new ScriptedTypeResolverProvider());
        context.registerInjectActivateService(new SlingTypeResolverSelector());
        dataFetchersSelector = context.getService(SlingDataFetcherSelector.class);
        typeResolverSelector = context.getService(SlingTypeResolverSelector.class);
        context.registerInjectActivateService(new SlingScalarsProvider());
        scalarsProvider = context.getService(SlingScalarsProvider.class);
    }

    protected String queryJSON(String stmt) throws Exception {
        return queryJSON(stmt, new String[]{});
    }

    protected String queryJSON(String stmt, String [] selectors) throws Exception {
        final ExecutionResult result = GraphQLResourceQuery.executeQuery(schemaProvider,
            dataFetchersSelector, typeResolverSelector, scalarsProvider, resource, selectors, stmt, Collections.emptyMap());
        assertTrue("Expecting no errors: " + result.getErrors(), result.getErrors().isEmpty());
        return new JsonSerializer().toJSON(result);
    }

    protected void setupAdditionalServices() {
    }

    protected String getTestSchemaName() {
        return "test-schema";
    }
}
