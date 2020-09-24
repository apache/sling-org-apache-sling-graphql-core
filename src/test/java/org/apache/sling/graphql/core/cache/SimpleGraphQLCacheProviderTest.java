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
package org.apache.sling.graphql.core.cache;

import org.apache.sling.commons.metrics.Counter;
import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.commons.metrics.Timer;
import org.apache.sling.graphql.api.cache.GraphQLCacheProvider;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

public class SimpleGraphQLCacheProviderTest {

    @Rule
    public OsgiContext context = new OsgiContext();

    @Before
    public void setUp() {
        MetricsService metricsService = mock(MetricsService.class);
        when(metricsService.counter(anyString())).thenReturn(mock(Counter.class));
        when(metricsService.timer(anyString())).thenReturn(mock(Timer.class));
        context.registerService(MetricsService.class, metricsService);

        MetricRegistry metricRegistry = mock(MetricRegistry.class);
        context.registerService(MetricRegistry.class, metricRegistry, "name", "sling");
    }

    @Test
    public void getHash() {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider());
        SimpleGraphQLCacheProvider provider = (SimpleGraphQLCacheProvider) context.getService(GraphQLCacheProvider.class);
        assertNotNull(provider);
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", provider.getHash("hello world"));
    }

    @Test
    public void testMemoryLimits() {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider(), "cacheSize", 0, "maxMemory", 40);
        SimpleGraphQLCacheProvider provider = (SimpleGraphQLCacheProvider) context.getService(GraphQLCacheProvider.class);
        assertNotNull(provider);

        String aHash = provider.cacheQuery("a", "a/b/c", null);
        assertEquals("a", provider.getQuery(aHash, "a/b/c", null));

        String bHash = provider.cacheQuery("b", "a/b/c", null);
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));

        // a should be evicted due to the size constraints
        assertNull(provider.getQuery(aHash, "a/b/c", null));

        // but b should still be there
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));

        // attempt a replacement of b
        assertEquals(bHash, provider.cacheQuery("b", "a/b/c", null));
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));

        // and this value should not be stored since it's over the cache's limit
        String abHash = provider.cacheQuery("ab", "a/b/c", null);
        assertNull(abHash);
    }

    @Test
    public void testCapacityLimits() {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider(), "cacheSize", 3, "maxMemory", 0);
        SimpleGraphQLCacheProvider provider = (SimpleGraphQLCacheProvider) context.getService(GraphQLCacheProvider.class);
        assertNotNull(provider);

        String aHash = provider.cacheQuery("a", "a/b/c", null);
        assertEquals("a", provider.getQuery(aHash, "a/b/c", null));

        String bHash = provider.cacheQuery("b", "a/b/c", null);
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));

        String cHash = provider.cacheQuery("c", "a/b/c", null);
        assertEquals("c", provider.getQuery(cHash, "a/b/c", null));

        String dHash = provider.cacheQuery("d", "a/b/c", null);
        assertEquals("d", provider.getQuery(dHash, "a/b/c", null));

        // a should be evicted due to the size constraints
        assertNull(provider.getQuery(aHash, "a/b/c", null));

        // but b, c, d should still be there
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));
        assertEquals("c", provider.getQuery(cHash, "a/b/c", null));
        assertEquals("d", provider.getQuery(dHash, "a/b/c", null));
    }

    @Test
    public void testCapacityHasPriorityOverMemory() {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider(), "cacheSize", 2, "maxMemory", 40);
        SimpleGraphQLCacheProvider provider = (SimpleGraphQLCacheProvider) context.getService(GraphQLCacheProvider.class);
        assertNotNull(provider);

        String aHash = provider.cacheQuery("a", "a/b/c", null);
        assertEquals("a", provider.getQuery(aHash, "a/b/c", null));

        String bHash = provider.cacheQuery("b", "a/b/c", null);
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));

        String cHash = provider.cacheQuery("c", "a/b/c", null);
        assertEquals("c", provider.getQuery(cHash, "a/b/c", null));

        // a should be evicted due to the size constraints
        assertNull(provider.getQuery(aHash, "a/b/c", null));

        // but b, c should still be there
        assertEquals("b", provider.getQuery(bHash, "a/b/c", null));
        assertEquals("c", provider.getQuery(cHash, "a/b/c", null));
    }

    @Test
    public void testSelectors() {
        context.registerInjectActivateService(new SimpleGraphQLCacheProvider(), "cacheSize", 2, "maxMemory", 40);
        SimpleGraphQLCacheProvider provider = (SimpleGraphQLCacheProvider) context.getService(GraphQLCacheProvider.class);
        assertNotNull(provider);

        final String queryText = UUID.randomUUID().toString();
        final String path = "testing/selectors";
        final String selectors = UUID.randomUUID().toString();

        String aHash = provider.cacheQuery(queryText, path, null);
        String bHash = provider.cacheQuery(queryText, path, null);
        String cHash = provider.cacheQuery(queryText, path, selectors);

        assertEquals("Expecting the same hash for same query", aHash, bHash);
        assertNotEquals("Expecting a different hash with added selectors", aHash, cHash);
    }
}
