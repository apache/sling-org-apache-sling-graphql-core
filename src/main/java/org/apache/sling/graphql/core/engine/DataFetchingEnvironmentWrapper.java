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

import java.util.Map;

import graphql.schema.DataFetchingFieldSelectionSet;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SelectionSet;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;

import graphql.schema.DataFetchingEnvironment;

/** Wraps the graphql-java DataFetchingEnvironment to provide 
 *  our own SlingDataFetcherEnvironment interface. This avoids
 *  having to expose the graphql-java APIs in our own API.
  */
class DataFetchingEnvironmentWrapper implements SlingDataFetcherEnvironment {
    private final DataFetchingEnvironment env;
    private final Resource currentResource;
    private final String options;
    private final String source;
    private final SelectionSet selectionSet;

    DataFetchingEnvironmentWrapper(DataFetchingEnvironment env, Resource currentResource, String options, String source) {
        this.env = env;
        this.currentResource = currentResource;
        this.options = options;
        this.source = source;
        this.selectionSet = new SelectionSetWrapper(env.getSelectionSet());
    }

    @Override
    public Object getParentObject() {
        return env.getSource();
    }

    @Override
    public Map<String, Object> getArguments() {
        return env.getArguments();
    }

    @Override
    public <T> T getArgument(String name) {
        return env.getArgument(name);
    }

    @Override
    public <T> T getArgument(String name, T defaultValue) {
        return env.getArgumentOrDefault(name, defaultValue);
    }

    @Override
    public Resource getCurrentResource() {
        return currentResource;
    }

    @Override
    public String getFetcherOptions() {
        return options;
    }

    @Override
    public String getFetcherSource() {
        return source;
    }

    @Override
    public SelectionSet getSelectionSet() {
        return selectionSet;
    }
}
