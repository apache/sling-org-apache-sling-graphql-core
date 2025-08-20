/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.graphql.core.engine;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import graphql.schema.idl.TypeDefinitionRegistry;

import static org.mockito.Mockito.when;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class DefaultQueryExecutorCacheTest {

    @Mock
    private RankedSchemaProviders schemaProvider;

    @Mock
    private SlingDataFetcherSelector dataFetcherSelector;

    @Mock
    private SlingTypeResolverSelector typeResolverSelector;

    @Mock
    private SlingScalarsProvider scalarsProvider;

    @Mock
    private Resource resource;

    @InjectMocks
    private DefaultQueryExecutor executor;

    @Before
    public void setUp() {
        // Activate the component with default config
        DefaultQueryExecutor.Config config = mock(DefaultQueryExecutor.Config.class);
        when(config.schemaCacheSize()).thenReturn(10);
        when(config.maxQueryTokens()).thenReturn(15000);
        when(config.maxWhitespaceTokens()).thenReturn(200000);
        when(config.maxFieldCount()).thenReturn(100000);

        executor.activate(config);

        when(resource.getPath()).thenReturn("/content/test");
    }

    @Test
    public void testGetTypeDefinitionRegistry_ValidSDL() {
        String validSDL = "type Query { hello: String }";
        String[] selectors = { "test" };

        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(validSDL, resource, selectors);

        assertNotNull(result);
        assertTrue(result.getType("Query").isPresent());
    }

    @Test
    public void testGetTypeDefinitionRegistry_InvalidSDL() {
        String invalidSDL = "invalid graphql syntax {";
        String[] selectors = { "test" };

        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(invalidSDL, resource, selectors);

        assertNull(result);
    }

    @Test
    public void testGetTypeDefinitionRegistry_CacheDisabled() {
        DefaultQueryExecutor.Config config = mock(DefaultQueryExecutor.Config.class);
        when(config.schemaCacheSize()).thenReturn(0);
        executor.activate(config);

        String sdl = "type Query { hello: String }";
        String[] selectors = { "test" };

        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(sdl, resource, selectors);

        assertNotNull(result);
    }

    @Test
    public void testGetTypeDefinitionRegistry_Extended() {
        Resource resource2 = mock(Resource.class);
        when(resource2.getPath()).thenReturn("/content/test2");

        DefaultQueryExecutor.Config config = mock(DefaultQueryExecutor.Config.class);
        when(config.schemaCacheSize()).thenReturn(2);
        executor.activate(config);

        String sdl = "type Query { hello: String }";
        String[] selectors = { "test" };

        executor.getTypeDefinitionRegistry(sdl, resource2, selectors);
        executor.getTypeDefinitionRegistry(sdl + " ", resource, selectors);
        executor.getTypeDefinitionRegistry(sdl + "  ", resource, selectors);
        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(sdl, resource2, selectors);
        assertNotNull(result);
    }
}