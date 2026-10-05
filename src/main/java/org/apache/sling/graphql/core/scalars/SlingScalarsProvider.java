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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;
import graphql.schema.idl.ScalarInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.SlingScalarConverter;
import org.apache.sling.graphql.core.osgi.ServiceReferenceObjectTuple;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

/**
 * Provides GraphQL Scalars (leaf data types) for query execution
 */
@Component(
        service = SlingScalarsProvider.class,
        property = {
            Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting GraphQL Scalars Provider",
            Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
        })
public class SlingScalarsProvider {

    private final Map<String, TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>>> scalars =
            new HashMap<>();

    /**
     * Monotonic revision of the converter set. Bumped after every successful bind/unbind map update
     * so callers can detect whether a schema built from an earlier snapshot is still current.
     */
    private final AtomicLong scalarGeneration = new AtomicLong();

    @Reference(
            service = SlingScalarConverter.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC)
    private void bindSlingScalarConverter(
            ServiceReference<SlingScalarConverter<Object, Object>> serviceReference,
            SlingScalarConverter<Object, Object> scalarConverter) {
        String name = (String) serviceReference.getProperty(SlingScalarConverter.NAME_SERVICE_PROPERTY);
        if (StringUtils.isNotEmpty(name)) {
            synchronized (scalars) {
                TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>> set =
                        scalars.computeIfAbsent(name, key -> new TreeSet<>());
                set.add(new ServiceReferenceObjectTuple<>(serviceReference, scalarConverter));
                scalarGeneration.incrementAndGet();
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
                            set.stream()
                                    .filter(tuple -> serviceReference.equals(tuple.getServiceReference()))
                                    .findFirst();
                    if (tupleToRemove.isPresent()) {
                        set.remove(tupleToRemove.get());
                        scalarGeneration.incrementAndGet();
                    }
                }
            }
        }
    }

    /**
     * @return current scalar converter generation; safe to sample outside a build and re-check before cache publish
     */
    public long getScalarGeneration() {
        return scalarGeneration.get();
    }

    /**
     * Returns custom scalars for the given schema together with the converter generation observed while
     * reading the map, so the caller can refuse to cache if converters change before publication.
     * Converter toString() and coercing-wrapper construction run outside the
     * {@code scalars} monitor so a slow converter cannot stall OSGi bind/unbind.
     */
    public CustomScalars getCustomScalars(Map<String, ScalarTypeDefinition> schemaScalars) {
        long generation;
        List<NamedConverter> snapshot;
        synchronized (scalars) {
            generation = scalarGeneration.get();
            snapshot = new ArrayList<>();
            for (String name : schemaScalars.keySet()) {
                if (ScalarInfo.isGraphqlSpecifiedScalar(name)) {
                    continue;
                }
                TreeSet<ServiceReferenceObjectTuple<SlingScalarConverter<Object, Object>>> set = scalars.get(name);
                if (set == null || set.isEmpty()) {
                    throw new SlingGraphQLException("SlingScalarConverter with name '" + name + "' not found");
                }
                snapshot.add(new NamedConverter(name, set.last().getServiceObject()));
            }
        }
        List<GraphQLScalarType> list = new ArrayList<>(snapshot.size());
        for (NamedConverter named : snapshot) {
            list.add(GraphQLScalarType.newScalar()
                    .name(named.name)
                    .description(named.converter.toString())
                    .coercing(new SlingCoercingWrapper(named.converter))
                    .build());
        }
        return new CustomScalars(generation, list);
    }

    private static final class NamedConverter {
        private final String name;
        private final SlingScalarConverter<Object, Object> converter;

        private NamedConverter(String name, SlingScalarConverter<Object, Object> converter) {
            this.name = name;
            this.converter = converter;
        }
    }

    /**
     * Snapshot of custom scalar types plus the provider generation at read time.
     */
    public static final class CustomScalars {
        private final long generation;
        private final Iterable<GraphQLScalarType> scalars;

        public CustomScalars(long generation, Iterable<GraphQLScalarType> scalars) {
            this.generation = generation;
            this.scalars = scalars;
        }

        public long getGeneration() {
            return generation;
        }

        public Iterable<GraphQLScalarType> getScalars() {
            return scalars;
        }
    }
}
