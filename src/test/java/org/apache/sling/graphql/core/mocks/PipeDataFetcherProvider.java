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
import org.osgi.service.component.annotations.Component;

@Component(service=DataFetcherProvider.class, property = { "namespace=test"})
public class PipeDataFetcherProvider implements DataFetcherProvider {

    static class PipeDataFetcher implements DataFetcher<Object> {

        private final Object value;

        PipeDataFetcher(Object value) {
            this.value = value;
        }

        @Override
        public Object get(DataFetchingEnvironment environment) throws Exception {
            return value;
        }

    }

    @Override
    public DataFetcher<Object> createDataFetcher(Resource r, String name, String options, String source) {
        if("pipe".equals(name)) {
            if("farenheit".equals(options)) {
                return new PipeDataFetcher(451);
            } else {
                return new PipeDataFetcher(r);
            }
        } else {
            return null;
        }

    }
}
