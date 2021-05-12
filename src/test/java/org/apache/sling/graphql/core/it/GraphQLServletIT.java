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
package org.apache.sling.graphql.core.it;

import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.core.mocks.ReplacingSchemaProvider;
import org.apache.sling.resource.presence.ResourcePresence;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class GraphQLServletIT extends GraphQLCoreTestSupport {

    @Inject
    @Filter(value = "(path=/content/graphql/two)")
    private ResourcePresence resourcePresence;

    @Inject
    private BundleContext bundleContext;

    @Inject
    private SchemaProvider defaultSchemaProvider;

    private static final String GRAPHQL_SERVLET_CONFIG_PID = "org.apache.sling.graphql.core.GraphQLServlet";

    @Configuration
    public Option[] configuration() {
        return new Option[]{
            baseConfiguration(),
            testDataFetchers(),
            factoryConfiguration("org.apache.sling.resource.presence.internal.ResourcePresenter")
                .put("path", "/content/graphql/two")
                .asOption(),

            // The GraphQL servlet is disabled by default, try setting up two of them
            factoryConfiguration(GRAPHQL_SERVLET_CONFIG_PID)
                .put("sling.servlet.resourceTypes", "sling/servlet/default")
                .put("sling.servlet.extensions", "gql")
                .put("sling.servlet.methods", new String[] { "GET", "POST" })
                .asOption(),
            factoryConfiguration(GRAPHQL_SERVLET_CONFIG_PID)
                .put("sling.servlet.resourceTypes", "graphql/test/two")
                .put("sling.servlet.selectors", new String[] { "testing", "another" })
                .put("sling.servlet.extensions", "otherExt")
                .asOption(),
        };
    }

    @Test
    public void testGqlExt() throws Exception {
        final String json = getContent("/graphql/two.gql", "query", "{ currentResource { resourceType name } }");
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo("graphql/test/two")));
        assertThat(json, hasJsonPath("$.data.currentResource.name", equalTo("two")));
        assertThat(json, hasNoJsonPath("$.data.currentResource.path"));
    }

    @Test
    public void testGqlExtWithPost() throws Exception {
        final String json = getContentWithPost("/graphql/two.gql", "{ currentResource { resourceType name } }", null);
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo("graphql/test/two")));
        assertThat(json, hasJsonPath("$.data.currentResource.name", equalTo("two")));
        assertThat(json, hasNoJsonPath("$.data.currentResource.path"));
    }

    @Test
    public void testPersistedQueriesBasic() throws Exception {
        String queryHash = "a16982712f6ecdeba5d950d42e3c13df0fc26d008c497f6bf012701b57e02a51";
        MockSlingHttpServletResponse response = persistQuery("/graphql/two.gql", "{ currentResource { resourceType name } }", null);
        assertEquals("Expected to have stored a persisted query.", 201, response.getStatus());
        assertEquals("The value of the Location header does not look correct.",
                "http://localhost/graphql/two.gql/persisted/" + queryHash + ".gql",
                response.getHeader("Location"));

        response =
                executeRequest("GET", "/graphql/two.gql/persisted/" + queryHash + ".gql", null, "application/json", new StringReader(""),
                        200);
        assertEquals("max-age=60", response.getHeader("Cache-Control"));
        final String json = response.getOutputAsString();
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo("graphql/test/two")));
        assertThat(json, hasJsonPath("$.data.currentResource.name", equalTo("two")));
        assertThat(json, hasNoJsonPath("$.data.currentResource.path"));
    }

    @Test
    public void testPersistingInvalidQueries() throws Exception {
        HttpHost targetHost = new HttpHost("localhost", httpPort(), "http");
        HttpClientContext context = HttpClientContext.create();
        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("query", "{ current { resourceType name } }");
        queryMap.put("variables", Collections.emptyMap());
        String json = toJSON(queryMap);
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost post = new HttpPost("http://localhost:" + httpPort() + "/graphql/two.gql/persisted");
            post.setEntity(new ByteArrayEntity(json.getBytes(), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse postResponse = client.execute(targetHost, post, context)) {
                assertEquals("Did not expect to persist an invalid query.", 400, postResponse.getStatusLine().getStatusCode());
                String content = IOUtils.toString(postResponse.getEntity().getContent());
                assertTrue("Expected a Sling error page.", StringUtils.isNotEmpty(content));
                assertTrue("Expected to find the failure reason in the Sling error page.", content.contains("400 Invalid GraphQL query."));
            }
        }
    }

    @Test
    public void testPersistedQueriesWithAuthorization() throws Exception {
        HttpHost targetHost = new HttpHost("localhost", httpPort(), "http");
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                new UsernamePasswordCredentials("admin", "admin")
        );
        AuthCache authCache = new BasicAuthCache();
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(targetHost, basicAuth);

        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);

        Map<String, Object> queryMap = new HashMap<>();
        queryMap.put("query", "{ currentResource { resourceType name } }");
        queryMap.put("variables", Collections.emptyMap());
        String json = toJSON(queryMap);
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost post = new HttpPost("http://localhost:" + httpPort() + "/graphql/two.gql/persisted");
            post.setEntity(new ByteArrayEntity(json.getBytes(), ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse postResponse = client.execute(targetHost, post, context)) {
                assertEquals("Expected to have stored a persisted query.", 201, postResponse.getStatusLine().getStatusCode());
                Header locationHeader = postResponse.getFirstHeader(HttpHeaders.LOCATION);
                assertNotNull(locationHeader);
                String location = locationHeader.getValue();
                HttpGet get = new HttpGet(location);
                try (CloseableHttpResponse getResponse = client.execute(targetHost, get, context)) {
                    assertEquals("Expected to find a persisted query.", 200, getResponse.getStatusLine().getStatusCode());
                    Header cacheControl = getResponse.getFirstHeader("Cache-Control");
                    assertNotNull("Expected a Cache-Control header.", cacheControl);
                    assertEquals("max-age=60,private", cacheControl.getValue());
                    String getJson = IOUtils.toString(getResponse.getEntity().getContent());
                    assertThat(getJson, hasJsonPath("$.data.currentResource.resourceType", equalTo("graphql/test/two")));
                    assertThat(getJson, hasJsonPath("$.data.currentResource.name", equalTo("two")));
                    assertThat(getJson, hasNoJsonPath("$.data.currentResource.path"));
                }
            }
        }
    }


    @Test
    public void testOtherExtAndTestingSelector() throws Exception {
        executeRequest("GET", "/graphql/two.otherExt", null, null, null, 404);
        final String json = getContent("/graphql/two.testing.otherExt", "query", "{ withTestingSelector { farenheit } }");
        assertThat(json, hasJsonPath("$.data.withTestingSelector.farenheit", equalTo(451)));
    }

    @Test
    public void testOtherExtAndOtherSelector() throws Exception {
        final String json = getContent("/graphql/two.another.otherExt", "query", "{ currentResource { resourceType name } }");
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo("graphql/test/two")));
        assertThat(json, hasJsonPath("$.data.currentResource.name", equalTo("two")));
        assertThat(json, hasNoJsonPath("$.data.currentResource.path"));
    }

    @Test
    public void testMissingQuery() throws Exception {
        MockSlingHttpServletResponse response = executeRequest("GET", "/graphql/two.gql", null, null, null, -1);
        assertEquals(400, response.getStatus());
    }

    @Test
    public void testDefaultJson() throws Exception {
        final String json = getContent("/graphql/two.json");
        assertThat(json, hasJsonPath("$.title", equalTo("GraphQL two")));
        assertThat(json, hasJsonPath("$.jcr:primaryType", equalTo("nt:unstructured")));
    }

    @Test
    public void testMultipleSchemaProviders() throws Exception {
        new ReplacingSchemaProvider("currentResource", "REPLACED").register(bundleContext, defaultSchemaProvider, Integer.MAX_VALUE);
        new ReplacingSchemaProvider("currentResource", "NOT_THIS_ONE").register(bundleContext, defaultSchemaProvider, 1);
        final String json = getContent("/graphql/two.gql", "query", "{ REPLACED { resourceType name } }");
        assertThat(json, hasJsonPath("$.data.REPLACED.resourceType", equalTo("graphql/test/two")));
        assertThat(json, hasJsonPath("$.data.REPLACED.name", equalTo("two")));
        assertThat(json, hasNoJsonPath("$.data.REPLACED.path"));
    }
}
