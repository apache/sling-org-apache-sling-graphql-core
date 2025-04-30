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
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
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
import org.apache.sling.graphql.core.mocks.LazyDataFetcher;
import org.apache.sling.graphql.core.mocks.MockSchemaProvider;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.apache.sling.testing.mock.osgi.MockOsgi;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.schema.idl.TypeDefinitionRegistry;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultQueryExecutorTest extends ResourceQueryTestBase {

    private final LazyDataFetcher lazyDataFetcher = new LazyDataFetcher();

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
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "lazy/fetcher", lazyDataFetcher);

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
        assertTrue("Wrong response, found errors: '" + errors + "'", errors.contains("Error: type=ValidationError; message=Validation error (FieldUndefined@[currentRsrc]) : Field 'currentRsrc' in type 'Query' is undefined; location=1,3;"));
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
            assertTrue("Failed to find expected field name: '" + expectedFieldname + "'", selectionSetFields.stream().anyMatch(f -> expectedFieldname.equals(f.getName())));
        }

        // In new graphql-java qualified field names are streamlined and so we don't have inlined sub types (inlined fragments)
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
                "unionTest/id",
                "unionTest/address",
                "unionTest/primaryFunction",
                "interfaceTest",
                "interfaceTest/id",
                "interfaceTest/address",
                "interfaceTest/primaryFunction"
        };
        for (String expectedQN : expectedQualifiedName) {
            assertTrue("Failed to find qualified field name: '" + expectedQN + "'", selectionSet.contains(expectedQN));
        }

        // No more inline fragments in 17.4 or later

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

        Map<String, List<String>> expectedObjectTypes = new HashMap<String, List<String>>() {{
            put("boolValue", Arrays.asList("Test2"));
            put("resourcePath", Arrays.asList("Test2"));
            put("aTest", Arrays.asList("Test2"));
            put("aTest/test", Arrays.asList("Test"));
            put("aTest/resourcePath", Arrays.asList("Test"));
            put("aTest/boolValue", Arrays.asList("Test"));
            put("allTests", Arrays.asList("Test2"));
            put("allTests/test", Arrays.asList("Test"));
            put("allTests/resourcePath", Arrays.asList("Test"));
            put("allTests/boolValue", Arrays.asList("Test"));
            put("unionTest", Arrays.asList("Test2"));
            put("unionTest/address", Arrays.asList("Human"));
            put("unionTest/id", Arrays.asList("Human", "Droid"));
            put("unionTest/primaryFunction", Arrays.asList("Droid"));
            put("interfaceTest", Arrays.asList("Test2"));
            put("interfaceTest/address", Arrays.asList("Human"));
            put("interfaceTest/id", Arrays.asList("Human", "Droid"));
            put("interfaceTest/primaryFunction", Arrays.asList("Droid"));
        }};
        // Compare size to see if we miss anything or have too many
        int count = countFields(selectionSetFields);
        assertFalse("Not enough fields found", count < expectedObjectTypes.size());
        assertFalse("Too many fields found", count > expectedObjectTypes.size());
        // Test the Object Type Names of a few fields
        for(Map.Entry<String, List<String>> entry: expectedObjectTypes.entrySet()) {
            // Parse path, find field and check object type names
            String[] tokens = entry.getKey().split("/");
            SelectedField field = findField(selectionSetFields, tokens);
            assertNotNull("Field not found with path: " + entry.getKey(), field);
            assertTrue("Field did not contain the expected Object Type Names: " + field.getName(), CollectionUtils.isEqualCollection(entry.getValue(), field.getObjectTypeNames()));
        }
    }

    private int countFields(List<SelectedField> subFields) {
        int answer = subFields.size();
        for(SelectedField granSubField: subFields) {
            answer += countFields(granSubField.getSubSelectedFields());
        }
        return answer;
    }

    private SelectedField findField(List<SelectedField> selectedFields, String[] tokens) {
        SelectedField answer = null;
        for (String token: tokens) {
            answer = selectedFields.stream().filter(f -> f.getName().equals(token)).findFirst().orElse(null);
            if(answer == null) {
                break;
            } else {
                // If found then take its sub-fields (one level down)
                selectedFields = answer.getSubSelectedFields();
            }
        }
        return answer;
    }

    @Test
    public void testInlinedFields() throws Exception {
        String query = getTextFromResource("test-inlined-fragments-query.txt");
        queryJSON(query);

        // retrieve the service used
        ServiceReference<?>[] serviceReferences = context.bundleContext().getServiceReferences(SlingDataFetcher.class.getName(), "(name=combined/fetcher)");
        EchoDataFetcher echoDataFetcher = (EchoDataFetcher) context.bundleContext().getService(serviceReferences[0]);

        // Access the computed SelectionSet
        SelectionSet selectionSet = echoDataFetcher.getSelectionSet();

        assertNotNull("No Selection Set found", selectionSet);
        assertEquals(6, selectionSet.getFields().size());

        final List<SelectedField> selectionSetFields = selectionSet.getFields();

        Map<String, List<String>> expectedObjectTypes = new HashMap<String, List<String>>() {{
            put("boolValue", Arrays.asList("Test2"));
            put("resourcePath", Arrays.asList("Test2"));
            put("aTest", Arrays.asList("Test2"));
            put("aTest/test", Arrays.asList("Test"));
            put("aTest/resourcePath", Arrays.asList("Test"));
            put("aTest/boolValue", Arrays.asList("Test"));
            put("allTests", Arrays.asList("Test2"));
            put("allTests/test", Arrays.asList("Test"));
            put("allTests/resourcePath", Arrays.asList("Test"));
            put("allTests/boolValue", Arrays.asList("Test"));
            put("unionTest", Arrays.asList("Test2"));
            put("unionTest/address", Arrays.asList("Human"));
            put("unionTest/id", Arrays.asList("Human", "Droid"));
            put("unionTest/name", Arrays.asList("Human"));
            put("unionTest/primaryFunction", Arrays.asList("Droid"));
            put("interfaceTest", Arrays.asList("Test2"));
            put("interfaceTest/address", Arrays.asList("Human"));
            put("interfaceTest/id", Arrays.asList("Human", "Droid"));
            put("interfaceTest/name", Arrays.asList("Droid"));
            put("interfaceTest/primaryFunction", Arrays.asList("Droid"));
        }};
        // Compare size to see if we miss anything or have too many
        int count = countFields(selectionSetFields);
        assertFalse("Not enough fields found", count < expectedObjectTypes.size());
        assertFalse("Too many fields found", count > expectedObjectTypes.size());
        // Test the Object Type Names of a few fields
        for(Map.Entry<String, List<String>> entry: expectedObjectTypes.entrySet()) {
            // Parse path, find field and check object type names
            String[] tokens = entry.getKey().split("/");
            SelectedField field = findField(selectionSetFields, tokens);
            assertNotNull("Field not found with path: " + entry.getKey(), field);
            assertTrue("Field did not contain the expected Object Type Names: " + field.getName(), CollectionUtils.isEqualCollection(entry.getValue(), field.getObjectTypeNames()));
        }
    }

    private String getTextFromResource(String fileName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if(is == null) {
                fail("Test File not found: " + fileName);
            }
            final StringWriter w = new StringWriter();
            IOUtils.copy(is, w);
            return w.toString();
        } catch(IOException ioe) {
            throw new RuntimeException("Error reading Test File" + fileName, ioe);
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
        TypeDefinitionRegistry
                registry1 = queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        TypeDefinitionRegistry registry2 =
                queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        assertEquals(registry1, registry2);

        // change the schema provider
        context.registerService(SchemaProvider.class, new MockSchemaProvider("test-schema-selected-foryou"), Constants.SERVICE_RANKING,
                Integer.MAX_VALUE);
        TypeDefinitionRegistry
                registry3 = queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        TypeDefinitionRegistry registry4 =
                queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        assertEquals(registry1, registry2);
        assertEquals(registry3, registry4);
        assertNotEquals(registry1, registry3);
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
        TypeDefinitionRegistry
                registry1 = queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        TypeDefinitionRegistry registry2 =
                queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        assertNotEquals(registry1, registry2);

        // change the schema provider
        context.registerService(SchemaProvider.class, new MockSchemaProvider("test-schema-selected-foryou"), Constants.SERVICE_RANKING,
                Integer.MAX_VALUE);
        TypeDefinitionRegistry
                registry3 = queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        TypeDefinitionRegistry registry4 =
                queryExecutor.getTypeDefinitionRegistry(schemaProvider.getSchema(resource, selectors), resource, selectors);
        assertNotEquals(registry3, registry4);
    }

    @Test
    public void testLazyDataFetcher() throws Exception {
        assertEquals(0, lazyDataFetcher.getCost());

        {
            // Without expensiveName, the Supplier is not called
            final String json = queryJSON("{ lazyQuery { cheapCount }}");
            assertThat(json, hasJsonPath("$.data.lazyQuery.cheapCount", equalTo(42)));
            assertEquals(0, lazyDataFetcher.getCost());
        }

        {
            // With expensiveName, the Supplier is called
            lazyDataFetcher.resetCost();
            final String json = queryJSON("{ lazyQuery { cheapCount expensiveName }}");
            assertThat(json, hasJsonPath("$.data.lazyQuery.cheapCount", equalTo(42)));
            assertThat(json, hasJsonPath("$.data.lazyQuery.expensiveName", equalTo("LAZYDATAFETCHER")));
            assertEquals(1, lazyDataFetcher.getCost());
        }

        {
            // With clone, the Supplier is also called once only
            lazyDataFetcher.resetCost();
            final String json = queryJSON("{ lazyQuery { cheapCount expensiveName expensiveNameClone }}");
            assertThat(json, hasJsonPath("$.data.lazyQuery.cheapCount", equalTo(42)));
            assertThat(json, hasJsonPath("$.data.lazyQuery.expensiveName", equalTo("LAZYDATAFETCHER")));
            assertThat(json, hasJsonPath("$.data.lazyQuery.expensiveNameClone", equalTo("LAZYDATAFETCHER")));
            assertEquals(1, lazyDataFetcher.getCost());
        }

    }

    @Test
    public void testMaxFieldCountConfig() {
        DefaultQueryExecutor.Config config = Mockito.mock(DefaultQueryExecutor.Config.class);
        Mockito.when(config.maxFieldCount()).thenReturn(1000);

        DefaultQueryExecutor executor = new DefaultQueryExecutor();
        executor.activate(config);

        int expectedMaxFieldCount = 1000;
        int actualMaxFieldCount = ExecutableNormalizedOperationFactory.Options.defaultOptions().getMaxFieldsCount();

        assertEquals("Max field count should match the configured value", expectedMaxFieldCount, actualMaxFieldCount);
    }
}
