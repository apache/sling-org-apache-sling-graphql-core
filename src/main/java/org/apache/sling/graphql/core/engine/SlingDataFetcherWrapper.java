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
package org.apache.sling.graphql.core.engine;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingGraphQLException;

/** Wraps a SlingDataFetcher to make it usable by graphql-java.
 *  The request {@link Resource} is read from {@link graphql.GraphQLContext}
 *  at fetch time so executable schemas can be reused across requests.
 */
class SlingDataFetcherWrapper<T> implements DataFetcher<T> {

    private final SlingDataFetcherSelector selector;
    private final String name;
    private final String options;
    private final String source;

    SlingDataFetcherWrapper(SlingDataFetcherSelector selector, String name, String options, String source) {
        this.selector = selector;
        this.name = name;
        this.options = options;
        this.source = source;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(DataFetchingEnvironment environment) throws Exception {
        final Resource currentResource = environment.getGraphQlContext().get(Resource.class);
        if (currentResource == null) {
            throw new SlingGraphQLException("GraphQLContext is missing the request Resource");
        }
        final SlingDataFetcher<T> fetcher = (SlingDataFetcher<T>) selector.getSlingFetcher(name);
        if (fetcher == null) {
            // Same as graphql-java when no DataFetcher is wired: read a matching source property.
            return (T) PropertyDataFetcher.fetching(environment.getField().getName())
                    .get(environment);
        }
        return fetcher.get(new DataFetchingEnvironmentWrapper(environment, currentResource, options, source));
    }
}
