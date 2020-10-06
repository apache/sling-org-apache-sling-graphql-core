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

import graphql.TypeResolutionEnvironment;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingTypeResolverEnvironment;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps the graphql-java TypeResolverEnvironment to provide
 * our own SlingTypeResolverEnvironment interface. This avoids
 * having to expose the graphql-java APIs in our own API.
 */
class TypeResolverEnvironmentWrapper implements SlingTypeResolverEnvironment {
    private final TypeResolutionEnvironment env;
    private final Resource currentResource;
    private final String options;
    private final String source;

    TypeResolverEnvironmentWrapper(TypeResolutionEnvironment env, Resource currentResource, String options,
                                   String source) {
        this.env = env;
        this.currentResource = currentResource;
        this.options = options;
        this.source = source;
    }

    @Override
    public Resource getCurrentResource() {
        return currentResource;
    }

    @Override
    public String getResolverOptions() {
        return options;
    }

    @Override
    public String getResolverSource() {
        return source;
    }

    @Override
    public Object getObject() {
        return env.getObject();
    }

    @Override
    public @Nullable Object getObjectType(String name) {
        return env.getSchema().getObjectType(name);
    }
}
