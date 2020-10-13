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

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import javax.json.Json;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.core.engine.DefaultQueryExecutor;
import org.apache.sling.graphql.core.engine.SlingDataFetcherSelector;
import org.apache.sling.graphql.core.engine.SlingTypeResolverSelector;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import net.minidev.json.JSONArray;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * Test the fields descriptions which are part of the schema as per
 * http://spec.graphql.org/June2018/#sec-Descriptions
 * 
 * Created for SLING-9479, to verify that our fetch: schema annotations
 * are ignored if separated from their types or fields by a comment line
 * containing just a hash character.
 */
public class SchemaDescriptionsTest {
    private Resource resource;
    private String schemaJson;

    private static final String SCHEMA_QUERY = "{ __schema { types { name description fields { name description }}}}";

    @Rule
    public final OsgiContext context = new OsgiContext();

    /** Haven't been able to get rid of the JSONArray in selected paths */
    static class DescriptionMatcher extends CustomMatcher<String> {

        private String toMatch;

        DescriptionMatcher(String toMatch) {
            super(toMatch);
            this.toMatch = stripSpace(toMatch);
        }

        static String stripSpace(String str) {
            return str.replaceAll("\\s+", " ").trim();
        }

        @Override
        public boolean matches(Object item) {
            String itemStr = null;
            if(item instanceof JSONArray) {
                itemStr = String.valueOf(((JSONArray)item).get(0));
            } else {
                itemStr = String.valueOf(item);
            }
            return toMatch.equals(stripSpace(itemStr));
        }
    }

    static Matcher<String> descriptionEquals(String str) {
        return new DescriptionMatcher(str);
    }
    
    @Before
    public void setup()  throws Exception {
        final String resourceType = "RT-" + UUID.randomUUID();
        final String path = "/some/path/" + UUID.randomUUID();
        resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getPath()).thenReturn(path);
        Mockito.when(resource.getResourceType()).thenReturn(resourceType);
        final ServletResolver servletResolver = Mockito.mock(ServletResolver.class);
        context.bundleContext().registerService(ServletResolver.class, servletResolver, null);
        context.registerInjectActivateService(new SlingDataFetcherSelector());
        context.registerInjectActivateService(new SlingTypeResolverSelector());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        context.registerInjectActivateService(new SlingScalarsProvider());
        context.registerService(SchemaProvider.class, new MockSchemaProvider("test-schema"));
        context.registerInjectActivateService(new RankedSchemaProviders());
        context.registerInjectActivateService(new DefaultQueryExecutor());
        schemaJson = queryJSON(SCHEMA_QUERY);
    }

    private String queryJSON(String stmt) throws Exception {
        QueryExecutor queryExecutor = context.getService(QueryExecutor.class);
        assertNotNull(queryExecutor);
        Map<String, Object> executionResult = queryExecutor.execute(stmt, Collections.emptyMap(), resource, new String[]{});
        return Json.createObjectBuilder(executionResult).build().asJsonObject().toString();
    }

    private void assertTypeDescription(String typeName, String expected) {
        final String path = String.format("$.data.__schema.types[?(@.name=='%s')].description", typeName);
        assertThat(schemaJson, hasJsonPath(path, descriptionEquals(expected)));
    }

    private void assertFieldDescription(String typeName, String fieldName, String expected) {
        final String path = String.format(
            "$.data.__schema.types[?(@.name=='%s')].fields[?(@.name=='%s')].description", 
            typeName,
            fieldName);
        assertThat(schemaJson, hasJsonPath(path, descriptionEquals(expected)));
    }

    @Test
    public void verifyTypesDescriptions() {
        assertTypeDescription("Query", "GraphQL Schema used for our tests");
        assertTypeDescription("SlingResource", "SlingResource, for our tests");
        assertTypeDescription("Test", "null");
    }

    @Test
    public void verifyFieldDescriptions() {
        assertFieldDescription("Query", "staticContent", "Test some static values");
        assertFieldDescription("SlingResource", "pathMD5", "null");
        assertFieldDescription("SlingResource", "pathSHA256", "SHA256 digest of the path");
        assertFieldDescription("SlingResource", "failure", "Failure message");
        assertFieldDescription("Test", "test", "null");
    }
}
