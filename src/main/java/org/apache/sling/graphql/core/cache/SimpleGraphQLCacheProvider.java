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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.metrics.Counter;
import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.graphql.api.cache.GraphQLCacheProvider;
import org.apache.sling.graphql.core.engine.SlingGraphQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;

@Component()
@Designate(ocd = SimpleGraphQLCacheProvider.Config.class)
public class SimpleGraphQLCacheProvider implements GraphQLCacheProvider {

    @ObjectClassDefinition(
            name = "Apache Sling GraphQL Simple Cache Provider",
            description = "The Apache Sling GraphQL Simple Cache Provider provides an in-memory size bound cache for persisted GraphQL " +
                    "queries."
    )
    public @interface Config {

        @AttributeDefinition(
                name = "Capacity",
                description = "The number of persisted queries to cache. If the cache size is set to a number greater than 0, then this " +
                        "parameter will have priority over maxMemory.",
                type = AttributeType.INTEGER,
                min = "0"
        )
        int cacheSize() default 0;

        @AttributeDefinition(
                name = "Max Values in Bytes",
                description = "The maximum amount of memory the values stored in the cache can use."
        )
        long maxMemory() default 10 * FileUtils.ONE_MB;

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleGraphQLCacheProvider.class);

    @Reference
    private MetricsService metricsService;

    @Reference(target = "(name=sling)")
    private MetricRegistry metricRegistry;

    private InMemoryLRUCache persistedQueriesCache;
    private Lock readLock;
    private Lock writeLock;

    private Counter evictions;

    private static final String METRIC_NS = SimpleGraphQLCacheProvider.class.getName();
    private static final String GAUGE_CACHE_SIZE = METRIC_NS + ".cacheSize";
    private static final String GAUGE_ELEMENTS = METRIC_NS + ".elements";
    private static final String GAUGE_MAX_MEMORY = METRIC_NS + ".maxMemory";
    private static final String GAUGE_CURRENT_MEMORY = METRIC_NS + ".currentMemory";
    private static final String COUNTER_EVICTIONS = METRIC_NS + ".evictions";
    private static final Set<String> MANUALLY_REGISTERED_METRICS = new HashSet<>(Arrays.asList(GAUGE_CACHE_SIZE, GAUGE_ELEMENTS,
            GAUGE_MAX_MEMORY, GAUGE_CURRENT_MEMORY));

    @Activate
    private void activate(Config config, BundleContext bundleContext) {
        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
        int capacity;
        if (config.cacheSize() < 0) {
            capacity = 0;
            LOGGER.debug("Cache capacity set to {}. Defaulting to 0.", config.cacheSize());
        } else {
            capacity = config.cacheSize();
        }
        long maxMemory;
        if (config.maxMemory() < 0) {
            maxMemory = 0;
            LOGGER.debug("Cache max memory set to {}. Defaulting to 0.", config.maxMemory());
        } else {
            maxMemory = config.maxMemory();
        }
        persistedQueriesCache = new InMemoryLRUCache(capacity, maxMemory);
        LOGGER.debug("In-memory cache initialized: capacity={}, maxMemory={}.", capacity, maxMemory);
        metricRegistry.register(GAUGE_CACHE_SIZE, (Gauge<Integer>) () -> capacity);
        metricRegistry.register(GAUGE_MAX_MEMORY, (Gauge<Long>) () -> maxMemory);
        metricRegistry.register(GAUGE_CURRENT_MEMORY, (Gauge<Long>) () -> persistedQueriesCache.currentSizeInBytes);
        metricRegistry.register(GAUGE_ELEMENTS, (Gauge<Integer>) () -> persistedQueriesCache.size());
        evictions = metricsService.counter(COUNTER_EVICTIONS);
    }

    @Deactivate
    private void deactivate() {
        for (String manuallyRegisteredMetric : MANUALLY_REGISTERED_METRICS) {
            metricRegistry.remove(manuallyRegisteredMetric);
        }
    }

