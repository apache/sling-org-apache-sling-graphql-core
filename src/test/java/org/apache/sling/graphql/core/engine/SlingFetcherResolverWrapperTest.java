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

import java.util.HashMap;
import java.util.Map;

import graphql.GraphQLContext;
import graphql.TypeResolutionEnvironment;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SlingFetcherResolverWrapperTest {

    @Mock
    private SlingDataFetcherSelector dataFetcherSelector;

    @Mock
    private SlingTypeResolverSelector typeResolverSelector;

    @Mock
    private Resource resource;

    @Test
    public void dataFetcher_missingResourceThrows() throws Exception {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getGraphQlContext()).thenReturn(GraphQLContext.newContext().build());

        SlingDataFetcherWrapper<Object> wrapper =
                new SlingDataFetcherWrapper<>(dataFetcherSelector, "test/fetcher", null, null);
        try {
            wrapper.get(env);
            fail("Expected SlingGraphQLException");
        } catch (SlingGraphQLException e) {
            assertTrue(e.getMessage().contains("missing the request Resource"));
        }
    }

    @Test
    public void dataFetcher_missingServiceFallsBackToProperty() throws Exception {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getGraphQlContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        when(dataFetcherSelector.getSlingFetcher("test/fetcher")).thenReturn(null);
        Map<String, Object> source = new HashMap<>();
        source.put("path", "/content/from-property");
        when(env.getSource()).thenReturn(source);
        when(env.getField()).thenReturn(Field.newField("path").build());

        SlingDataFetcherWrapper<Object> wrapper =
                new SlingDataFetcherWrapper<>(dataFetcherSelector, "test/fetcher", "opts", "src");
        assertEquals("/content/from-property", wrapper.get(env));
    }

    @Test
    public void dataFetcher_sameWrapperSeesLaterRegistration() throws Exception {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getGraphQlContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        Map<String, Object> source = new HashMap<>();
        source.put("path", "/content/from-property");
        when(env.getSource()).thenReturn(source);
        when(env.getField()).thenReturn(Field.newField("path").build());
        @SuppressWarnings("unchecked")
        SlingDataFetcher<Object> fetcher = mock(SlingDataFetcher.class);
        when(fetcher.get(any())).thenReturn("from-osgi");
        when(dataFetcherSelector.getSlingFetcher("test/fetcher"))
                .thenReturn(null)
                .thenReturn(fetcher);

        SlingDataFetcherWrapper<Object> wrapper =
                new SlingDataFetcherWrapper<>(dataFetcherSelector, "test/fetcher", "opts", "src");
        assertEquals("/content/from-property", wrapper.get(env));
        assertSame("from-osgi", wrapper.get(env));
        verify(fetcher).get(any());
    }

    @Test
    public void dataFetcher_delegatesToLiveService() throws Exception {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getGraphQlContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        @SuppressWarnings("unchecked")
        SlingDataFetcher<Object> fetcher = mock(SlingDataFetcher.class);
        when(dataFetcherSelector.getSlingFetcher("test/fetcher")).thenReturn(fetcher);
        when(fetcher.get(any())).thenReturn("ok");

        SlingDataFetcherWrapper<Object> wrapper =
                new SlingDataFetcherWrapper<>(dataFetcherSelector, "test/fetcher", "opts", "src");
        assertSame("ok", wrapper.get(env));
        verify(fetcher).get(any());
    }

    @Test
    public void typeResolver_missingResourceThrows() {
        TypeResolutionEnvironment env = mock(TypeResolutionEnvironment.class);
        when(env.getGraphQLContext()).thenReturn(GraphQLContext.newContext().build());

        SlingTypeResolverWrapper wrapper =
                new SlingTypeResolverWrapper(typeResolverSelector, "test/resolver", null, null);
        try {
            wrapper.getType(env);
            fail("Expected SlingGraphQLException");
        } catch (SlingGraphQLException e) {
            assertTrue(e.getMessage().contains("missing the request Resource"));
        }
    }

    @Test
    public void typeResolver_missingServiceReturnsNull() {
        TypeResolutionEnvironment env = mock(TypeResolutionEnvironment.class);
        when(env.getGraphQLContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        when(typeResolverSelector.getSlingTypeResolver("test/resolver")).thenReturn(null);

        SlingTypeResolverWrapper wrapper =
                new SlingTypeResolverWrapper(typeResolverSelector, "test/resolver", "opts", "src");
        assertNull(wrapper.getType(env));
    }

    @Test
    public void typeResolver_sameWrapperSeesLaterRegistration() {
        TypeResolutionEnvironment env = mock(TypeResolutionEnvironment.class);
        when(env.getGraphQLContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        @SuppressWarnings("unchecked")
        SlingTypeResolver<Object> resolver = mock(SlingTypeResolver.class);
        GraphQLObjectType objectType = mock(GraphQLObjectType.class);
        when(resolver.getType(any())).thenReturn(objectType);
        when(typeResolverSelector.getSlingTypeResolver("test/resolver"))
                .thenReturn(null)
                .thenReturn(resolver);

        SlingTypeResolverWrapper wrapper =
                new SlingTypeResolverWrapper(typeResolverSelector, "test/resolver", "opts", "src");
        assertNull(wrapper.getType(env));
        assertSame(objectType, wrapper.getType(env));
        verify(resolver).getType(any());
    }

    @Test
    public void typeResolver_delegatesWhenResultIsObjectType() {
        TypeResolutionEnvironment env = mock(TypeResolutionEnvironment.class);
        when(env.getGraphQLContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        @SuppressWarnings("unchecked")
        SlingTypeResolver<Object> resolver = mock(SlingTypeResolver.class);
        GraphQLObjectType objectType = mock(GraphQLObjectType.class);
        when(typeResolverSelector.getSlingTypeResolver("test/resolver")).thenReturn(resolver);
        when(resolver.getType(any())).thenReturn(objectType);

        SlingTypeResolverWrapper wrapper =
                new SlingTypeResolverWrapper(typeResolverSelector, "test/resolver", "opts", "src");
        assertSame(objectType, wrapper.getType(env));
        verify(resolver).getType(any());
    }

    @Test
    public void typeResolver_nonObjectTypeResultReturnsNull() {
        TypeResolutionEnvironment env = mock(TypeResolutionEnvironment.class);
        when(env.getGraphQLContext())
                .thenReturn(
                        GraphQLContext.newContext().of(Resource.class, resource).build());
        @SuppressWarnings("unchecked")
        SlingTypeResolver<Object> resolver = mock(SlingTypeResolver.class);
        when(typeResolverSelector.getSlingTypeResolver("test/resolver")).thenReturn(resolver);
        when(resolver.getType(any())).thenReturn("not-a-graphql-object-type");

        SlingTypeResolverWrapper wrapper =
                new SlingTypeResolverWrapper(typeResolverSelector, "test/resolver", null, null);
        assertNull(wrapper.getType(env));
    }
}
