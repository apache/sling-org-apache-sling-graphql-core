
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.graphql.api.SlingScalarConverter;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.core.osgi.ServiceReferenceObjectTuple;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

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

    private final Map<String, TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>>> scalars = new HashMap<>();

    @Reference(
            service = SlingScalarConverter.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    private void bindSlingScalarConverter(ServiceReference<SlingScalarConverter<Object, Object>> serviceReference,
                                          SlingScalarConverter<Object, Object> scalarConverter) {
        String name = (String) serviceReference.getProperty(SlingScalarConverter.NAME_SERVICE_PROPERTY);
        if (StringUtils.isNotEmpty(name)) {
            synchronized (scalars) {
                TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>> set =
                        scalars.computeIfAbsent(name, key -> new TreeSet<>());
                set.add(new ServiceReferenceObjectTuple<>(serviceReference, scalarConverter));
            }
        }
    }

    @SuppressWarnings("unused")
    private void unbindSlingScalarConverter(ServiceReference<SlingScalarConverter<Object, Object>> serviceReference) {
        String name = (String) serviceReference.getProperty(SlingScalarConverter.NAME_SERVICE_PROPERTY);
        if (StringUtils.isNotEmpty(name)) {
            synchronized (scalars) {
                TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>> set = scalars.get(name);
                if (set != null) {
                    Optional<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>> tupleToRemove =
                            set.stream().filter(tuple -> serviceReference.equals(tuple.getServiceReference())).findFirst();
                    tupleToRemove.ifPresent(set::remove);
                }
            }
        }
    }

    private GraphQLScalarType getScalar(String name) {
        // Ignore standard scalars
        if(ScalarInfo.isGraphqlSpecifiedScalar(name)) {
            return null;
        }
        TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>> set = scalars.get(name);
        if (set == null || set.isEmpty()) {
            throw new SlingGraphQLException("SlingScalarConverter with name '" + name + "' not found");
        }
        SlingScalarConverter<Object, Object> converter = set.last().getServiceObject();

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
