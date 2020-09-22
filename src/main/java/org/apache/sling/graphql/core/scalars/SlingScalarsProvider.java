
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

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.sling.graphql.api.SlingScalarConverter;
import org.apache.sling.graphql.core.engine.SlingGraphQLException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

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

    private BundleContext bundleContext;
    
    @Activate
    public void activate(BundleContext ctx) {
        bundleContext = ctx;
    }

    @SuppressWarnings("unchecked")
    private GraphQLScalarType getScalar(String name) {

        // Ignore standard scalars
        if(ScalarInfo.isGraphqlSpecifiedScalar(name)) {
            return null;
        }

        SlingScalarConverter<Object, Object> converter = null;
        final String filter = String.format("(%s=%s)", SlingScalarConverter.NAME_SERVICE_PROPERTY, name);
        ServiceReference<?>[] refs= null;
        try {
            refs = bundleContext.getServiceReferences(SlingScalarConverter.class.getName(), filter);
        } catch(InvalidSyntaxException ise) {
            throw new SlingGraphQLException("Invalid OSGi filter syntax:" + filter);
        }
        if(refs != null) {
            // SlingScalarConverter services must have a unique name for now
            // (we might use a namespacing @directive in the schema to allow multiple ones with the same name)
            if(refs.length > 1) {
                throw new SlingGraphQLException(String.format("Got %d services for %s, expected just one", refs.length, filter));
            }
            converter = (SlingScalarConverter<Object, Object>)bundleContext.getService(refs[0]);
        }

        if(converter == null) {
            throw new SlingGraphQLException("SlingScalarConverter with name '" + name + "' not found");
        }

        return GraphQLScalarType.newScalar()
            .name(name)
            .description(converter.toString())
            .coercing(new SlingCoercingWrapper(converter))
            .build();
    }

    public Iterable<GraphQLScalarType> getCustomScalars(Map<String,ScalarTypeDefinition> schemaScalars) {
        // Using just the names for now, not sure why we'd need the ScalarTypeDefinitions
        return schemaScalars.keySet().stream()
            .map(this::getScalar)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

}
