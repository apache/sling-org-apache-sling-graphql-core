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
package org.apache.sling.graphql.core.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.servlet.Servlet;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.commons.metrics.Counter;
import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.commons.metrics.Timer;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.api.engine.ValidationResult;
import org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockRequestPathInfo;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GraphQLServletTest {

    @Rule
    public SlingContext context = new SlingContext();

    private static final String TEST_RESOURCE_TYPE = "a/b/c";
    private static final String TEST_QUERY = "{\"query\": \"{ currentResource { resourceType name } }\" }";
    private Resource resource;

    @Mock
    private MetricsService metricsService;
    @Spy
    private MetricRegistry metricRegistry = new MetricRegistry();
    @Mock
    private Counter counter;
    @Mock
    private Timer timer;

    @Before
    public void setUp() {
        when(timer.time()).thenReturn(mock(Timer.Context.class));
        when(metricsService.counter(any(String.class))).thenReturn(counter);
        when(metricsService.timer(any(String.class))).thenReturn(timer);
        context.registerService(MetricsService.class, metricsService);

        context.registerService(MetricRegistry.class, metricRegistry, "name", "sling");

        QueryExecutor queryExecutor = mock(QueryExecutor.class);
        ValidationResult validationResult = mock(ValidationResult.class);
        when(validationResult.isValid()).thenReturn(true);
        when(queryExecutor.validate(any(String.class), any(Map.class), any(Resource.class), any(String[].class))).thenReturn(validationResult);
        context.registerService(QueryExecutor.class, queryExecutor);

        context.build().resource("/content/graphql", ResourceResolver.PROPERTY_RESOURCE_TYPE, TEST_RESOURCE_TYPE).commit();
        resource = context.resourceResolver().resolve("/content/graphql");
    }

    @Test
    public void testCachingErrors() throws IOException {
            context.registerInjectActivateService(new SimpleGraphQLCacheProvider(), "maxMemory", 10);
            context.registerInjectActivateService(new GraphQLServlet(), ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, TEST_RESOURCE_TYPE,
                    "persistedQueries.suffix", "/persisted");
            GraphQLServlet servlet = (GraphQLServlet) context.getService(Servlet.class);
            assertNotNull(servlet);

            MockSlingHttpServletResponse response = context.response();
            MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.bundleContext());
            request.setMethod("POST");
            request.setContent(TEST_QUERY.getBytes(StandardCharsets.UTF_8));

            request.setResource(resource);
            MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) request.getRequestPathInfo();
            requestPathInfo.setExtension("gql");
            requestPathInfo.setResourcePath(resource.getPath());
            requestPathInfo.setSuffix("/persisted");

            servlet.doPost(request, response);

            assertEquals(500, response.getStatus());
    }

    @Test
    public void testDisabledSuffix() throws IOException {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider());
        context.registerInjectActivateService(new GraphQLServlet(), ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, TEST_RESOURCE_TYPE,
                "persistedQueries.suffix", "");
        GraphQLServlet servlet = (GraphQLServlet) context.getService(Servlet.class);
        assertNotNull(servlet);

        MockSlingHttpServletResponse response = context.response();
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.bundleContext());


        request.setResource(resource);
        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) request.getRequestPathInfo();
        requestPathInfo.setExtension("gql");
        requestPathInfo.setResourcePath(resource.getPath());
        requestPathInfo.setSuffix("/persisted");
        request.setPathInfo("/content/graphql/persisted/hash");
        servlet.doGet(request, response);
        assertEquals(400, response.getStatus());
        assertEquals("Persisted queries are disabled.", response.getStatusMessage());
    }

    @Test
    public void testMetricsRegistered() {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider());
        context.registerInjectActivateService(new GraphQLServlet(), ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, TEST_RESOURCE_TYPE,
            "persistedQueries.suffix", "");

        String expectedMetricPrefix = "org.apache.sling.graphql.core.servlet.GraphQLServlet.rt:" + TEST_RESOURCE_TYPE + ".m:GET.e:gql";

        verify(metricsService).counter(expectedMetricPrefix + ".cache_hits");
        verify(metricsService).counter(expectedMetricPrefix + ".requests_total");
        verify(metricsService).timer(expectedMetricPrefix + ".requests_timer");
        verify(metricRegistry).register(eq(expectedMetricPrefix + ".cache_hit_rate"), any(Gauge.class));
    }

    @Test
    public void testCacheHitRatioMetric () {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider());
        context.registerInjectActivateService(new GraphQLServlet(), ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, TEST_RESOURCE_TYPE,
            "persistedQueries.suffix", "/persisted");

        // test resource type, default method, default extension
        String expectedMetric = "org.apache.sling.graphql.core.servlet.GraphQLServlet.rt:" + TEST_RESOURCE_TYPE + ".m:GET.e:gql.cache_hit_rate";

        assertTrue(metricRegistry.getGauges().containsKey(expectedMetric));
        assertEquals(0.0f, metricRegistry.getGauges().get(expectedMetric).getValue());

        // increments both the hit and miss metric
        when(counter.getCount()).thenReturn(1L);

        assertEquals(0.5f, metricRegistry.getGauges().get(expectedMetric).getValue());
    }

    private void assertPostWithBody(String contentType, String query, int expectedStatus) throws IOException {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider());
        context.registerInjectActivateService(new GraphQLServlet(), ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES, TEST_RESOURCE_TYPE,
                "persistedQueries.suffix", "");
        GraphQLServlet servlet = (GraphQLServlet) context.getService(Servlet.class);
        assertNotNull(servlet);

        MockSlingHttpServletResponse response = context.response();
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.bundleContext());

        request.setMethod("POST");
        request.setContent(query.getBytes(StandardCharsets.UTF_8));
        request.setContentType(contentType);

        request.setResource(resource);
        MockRequestPathInfo requestPathInfo = (MockRequestPathInfo) request.getRequestPathInfo();
        requestPathInfo.setExtension("gql");
        requestPathInfo.setResourcePath(resource.getPath());

        servlet.doPost(request, response);
        assertEquals(expectedStatus, response.getStatus());
}

    @Test
    public void testBasicJsonContentType() throws IOException {
        assertPostWithBody("application/json", TEST_QUERY, 200);
    }

    @Test
    public void testJsonContentTypeWithCharset() throws IOException {
        assertPostWithBody("application/json  ; charset=UTF-8", TEST_QUERY,200);
    }

    @Test
    public void testNoContentType() throws IOException {
        assertPostWithBody(null, TEST_QUERY, 400);
    }

    @Test
    public void testWrongContentType() throws IOException {
        assertPostWithBody("text/html", TEST_QUERY, 400);
    }

    /**
     * SLING-11248 NothingToRead exception should return 400.
     */
    @Test
    public void testEmptyQueryNothingToRead() throws IOException {
        assertPostWithBody("application/json  ; charset=UTF-8", "", 400);
    }
}
