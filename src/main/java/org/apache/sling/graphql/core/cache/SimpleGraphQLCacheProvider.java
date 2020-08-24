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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.graphql.api.cache.GraphQLCacheProvider;
import org.apache.sling.graphql.core.engine.SlingGraphQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                description = "The number of persisted queries to cache.",
                type = AttributeType.INTEGER,
                min = "0"
        )
        int capacity() default DEFAULT_CACHE_SIZE;
    }

    private static final int DEFAULT_CACHE_SIZE = 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleGraphQLCacheProvider.class);

    private InMemoryLRUCache persistedQueriesCache;
    private ReadWriteLock readWriteLock;
    private Lock readLock;
    private Lock writeLock;

    @Activate
    public SimpleGraphQLCacheProvider(Config config) {
        readWriteLock = new ReentrantReadWriteLock();
        readLock = readWriteLock.readLock();
        writeLock = readWriteLock.writeLock();
        int cacheSize = config.capacity();
        if (cacheSize < 0) {
            cacheSize = 0;
            LOGGER.debug("Cache capacity set to {}. Defaulting to 0.", config.capacity());
        }
        persistedQueriesCache = new InMemoryLRUCache(cacheSize);
        LOGGER.debug("Initialized the in-memory cache for a maximum of {} queries.", config.capacity());
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
    @NotNull
    public String cacheQuery(@NotNull String query, @NotNull String resourceType, @Nullable String selectorString) {
        writeLock.lock();
        try {
            String hash = getHash(query);
            String key = getCacheKey(hash, resourceType, selectorString);
            persistedQueriesCache.put(key, query);
            return hash;
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

    private static class InMemoryLRUCache extends LinkedHashMap<String, String> {

        private final int capacity;

        public InMemoryLRUCache(int capacity) {
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > capacity;
        }
    }

}
