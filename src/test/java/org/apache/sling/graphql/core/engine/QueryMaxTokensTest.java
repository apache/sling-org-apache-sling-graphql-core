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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.equalTo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;

/** Test the SLING-12198 configurable max tokens */
@RunWith(Parameterized.class)
public class QueryMaxTokensTest extends ResourceQueryTestBase {
  private static final int DEFAULT_MAX_TOKENS = 15000;
  private static final String INVALID = "invalid ";
  private static final String WHITESPACE = " {\t";
  private final Integer maxTokens;
  private final Integer maxWhitespaceTokens;
  private final int nOk;
  private final int nTokensFailure;
  private final int nWhitespaceFailure;

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    final List<Object[]> result = new ArrayList<>();
    result.add(new Object[] { "default values", null, null, DEFAULT_MAX_TOKENS - 1, DEFAULT_MAX_TOKENS, -1 });
    result.add(new Object[] { "same values", 100, 100, 99, 100, 100 });
    result.add(new Object[] { "more whitespace", 100, 200, 99, 100, 150 });
    return result;
  }

  public QueryMaxTokensTest(String name, Integer maxTokens, Integer maxWhitespaceTokens, int nOk, int nTokensFailure,
      int nWhitespaceFailure) {
    this.maxTokens = maxTokens;
    this.maxWhitespaceTokens = maxWhitespaceTokens;
    this.nOk = nOk;
    this.nTokensFailure = nTokensFailure;
    this.nWhitespaceFailure = nWhitespaceFailure;
  }

  protected Map<String, Object> getQueryExecutorProperties() {
    final Map<String, Object> props = new HashMap<>();
    if (maxTokens != null) {
      props.put("queryMaxTokens", maxTokens);
    }
    if (maxWhitespaceTokens != null) {
      props.put("queryMaxWhitespaceTokens", maxWhitespaceTokens);
    }
    return props;
  }

  static String repeat(String what, int howMany) {
    final StringBuffer sb = new StringBuffer();
    for (int i = 0; i < howMany; i++) {
      sb.append(what);
    }
    return sb.toString();
  }

  private void assertQueryFailure(String query, String expectedError) throws Exception {
    final String json = queryJSON(query);
    assertThat(json, hasJsonPath("$.errors[0].extensions.classification", is("InvalidSyntax")));
    assertThat(json, hasJsonPath("$.errors[0].message", containsString(expectedError)));
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
    assertQueryFailure(repeat(INVALID, nOk), "Invalid syntax with offending token");
  }

  @Test
  public void tooManyTokens() throws Exception {
    assertQueryFailure(repeat(INVALID, nTokensFailure),
        "'grammar' tokens have been presented. To prevent Denial Of Service");
  }

  @Test
  public void tooManyWhitespaceTokens() throws Exception {
    if (nWhitespaceFailure >= 0) {
      assertQueryFailure(repeat(WHITESPACE, nWhitespaceFailure),
          "'whitespace' tokens have been presented. To prevent Denial Of Service");
    }
  }
}