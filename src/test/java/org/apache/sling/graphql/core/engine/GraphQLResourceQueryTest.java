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
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.FailingDataFetcher;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

public class GraphQLResourceQueryTest extends ResourceQueryTestBase {
    
    protected void setupDataFetchers() {
        final Dictionary<String, Object> staticData = new Hashtable<>();
        staticData.put("test", true);

        TestUtil.registerSlingDataFetcher(context.bundleContext(), "echoNS/echo", new EchoDataFetcher(null));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "failure/fail", new FailingDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "test/static", new EchoDataFetcher(staticData));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "test/fortyTwo", new EchoDataFetcher(42));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/digest", new DigestDataFetcher());
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
            new GraphQLResourceQuery().executeQuery(schemaProvider, dataFetchersSelector, scalarsProvider, resource, null, stmt, null);
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
    public void invalidFetcherNamesTest() throws Exception {
        schemaProvider = new MockSchemaProvider("failing-schema");
        final ServiceRegistration<?> reg = TestUtil.registerSlingDataFetcher(context.bundleContext(), "missingSlash", new EchoDataFetcher(42));
        try {
            queryJSON("{ currentResource { missingSlash } }", null);
            fail("Expected query to fail");
        } catch(Exception e) {
            TestUtil.assertNestedException(e, IOException.class, "does not match");
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