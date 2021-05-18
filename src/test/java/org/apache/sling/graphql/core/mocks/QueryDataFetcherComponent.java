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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.helpers.GenericConnection;
import org.osgi.service.component.annotations.Component;

@Component(service=SlingDataFetcher.class, property = { "name=test/query"})
public class QueryDataFetcherComponent implements SlingDataFetcher<Object> {

    @Override
	public Object get(SlingDataFetcherEnvironment env) throws Exception {
        // Not a real query, just simulating that to test pagination
        final List<Resource> data = new ArrayList<>();
        data.add(env.getCurrentResource());
        data.add(env.getCurrentResource().getParent());
        data.add(env.getCurrentResource().getParent().getParent());
        final Function<Resource, String> cursorStringProvider = r -> r.getPath();
        return new GenericConnection.Builder<>(data.iterator(), cursorStringProvider).build();
    }
}
