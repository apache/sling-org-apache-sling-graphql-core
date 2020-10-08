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
package org.apache.sling.graphql.api.engine;

import java.util.Map;

import javax.json.JsonObject;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * A {@code QueryExecutor} service allows consumers to validate and execute GraphQL queries directly.
 */
@ProviderType
public interface QueryExecutor {

    /**
     * Validates the passed {@code query} and {@code variables}, by checking if the query obeys the known schemas.
     *
     * @param query         the query
     * @param variables     the query's variables; can be an empty {@link Map} if the query doesn't accept variables
     * @param queryResource the current resource, used as the root for the query
     * @param selectors     potential selectors used to select the schema applicable to the passed {@code query}
     * @return {code true} if the {@code query} is valid, {@code false} otherwise
     */
    boolean isValid(@NotNull String query, @NotNull Map<String, Object> variables, @NotNull Resource queryResource,
                    @NotNull String[] selectors);

    /**
     * Executes the passed {@code query}.
     *
     * @param query         the query
     * @param variables     the query's variables; can be an empty {@link Map} if the query doesn't accept variables
     * @param queryResource the current resource, used as the root for the query
     * @param selectors     potential selectors used to select the schema applicable to the passed {@code query}
     * @return a {@link JsonObject} representing the query's result
     * @throws SlingGraphQLException if the execution of the query leads to any issues
     */
    @NotNull
    JsonObject execute(@NotNull String query, @NotNull Map<String, Object> variables, @NotNull Resource queryResource,
                       @NotNull String[] selectors);
}
