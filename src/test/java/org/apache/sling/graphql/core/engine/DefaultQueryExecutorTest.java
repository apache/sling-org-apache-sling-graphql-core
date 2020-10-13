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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.api.engine.ValidationResult;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.DummyTypeResolver;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.FailingDataFetcher;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.mocks.DroidDTO;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultQueryExecutorTest extends ResourceQueryTestBase {

    protected void setupAdditionalServices() {
        final Dictionary<String, Object> staticData = new Hashtable<>();
        staticData.put("test", true);

        final Dictionary<String, Object> data = new Hashtable<>();
        final List<Object> items = new ArrayList<>();
        items.add(new HumanDTO("human-1", "Luke", "Tatooine"));
        items.add(new DroidDTO("droid-1", "R2-D2", "whistle"));
        data.put("items", items);

        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "character/fetcher", new EchoDataFetcher(data));
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
    public void dataFetcherFailureTest() {
        try {
            final String stmt = "{ currentResource { failure } }";
            QueryExecutor queryExecutor = context.getService(QueryExecutor.class);
            assertNotNull(queryExecutor);
            queryExecutor.execute(stmt, Collections.emptyMap(), resource, new String[] {});
        } catch(RuntimeException rex) {
            assertThat(rex.getMessage(), containsString("Error: type=DataFetchingException; message=Exception while fetching data (/currentResource/failure) : FailingDataFetcher"));
        }
    }

    @Test
    public void invalidQueryTest() {
        final String stmt = "{ currentRsrc { failure } }";
        QueryExecutor queryExecutor = context.getService(QueryExecutor.class);
        assertNotNull(queryExecutor);
        ValidationResult result = queryExecutor.validate(stmt, Collections.emptyMap(), resource, new String[] {});
        assertFalse(result.isValid());
        String errors = String.join("\n", result.getErrors());
        assertTrue(errors.contains("Error: type=ValidationError; message=Validation error of type FieldUndefined: Field 'currentRsrc' in type 'Query' is undefined @ 'currentRsrc'; location=1,3;"));
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
    public void invalidFetcherNamesTest() {
        context.registerService(SchemaProvider.class, new MockSchemaProvider("failing-fetcher-schema"), Constants.SERVICE_RANKING,
                Integer.MAX_VALUE);
        final ServiceRegistration<?> reg = TestUtil.registerSlingDataFetcher(context.bundleContext(), "missingSlash", new EchoDataFetcher(42));
        try {
            queryJSON("{ currentResource { missingSlash } }", new String[] {});
            fail("Expected query to fail");
        } catch(Exception e) {
            TestUtil.assertNestedException(e, SlingGraphQLException.class, "Invalid fetcher name missingSlash");
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void invalidTypeResolverNamesTest() {
        context.registerService(SchemaProvider.class, new MockSchemaProvider("failing-type-resolver-schema"), Constants.SERVICE_RANKING,
                Integer.MAX_VALUE);
        final ServiceRegistration<?> reg = TestUtil.registerSlingTypeResolver(context.bundleContext(), "missingSlash",
                new DummyTypeResolver());
        try {
            queryJSON("{ currentResource { missingSlash } }", new String[] {});
            fail("Expected query to fail");
        } catch(Exception e) {
            TestUtil.assertNestedException(e, SlingGraphQLException.class, "Invalid type resolver name missingSlash");
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void unionQueryTest() throws Exception {
        final String json = queryJSON("{ unionQuery { items { ... on Human { name address }  ... on Droid { name primaryFunction } } } }");
        assertThat(json, hasJsonPath("$.data.unionQuery"));
        assertThat(json, hasJsonPath("$.data.unionQuery.items[0].name", equalTo("Luke")));
        assertThat(json, hasJsonPath("$.data.unionQuery.items[0].address", equalTo("Tatooine")));
        assertThat(json, hasJsonPath("$.data.unionQuery.items[1].name", equalTo("R2-D2")));
        assertThat(json, hasJsonPath("$.data.unionQuery.items[1].primaryFunction", equalTo("whistle")));
    }

    @Test
    public void interfaceQueryTest() throws Exception {
        final String json = queryJSON("{ interfaceQuery { items { id ... on Human { name address }  ... on Droid { name primaryFunction } } } }");
        assertThat(json, hasJsonPath("$.data.interfaceQuery"));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.items[0].id", equalTo("human-1")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.items[0].name", equalTo("Luke")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.items[0].address", equalTo("Tatooine")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.items[1].id", equalTo("droid-1")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.items[1].name", equalTo("R2-D2")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.items[1].primaryFunction", equalTo("whistle")));
    }
}
