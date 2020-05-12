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

package org.apache.sling.graphql.core.mocks;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.graphqljava.DataFetcherProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class FailingDataFetcherProvider implements DataFetcherProvider {

    private final String name;

    static class FailingDataFetcher implements DataFetcher<Object> {
        @Override
        public Object get(DataFetchingEnvironment environment) {
            throw new RuntimeException(getClass().getSimpleName());
        }
    }

    public FailingDataFetcherProvider(String name) {
        this.name = name;
    }

    @Override
    public @Nullable DataFetcher<Object> createDataFetcher(@NotNull Resource r, @NotNull String name,
            @Nullable String options, @Nullable String source) throws IOException {
        if(this.name.equals(name)) {
            return new FailingDataFetcher();
        } else {
            return null;
        }
    }
}
