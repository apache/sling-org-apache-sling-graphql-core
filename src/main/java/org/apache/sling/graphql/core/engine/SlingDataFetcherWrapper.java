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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingDataFetcher;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

/** Wraps a SlingDataFetcher to make it usable by graphql-java */
class SlingDataFetcherWrapper<T> implements DataFetcher<T> {

    private final SlingDataFetcher<T> fetcher;
    private final Resource currentResource;
    private final String options;
    private final String source;

    SlingDataFetcherWrapper(SlingDataFetcher<T> fetcher, Resource currentResource, String options, String source) {
        this.fetcher = fetcher;
        this.currentResource = currentResource;
        this.options = options;
        this.source = source;
    }

    @Override
    public T get(DataFetchingEnvironment environment) throws Exception {
        return fetcher.get(new DataFetchingEnvironmentWrapper(environment, currentResource, options, source));
    }
}
