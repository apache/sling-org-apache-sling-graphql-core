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
import java.util.Hashtable;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.graphql.api.cache.GraphQLCacheProvider;
import org.apache.sling.graphql.core.engine.SlingGraphQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        immediate = true
)
public class GraphQLCacheProviderImpl implements GraphQLCacheProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLCacheProviderImpl.class);

    @Reference
    private CachingProvider cachingProvider;

    private Cache<String, String> persistedQueriesCache;

    private ServiceRegistration<GraphQLCacheProvider> registration;

    @Override
    @Nullable
    public String getQuery(@NotNull String hash, @NotNull String resourceType, @Nullable String selectorString) {
        return persistedQueriesCache.get(getCacheKey(hash, resourceType, selectorString));
    }

    @Override
    @NotNull
    public String cacheQuery(@NotNull String query, @NotNull String resourceType, @Nullable String selectorString) {
        String hash = getHash(query);
        String key = getCacheKey(hash, resourceType, selectorString);
        persistedQueriesCache.put(key, query);
        return hash;
    }


    @Activate
    private void activate(BundleContext bundleContext) {
        if (cachingProvider != null) {
            try {
                CacheManager cacheManager = cachingProvider.getCacheManager();
                persistedQueriesCache = cacheManager
                        .createCache(bundleContext.getBundle().getSymbolicName() + "_persistedQueries", new MutableConfiguration<>());
                registration = bundleContext.registerService(GraphQLCacheProvider.class, this, new Hashtable<>());
            } catch (Exception e) {
                LOGGER.error("Unable to configure a cache for persisted queries.", e);
            }
        } else {
            LOGGER.error("Cannot activate the " + GraphQLCacheProvider.class.getName() + " without a cachingProvider.");
        }
    }

    @Deactivate
    private void deactivate() {
        if (registration != null) {
            registration.unregister();
        }
        cachingProvider.close();
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

}
