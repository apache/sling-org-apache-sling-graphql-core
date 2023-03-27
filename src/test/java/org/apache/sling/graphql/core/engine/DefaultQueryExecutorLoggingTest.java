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

import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.util.LogCapture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ch.qos.logback.classic.Level;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;

public class DefaultQueryExecutorLoggingTest extends ResourceQueryTestBase {

    private static final String TEST_SCHEMA_MARKER = "GraphQL Schema used for our tests";
    private static final String [] TEST_SELECTORS = { "selected", "foryou" };
    private LogCapture capture;
    
    protected void setupAdditionalServices() {
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "echoNS/echo", new EchoDataFetcher(null));
    }
    
    @Before
    public void setupCapture() {
        capture = new LogCapture(DefaultQueryExecutor.class.getPackage().getName(), true);
        capture.start();
    }

    @After
    public void verifyNoSchemaLogged() {
        capture.list.stream()
            .filter(event -> event.getFormattedMessage().contains(TEST_SCHEMA_MARKER))
            .forEach(event-> assertEquals("Expecting schema marker at DEBUG level only", Level.DEBUG, event.getLevel()))
        ;
    }

    @Test
    public void basicTest() throws Exception {
        final String json = queryJSON("{ currentResource { resourceType } }");
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo(resource.getResourceType())));

        capture.assertContains(Level.DEBUG,
            TEST_SCHEMA_MARKER,
            "Executing query"
        );
    }

    private void assertQuerySyntaxError(String ... selectors) {
// This does not work anymore because the error message is based on the query string
//        final String invalidQuery = "INVALID " + UUID.randomUUID();
        final String invalidQuery = "INVALID " + "4ecae67c-13c5-432e-b36c-7a29d35118c1";
        queryJSON(invalidQuery, selectors);
        capture.assertContains(
            Level.ERROR,
            "Query failed for Resource " + resource.getPath(),
            "query=" + invalidQuery,
            "Errors:Error: type=InvalidSyntax",
            "message=Invalid syntax with ANTLR error 'token recognition error at: '4ec'' at line 1 column 9",
            String.format("selectors=%s", Arrays.toString(selectors))
        );
    }

    @Test
    public void querySyntaxError() throws Exception {
        assertQuerySyntaxError();
    }

    @Test
    public void querySyntaxErrorWithSelectors() throws Exception {
        assertQuerySyntaxError(TEST_SELECTORS);
    }

    private void assertSchemaFailure(String ... selectors) {
        final String msg = "TEST-" + UUID.randomUUID();
        schemaProvider.setSchemaException(new RuntimeException(msg));

        final String query = "{ currentResource { path } }";
        queryJSON(query, selectors);

        capture.assertContains(
            Level.INFO,
            "Schema provider Exception"
        );
        capture.assertContains(
            Level.ERROR,
            "Query failed for Resource " + resource.getPath(),
            "query=" + query,
            String.format("selectors=%s", Arrays.toString(selectors))
        );

        assertFalse(
            "For now, the schema exception message is not logged",
            capture.anyMatch(event -> event.getFormattedMessage().contains(msg))
        );
    }

    @Test
    public void schemaFailure() throws Exception {
        assertSchemaFailure();
    }

    @Test
    public void schemaFailureWithSelectors() throws Exception {
        assertSchemaFailure(TEST_SELECTORS);
    }
}
