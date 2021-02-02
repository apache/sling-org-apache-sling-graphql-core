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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import graphql.schema.GraphQLSchema;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SelectedField;
import org.apache.sling.graphql.api.SelectionSet;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.api.engine.ValidationResult;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.DroidDTO;
import org.apache.sling.graphql.core.mocks.DummyTypeResolver;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.FailingDataFetcher;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import graphql.schema.idl.TypeDefinitionRegistry;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultQueryExecutorTest extends ResourceQueryTestBase {

    protected void setupAdditionalServices() {
        final Dictionary<String, Object> staticData = new Hashtable<>();
        staticData.put("test", true);

        HumanDTO human = new HumanDTO("human-1", "Luke", "Tatooine");
        DroidDTO droid = new DroidDTO("droid-1", "R2-D2", "whistle");

        final List<Object> characters = new ArrayList<>();
        characters.add(human);
        characters.add(droid);

        final Dictionary<String, Object> data = new Hashtable<>();
        data.put("characters", characters);

        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "character/fetcher", new EchoDataFetcher(data));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "echoNS/echo", new EchoDataFetcher(null));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "failure/fail", new FailingDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "test/static", new EchoDataFetcher(staticData));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "test/fortyTwo", new EchoDataFetcher(42));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/digest", new DigestDataFetcher());

        final Dictionary<String, Object> dataCombined = new Hashtable<>();
        dataCombined.put("unionTest", characters);
        dataCombined.put("interfaceTest", characters);
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "combined/fetcher", new EchoDataFetcher(dataCombined));
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
    public void queryValidationErrorResponseTest() throws Exception {
        final String json = queryJSON("{ currentResource_NON_EXISTENT { nullValue } }");
        assertThat(json, hasJsonPath("$.errors[0].message"));
        assertThat(json, hasJsonPath("$.errors[0].locations"));
        assertThat(json, hasJsonPath("$.errors[0].extensions"));
        assertThat(json, hasJsonPath("$.errors[0].extensions.classification", is("ValidationError")));
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
    public void schemaSelectorsTest(){
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
            final String json = queryJSON("{ currentResource { missingSlash } }", new String[] {});
            assertThat(json, hasJsonPath("$.errors[0].message", containsString("Invalid fetcher name missingSlash")));
            assertThat(json, hasJsonPath("$.errors[0].extensions.exception", is(SlingGraphQLException.class.getName())));
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
            final String json = queryJSON("{ currentResource { missingSlash } }", new String[] {});
            assertThat(json, hasJsonPath("$.errors[0].message", containsString("Invalid type resolver name missingSlash")));
            assertThat(json, hasJsonPath("$.errors[0].extensions.exception", is(SlingGraphQLException.class.getName())));
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void unionQueryTest() throws Exception {
        final String json = queryJSON("{ unionQuery { characters { ... on Human { name address }  ... on Droid { name primaryFunction } } } }");
        assertThat(json, hasJsonPath("$.data.unionQuery"));
        assertThat(json, hasJsonPath("$.data.unionQuery.characters[0].name", equalTo("Luke")));
        assertThat(json, hasJsonPath("$.data.unionQuery.characters[0].address", equalTo("Tatooine")));
        assertThat(json, hasJsonPath("$.data.unionQuery.characters[1].name", equalTo("R2-D2")));
        assertThat(json, hasJsonPath("$.data.unionQuery.characters[1].primaryFunction", equalTo("whistle")));
    }

    @Test
    public void interfaceQueryTest() throws Exception {
        final String json = queryJSON("{ interfaceQuery { characters { id ... on Human { name address }  ... on Droid { name primaryFunction } } } }");
        assertThat(json, hasJsonPath("$.data.interfaceQuery"));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.characters[0].id", equalTo("human-1")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.characters[0].name", equalTo("Luke")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.characters[0].address", equalTo("Tatooine")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.characters[1].id", equalTo("droid-1")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.characters[1].name", equalTo("R2-D2")));
        assertThat(json, hasJsonPath("$.data.interfaceQuery.characters[1].primaryFunction", equalTo("whistle")));
    }

    @Test
    public void selectionSetTest() throws Exception {
        queryJSON("{ combinedFetcher { boolValue resourcePath aTest { boolValue test resourcePath } allTests { boolValue test resourcePath } unionTest { ... on Human { id address } ... on Droid { id primaryFunction } } interfaceTest { id ... on Human { address } ... on Droid { primaryFunction } } } }");

        // retrieve the service used
        ServiceReference<?>[] serviceReferences = context.bundleContext().getServiceReferences(SlingDataFetcher.class.getName(), "(name=combined/fetcher)");
        EchoDataFetcher echoDataFetcher = (EchoDataFetcher) context.bundleContext().getService(serviceReferences[0]);

        // Access the computed SelectionSet
        SelectionSet selectionSet = echoDataFetcher.getSelectionSet();

        assertEquals(6, selectionSet.getFields().size());

        String[] expectedFieldNames = new String[] {
                "boolValue",
                "resourcePath",
                "aTest",
                "unionTest",
                "interfaceTest"
        };
        final List<SelectedField> selectionSetFields = selectionSet.getFields();
        for (String expectedFieldname : expectedFieldNames) {
            assertTrue(selectionSetFields.stream().anyMatch(f -> expectedFieldname.equals(f.getName())));
        }

        // Assert it contains the expected results
        String[] expectedQualifiedName = new String[] {
                "boolValue",
                "resourcePath",
                "aTest",
                "aTest/test",
                "aTest/boolValue",
                "aTest/resourcePath",
                "allTests",
                "allTests/test",
                "allTests/boolValue",
                "allTests/resourcePath",
                "unionTest",
                "unionTest/Human",
                "unionTest/Human/id",
                "unionTest/Human/address",
                "unionTest/Droid",
                "unionTest/Droid/id",
                "unionTest/Droid/primaryFunction",
                "interfaceTest",
                "interfaceTest/id",
                "interfaceTest/Human",
                "interfaceTest/Human/address",
                "interfaceTest/Droid",
                "interfaceTest/Droid/primaryFunction"
        };
        for (String expectedQN : expectedQualifiedName) {
            assertTrue(selectionSet.contains(expectedQN));
        }

        String[] expectedNonInlineQNs = new String[] {
                "boolValue",
                "resourcePath",
                "aTest",
                "aTest/test",
                "aTest/boolValue",
                "aTest/resourcePath",
                "allTests",
                "allTests/test",
                "allTests/boolValue",
                "allTests/resourcePath",
                "unionTest",
                "unionTest/Human/id",
                "unionTest/Human/address",
                "unionTest/Droid/id",
                "unionTest/Droid/primaryFunction",
                "interfaceTest",
                "interfaceTest/id",
                "interfaceTest/Human/address",
                "interfaceTest/Droid/primaryFunction"
        };
        for (String expectedNonInlineQN : expectedNonInlineQNs) {
            assertFalse(Objects.requireNonNull(selectionSet.get(expectedNonInlineQN)).isInline());
        }

        String[] expectedInlineQNs = new String[] {
                "unionTest/Human",
                "unionTest/Droid",
                "interfaceTest/Human",
                "interfaceTest/Droid"
        };
        for (String expectedInlineQN : expectedInlineQNs) {
            assertTrue(Objects.requireNonNull(selectionSet.get(expectedInlineQN)).isInline());
        }

        String[] expectedSubFieldNames = new String[] {
                "test",
                "boolValue",
                "resourcePath"
        };

        SelectedField allTests = selectionSet.get("allTests");
        assert allTests != null;
        List<SelectedField> subSelectedFields = allTests.getSubSelectedFields();
        for (String expectedSubFieldname : expectedSubFieldNames) {
            assertTrue(subSelectedFields.stream().anyMatch(f -> expectedSubFieldname.equals(f.getName())));
        }
    }

    @Test
    public void testCachedTypeRegistry() throws IOException {
        // by default we'll get the test-schema
        final DefaultQueryExecutor queryExecutor = (DefaultQueryExecutor) context.getService(QueryExecutor.class);
        final RankedSchemaProviders schemaProvider = context.getService(RankedSchemaProviders.class);
        assertNotNull(queryExecutor);
        assertNotNull(schemaProvider);
        String[] selectors = new String[]{};
        String schema = Objects.requireNonNull(schemaProvider.getSchema(resource, selectors));
        GraphQLSchema
                schema1 = queryExecutor.getSchema(schema, resource, selectors);
        GraphQLSchema schema2 =
                queryExecutor.getSchema(schema, resource, selectors);
        assertEquals(schema1, schema2);

        // change the schema provider
        context.registerService(SchemaProvider.class, new MockSchemaProvider("test-schema-selected-foryou"), Constants.SERVICE_RANKING,
                Integer.MAX_VALUE);
        GraphQLSchema
                schema3 = queryExecutor.getSchema(schema, resource, selectors);
        GraphQLSchema schema4 =
                queryExecutor.getSchema(schema, resource, selectors);
        assertEquals(schema1, schema2);
        assertEquals(schema3, schema4);
        assertNotEquals(schema1, schema3);
    }

    @Test
    public void testTypeRegistryWithTheCacheDisabled() throws IOException {
        Map<String, Object> properties = new HashMap<>();
        properties.put("schemaCacheSize", 0);

        DefaultQueryExecutor queryExecutor = (DefaultQueryExecutor) context.registerService(QueryExecutor.class, new DefaultQueryExecutor(),
                properties);
        MockOsgi.injectServices(queryExecutor, context.bundleContext(), properties);
        MockOsgi.activate(queryExecutor, context.bundleContext(), properties);

        final RankedSchemaProviders schemaProvider = context.getService(RankedSchemaProviders.class);
        assertNotNull(queryExecutor);
        assertNotNull(schemaProvider);
        String[] selectors = new String[]{};
        String schema = Objects.requireNonNull(schemaProvider.getSchema(resource, selectors));
        GraphQLSchema
                schema1 = queryExecutor.getSchema(schema, resource, selectors);
        GraphQLSchema schema2 =
                queryExecutor.getSchema(schema, resource, selectors);
        assertNotEquals(schema1, schema2);

        // change the schema provider
        context.registerService(SchemaProvider.class, new MockSchemaProvider("test-schema-selected-foryou"), Constants.SERVICE_RANKING,
                Integer.MAX_VALUE);
        GraphQLSchema
                schema3 = queryExecutor.getSchema(schema, resource, selectors);
        GraphQLSchema schema4 =
                queryExecutor.getSchema(schema, resource, selectors);
        assertNotEquals(schema3, schema4);
    }

}
