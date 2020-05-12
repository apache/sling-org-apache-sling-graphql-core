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

import java.io.IOException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.graphqljava.DataFetcherProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

public class EchoDataFetcherProvider implements DataFetcherProvider {

    private final String name;
    private final Object staticData;

    static class EchoDataFetcher implements DataFetcher<Object> {
        private final Object data;

        EchoDataFetcher(Object data) {
            this.data = data;
        }

        @Override
        public Object get(DataFetchingEnvironment environment) {
            return data;
        }
    }

    public EchoDataFetcherProvider(String name) {
        this(name, null);
    }

    public EchoDataFetcherProvider(String name, Object staticData) {
        this.name = name;
        this.staticData = staticData;
    }

    @Override
    public @Nullable DataFetcher<Object> createDataFetcher(@NotNull Resource r, @NotNull String name,
            @Nullable String options, @Nullable String source) throws IOException {
        if(this.name.equals(name)) {
            Object value = null;
            if("null".equals(options)) {
                // keep null value
            } else if(staticData != null) {
                value = staticData;
            } else {
                value = r;
            }
            return new EchoDataFetcher(value);
        } else {
            return null;
        }
    }
}
