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

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.UUID;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.apache.sling.graphql.core.json.JsonSerializer;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.FailingDataFetcher;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.MockScriptServlet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.ServiceRegistration;

import graphql.ExecutionResult;

public class GraphQLResourceQueryTest {
    private SchemaProvider schemaProvider;
    private SlingDataFetcherSelector dataFetchersSelector;
    private Resource resource;

    @Rule
    public final OsgiContext context = new OsgiContext();

    private void assertNestedException(Throwable t, Class<?> clazz, String messageContains) {
        boolean found = false;
        while(t != null) {
            if(t.getClass().equals(clazz) && t.getMessage().contains(messageContains)) {
                found = true;
                break;
            }
            t = t.getCause();
        }
        if(!found) {
            fail(String.format("Did not get %s exception with message containing '%s'", 
                clazz.getName(), messageContains));
        }
    }

    private ServiceRegistration<?> registerSlingDataFetcher(String name, SlingDataFetcher<?> f) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(SlingDataFetcher.NAME_SERVICE_PROPERTY, name);
        return context.bundleContext().registerService(SlingDataFetcher.class, f, props);
    }

    @Before
    public void setup() {
        schemaProvider = new MockSchemaProvider("test-schema");
        final String resourceType = "RT-" + UUID.randomUUID();
        final String path = "/some/path/" + UUID.randomUUID();
        resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getPath()).thenReturn(path);
        Mockito.when(resource.getResourceType()).thenReturn(resourceType);

        final Dictionary<String, Object> staticData = new Hashtable<>();
        staticData.put("test", true);

        registerSlingDataFetcher("echoNS/echo", new EchoDataFetcher(null));
        registerSlingDataFetcher("failure/fail", new FailingDataFetcher());
        registerSlingDataFetcher("test/static", new EchoDataFetcher(staticData));
        registerSlingDataFetcher("test/fortyTwo", new EchoDataFetcher(42));
        registerSlingDataFetcher("test/digest", new DigestDataFetcher());

        // Our MockScriptServlet to simulates a script for unit tests, for the
        // integration tests we use a real script
        final MockScriptServlet mss = new MockScriptServlet();
        final ServletResolver servletResolver = Mockito.mock(ServletResolver.class);
        Mockito.when(servletResolver.resolveServlet(Mockito.any(Resource.class), Mockito.any(String.class))).thenReturn(mss);
        context.bundleContext().registerService(ServletResolver.class, servletResolver, null);

        context.registerInjectActivateService(new ScriptedDataFetcherProvider());
        context.registerInjectActivateService(new SlingDataFetcherSelector());
        dataFetchersSelector = context.getService(SlingDataFetcherSelector.class);
    }

    private String queryJSON(String stmt) throws Exception {
        return queryJSON(stmt, null);
    }

    private String queryJSON(String stmt, String [] selectors) throws Exception {
        final ExecutionResult result = new GraphQLResourceQuery().executeQuery(schemaProvider,
            dataFetchersSelector, resource, selectors, stmt, null);
        assertTrue("Expecting no errors: " + result.getErrors(), result.getErrors().isEmpty());
        return new JsonSerializer().toJSON(result);
    }

    @Test
    public void basicTest() throws Exception {
        final String json = queryJSON("{ currentResource { path resourceType } }");
        assertThat(json, hasJsonPath("$.data.currentResource"));
        assertThat(json, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo(resource.getResourceType())));
    }

    @Test
    public void staticContentTest() throws Exception {
        final String json = queryJSON("{ staticContent { test } }");
        assertThat(json, hasJsonPath("$.data.staticContent"));
        assertThat(json, hasJsonPath("$.data.staticContent.test", equalTo(true)));
    }

    @Test
    public void digestFieldsTest() throws Exception {
        final String json = queryJSON("{ currentResource { path pathMD5 pathSHA256 resourceTypeMD5 } }");

        final String pathMD5 = DigestDataFetcher.computeDigest("md5", resource.getPath());
        final String pathSHA256 = DigestDataFetcher.computeDigest("sha-256", resource.getPath());
        final String resourceTypeMD5 = DigestDataFetcher.computeDigest("md5", resource.getResourceType());

        assertThat(json, hasJsonPath("$.data.currentResource"));
        assertThat(json, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(json, hasJsonPath("$.data.currentResource.pathMD5", equalTo("md5#path#" + pathMD5)));
        assertThat(json, hasJsonPath("$.data.currentResource.pathSHA256", equalTo("sha-256#path#" + pathSHA256)));
        assertThat(json, hasJsonPath("$.data.currentResource.resourceTypeMD5", equalTo("md5#resourceType#" + resourceTypeMD5)));
    }

    @Test
    public void nullValueTest() throws Exception {
        final String json = queryJSON("{ currentResource { nullValue } }");
        assertThat(json, hasJsonPath("$.data.currentResource"));
        assertThat(json, hasJsonPath("$.data.currentResource.nullValue", is(nullValue())));
    }

    @Test
    public void dataFetcherFailureTest() throws Exception {
        try {
            final String stmt = "{ currentResource { failure } }";
            new GraphQLResourceQuery().executeQuery(schemaProvider, dataFetchersSelector, resource, null, stmt, null);
        } catch(RuntimeException rex) {
            assertThat(rex.getMessage(), equalTo("FailureDataFetcher"));
        }
    }

    @Test
    public void schemaSelectorsTest() throws Exception {
        final String [] selectors = { "selected", "foryou" };
        final String json = queryJSON("{ currentResource { path fortyTwo } }", selectors);

        assertThat(json, hasJsonPath("$.data.currentResource"));
        assertThat(json, hasJsonPath("$.data.currentResource.path", equalTo(42)));
        assertThat(json, hasJsonPath("$.data.currentResource.fortyTwo", equalTo(42)));
    }

    @Test
    public void duplicateProviderTest() throws Exception {
        registerSlingDataFetcher("test/static", new EchoDataFetcher(42));
        try {
            queryJSON("{ currentResource { path } }", null);
            fail("Expected query to fail");
        } catch(Exception e) {
            assertNestedException(e, IOException.class, "expected just one");
        }
    }

    @Test
    public void invalidFetcherNamesTest() throws Exception {
        schemaProvider = new MockSchemaProvider("failing-schema");
        final ServiceRegistration<?> reg = registerSlingDataFetcher("missingSlash", new EchoDataFetcher(42));
        try {
            queryJSON("{ currentResource { missingSlash } }", null);
            fail("Expected query to fail");
        } catch(Exception e) {
            assertNestedException(e, IOException.class, "does not match");
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void scriptedFetcherProviderTest() throws Exception {
        final String json = queryJSON("{ currentResource { path } scriptedFetcher (testing: \"1, 2, 3\") { boolValue resourcePath testingArgument } }", null);
        assertThat(json, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.boolValue", equalTo(true)));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.resourcePath", equalTo(resource.getPath())));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.testingArgument", equalTo("1, 2, 3")));
    }
}