    @Override
    @Nullable
    public String getQuery(@NotNull String hash, @NotNull String resourceType, @Nullable String selectorString) {
        readLock.lock();
        try {
            return persistedQueriesCache.get(getCacheKey(hash, resourceType, selectorString));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    @Nullable
    public String cacheQuery(@NotNull String query, @NotNull String resourceType, @Nullable String selectorString) {
        writeLock.lock();
        try {
            String hash = getHash(query);
            String key = getCacheKey(hash, resourceType, selectorString);
            persistedQueriesCache.put(key, query);
            if (persistedQueriesCache.containsKey(key)) {
                return key;
            }
            return null;
        } finally {
            writeLock.unlock();
        }
    }

    @NotNull
    private String getCacheKey(@NotNull String hash, @NotNull String resourceType, @Nullable String selectorString) {
        StringBuilder key = new StringBuilder(resourceType);
        if (StringUtils.isNotEmpty(selectorString)) {
            key.append("_").append(selectorString);
        }
        key.append("_").append(hash);
        return key.toString();
    }

    @NotNull String getHash(@NotNull String query) {
        StringBuilder buffer = new StringBuilder();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    buffer.append('0');
                }
                buffer.append(hex);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new SlingGraphQLException("Failed hashing query - " + e.getMessage());
        }
        return buffer.toString();
    }

    /**
     * This implementation provides a simple LRU eviction based on either the number of entries or the memory used by the stored values.
     * Synchronization has to happen externally.
     */
    private class InMemoryLRUCache extends LinkedHashMap<String, String> {

        private final int capacity;
        private final long maxSizeInBytes;
        private long currentSizeInBytes;

        public InMemoryLRUCache(int capacity, long maxSizeInBytes) {
            this.capacity = Math.max(capacity, 0);
            this.maxSizeInBytes = Math.max(maxSizeInBytes, 0);
            this.currentSizeInBytes = 0;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            boolean willRemove = false;
            if (capacity > 0) {
                willRemove = size() > capacity;
            } else if (maxSizeInBytes > 0) {
                willRemove = currentSizeInBytes > maxSizeInBytes;
            }
            if (willRemove) {
                evictions.increment();
                currentSizeInBytes -= getApproximateStringSizeInBytes(eldest.getValue());
            }
            return willRemove;
        }

        @Override
        public String put(String key, String value) {
            long valueSize = getApproximateStringSizeInBytes(value);
            if (capacity <= 0 && maxSizeInBytes > 0) {
                long newSizeInBytes;
                boolean isReplacement = containsKey(key);
                if (isReplacement) {
                    long oldValueSize = getApproximateStringSizeInBytes(get(key));
                    newSizeInBytes = currentSizeInBytes - oldValueSize + valueSize;
                } else {
                    // calculate what happens after removing LRU
                    newSizeInBytes = currentSizeInBytes + valueSize;
                    Optional<String> head = this.values().stream().findFirst();
                    if (head.isPresent()) {
                        newSizeInBytes -= getApproximateStringSizeInBytes(head.get());
                    }
                }
                if (newSizeInBytes <= maxSizeInBytes) {
                    if (isReplacement) {
                        currentSizeInBytes = newSizeInBytes;
                    } else {
                        currentSizeInBytes += valueSize;
                    }
                    return super.put(key, value);
                }
            } else {
                currentSizeInBytes += valueSize;
                return super.put(key, value);
            }
            return null;
        }

        int getApproximateStringSizeInBytes(@NotNull String string) {
            return 8 * (((string.length() * 2) + 45) / 8);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InMemoryLRUCache) {
                InMemoryLRUCache other = (InMemoryLRUCache) obj;
                return Objects.equals(capacity, other.capacity) && Objects.equals(maxSizeInBytes, other.maxSizeInBytes) &&
                        Objects.equals(currentSizeInBytes, other.currentSizeInBytes) && super.equals(obj);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return super.hashCode() + capacity + ((int) (maxSizeInBytes + currentSizeInBytes));
        }
    }
}
