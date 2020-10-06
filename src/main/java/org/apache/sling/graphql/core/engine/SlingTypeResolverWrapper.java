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
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingTypeResolver;

/**
 * Wraps a SlingTypeResolver to make it usable by graphql-java
 */
class SlingTypeResolverWrapper implements TypeResolver {

    private final SlingTypeResolver<Object> resolver;
    private final Resource currentResource;
    private final String options;
    private final String source;

    SlingTypeResolverWrapper(SlingTypeResolver<Object> resolver, Resource currentResource, String options,
                             String source) {
        this.resolver = resolver;
        this.currentResource = currentResource;
        this.options = options;
        this.source = source;
    }

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment environment) {
        Object r = resolver.getType(new TypeResolverEnvironmentWrapper(environment, currentResource, options, source));
        if (r instanceof GraphQLObjectType) {
            return (GraphQLObjectType) r;
        }
        return null;
    }
}
