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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.UUID;

import com.cedarsoftware.util.io.JsonWriter;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.graphqljava.DataFetcherProvider;
import org.apache.sling.graphql.core.schema.DataFetcherSelector;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.DigestDataFetcherProvider;
import org.apache.sling.graphql.core.mocks.EchoDataFetcherProvider;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import graphql.ExecutionResult;

public class GraphQLResourceQueryTest {
    private final SchemaProvider schemaProvider = new MockSchemaProvider();
    private DataFetcherSelector dataFetchersSelector;
    private Resource resource;

    @Rule
    public final OsgiContext context = new OsgiContext();

    private void registerDataFetcherProvider(String namespace, DataFetcherProvider p) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(DataFetcherProvider.NAMESPACE_SERVICE_PROPERTY, namespace);
        context.bundleContext().registerService(DataFetcherProvider.class, p, props);
    }

    @Before
    public void setup() {
        final String resourceType = "RT-" + UUID.randomUUID();
        final String path = "/some/path/" + UUID.randomUUID();
        resource = Mockito.mock(Resource.class);
        Mockito.when(resource.getPath()).thenReturn(path);
        Mockito.when(resource.getResourceType()).thenReturn(resourceType);

        final Dictionary<String, Object> staticData = new Hashtable<>();
        staticData.put("test", true);

        registerDataFetcherProvider("test", new EchoDataFetcherProvider("echo"));
        registerDataFetcherProvider("test", new EchoDataFetcherProvider("static", staticData));
        registerDataFetcherProvider("test", new DigestDataFetcherProvider());

        dataFetchersSelector = new DataFetcherSelector(context.bundleContext());
    }

    private String queryJSON(String stmt) throws Exception {
        final ExecutionResult result = new GraphQLResourceQuery().executeQuery(schemaProvider, dataFetchersSelector, resource, stmt);
        assertTrue("Expecting no errors: " + result.getErrors(), result.getErrors().isEmpty());
        return JsonWriter.objectToJson(result);
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
}