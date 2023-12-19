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
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.equalTo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;

/** Test the SLING-12198 configurable max tokens */
public class QueryMaxTokensTest extends ResourceQueryTestBase {
    private static final int FAILING_TOKENS_COUNT = 15000;
    private static final String INVALID = "invalid ";

    static String repeat(String what, int howMany) {
        final StringBuffer sb = new StringBuffer();
        for(int i=0; i < howMany; i++) {
            sb.append(what);
        }
        return sb.toString();
    }

    private void assertQueryFailure(String query, boolean isTooManyTokens) throws Exception {
        final String json = queryJSON(query);
        assertThat(json, hasJsonPath("$.errors[0].extensions.classification", is("InvalidSyntax")));
        final String expected = isTooManyTokens ? "To prevent Denial Of Service attacks" : "Invalid syntax with offending token";
        assertThat(json, hasJsonPath("$.errors[0].message", containsString(expected)));
    }

    protected void setupAdditionalServices() {
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "echoNS/echo", new EchoDataFetcher(null));
    }

    @Test
    public void verifyQueriesWork() throws Exception {
        final String json = queryJSON("{ currentResource { path resourceType } }");
        assertThat(json, hasJsonPath("$.data.currentResource"));
        assertThat(json, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo(resource.getResourceType())));
    }

    @Test
    public void numberOfTokensOk() throws Exception {
        assertQueryFailure(repeat(INVALID, FAILING_TOKENS_COUNT - 1), false);
    }

    @Test
    public void tooManyTokens() throws Exception {
        assertQueryFailure(repeat(INVALID, FAILING_TOKENS_COUNT), true);
    }
}