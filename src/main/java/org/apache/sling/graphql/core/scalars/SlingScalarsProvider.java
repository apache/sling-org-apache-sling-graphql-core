
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

package org.apache.sling.graphql.core.scalars;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.script.ScriptException;

import org.apache.sling.graphql.api.SlingScalarConverter;
import org.apache.sling.graphql.api.SlingScalarConvertersProvider;
import org.apache.sling.graphql.core.engine.SlingGraphQLException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.ScalarInfo;

/**
 * Provides GraphQL Scalars (leaf data types) for query execution
 */
@Component(service = SlingScalarsProvider.class, property = {
    Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting GraphQL Scalars Provider",
    Constants.SERVICE_VENDOR + "=The Apache Software Foundation" })
public class SlingScalarsProvider {

    @Reference
    private SlingScalarConvertersProvider slingScalarConvertersProvider;

    public List<GraphQLScalarType> getCustomScalars() {
        return slingScalarConvertersProvider.getScalarConverters().stream()
                .map(converter -> GraphQLScalarType.newScalar()
                        .name(converter.getName())
                        .description(converter.getDescription())
                        .coercing(new SlingCoercingWrapper(converter))
                        .build())
                .collect(Collectors.toList());
    }

}
