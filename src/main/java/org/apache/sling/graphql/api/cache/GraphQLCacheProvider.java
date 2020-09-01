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
package org.apache.sling.graphql.api.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * A {@code GraphQLCacheProvider} is responsible for caching GraphQL queries, in order to provide support for persisted queries for the
 * {@link org.apache.sling.graphql.core.servlet.GraphQLServlet}.
 */
@ProviderType
public interface GraphQLCacheProvider {

    /**
     * Attempts to retrieve a previously persisted query from the cache.
     *
     * @param hash           the query's SHA-256 hash
     * @param resourceType   the resource type of the {@link org.apache.sling.graphql.core.servlet.GraphQLServlet} which will execute the
     *                       query, since multiple servlets can be registered
     * @param selectorString the selector string with which the {@link org.apache.sling.graphql.core.servlet.GraphQLServlet} is registered
     * @return the query, if found, {@code null} otherwise
     * @see #cacheQuery(String, String, String)
     */
    @Nullable String getQuery(@NotNull String hash, @NotNull String resourceType, @Nullable String selectorString);

    /**
     * Stores the {@code query} into the cache, potentially overriding a previous value.
     *
     * @param query          the GraphQL query
     * @param resourceType   the resource type of the {@link org.apache.sling.graphql.core.servlet.GraphQLServlet} which will execute the
     *                       query, since multiple servlets can be registered
     * @param selectorString the selector string with which the {@link org.apache.sling.graphql.core.servlet.GraphQLServlet} is registered
     * @return the query's SHA-256 hash, which will be passed to the GraphQL client for query retrieval
     */
    @NotNull String cacheQuery(@NotNull String query, @NotNull String resourceType, @Nullable String selectorString);
}
