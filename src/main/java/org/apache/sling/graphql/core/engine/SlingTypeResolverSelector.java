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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.core.osgi.ServiceReferenceObjectTuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects a SlingTypeProvider used to get the corresponding object type,
 * based on a name specified by a GraphQL schema directive.
 */
@Component(service = SlingTypeResolverSelector.class)
public class SlingTypeResolverSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlingTypeResolverSelector.class);
    static final Pattern RESOLVER_NAME_PATTERN = Pattern.compile("\\w+(/\\w+)+");

    private final Map<String, TreeSet<ServiceReferenceObjectTuple<SlingTypeResolver<Object>>>> typeResolvers = new HashMap<>();

    /**
     * Resolvers which have a name starting with this prefix must be
     * under the {#link RESERVED_PACKAGE_PREFIX} package.
     */
    public static final String RESERVED_NAME_PREFIX = "sling/";

    /**
     * Package name prefix for resolvers which have names starting
     * with the {#link RESERVED_NAME_PREFIX}.
     */
    public static final String RESERVED_PACKAGE_PREFIX = "org.apache.sling.";

    /**
     * @return a SlingTypeResolver, or null if none available. First tries to get an
     * OSGi SlingTypeResolver service, and if not found tries to find a scripted SlingTypeResolver.
     */
    @Nullable
    public SlingTypeResolver<Object> getSlingTypeResolver(@NotNull String name) {
        TreeSet<ServiceReferenceObjectTuple<SlingTypeResolver<Object>>> resolvers = typeResolvers.get(name);
        if (resolvers != null && !resolvers.isEmpty()) {
            return resolvers.last().getServiceObject();
        }
        return null;
    }

    private boolean hasValidName(@NotNull ServiceReference<SlingTypeResolver<Object>> serviceReference,
                                 @NotNull SlingTypeResolver<Object> slingTypeResolver) {
        String name = PropertiesUtil.toString(serviceReference.getProperty(SlingTypeResolver.NAME_SERVICE_PROPERTY), null);
        if (StringUtils.isNotEmpty(name)) {
            if (!nameMatchesPattern(name)) {
                LOGGER.error("Invalid SlingTypeResolver {}: type resolver name is not namespaced (e.g. ns/myTypeResolver)",
                        slingTypeResolver.getClass().getName());
                return false;
            }
            if (name.startsWith(RESERVED_NAME_PREFIX)) {
                final String className = slingTypeResolver.getClass().getName();
                if (!slingTypeResolver.getClass().getName().startsWith(RESERVED_PACKAGE_PREFIX)) {
                    LOGGER.error(
                            "Invalid SlingTypeResolver {}: type resolver names starting with '{}' are reserved for Apache Sling Java " +
                                    "packages",
                            className, RESERVED_NAME_PREFIX);
                    return false;
                }
            }
        } else {
            LOGGER.error("Invalid {} implementation: type resolver {} is missing the mandatory value for its {} service property.",
                    SlingTypeResolver.class.getName(), slingTypeResolver.getClass().getName(), SlingTypeResolver.NAME_SERVICE_PROPERTY);
            return false;
        }
        return true;
    }

    static boolean nameMatchesPattern(String name) {
        if (StringUtils.isNotEmpty(name)) {
            return RESOLVER_NAME_PATTERN.matcher(name).matches();
        }
        return false;
    }

    @Reference(
            service = SlingTypeResolver.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    private void bindSlingTypeResolver(ServiceReference<SlingTypeResolver<Object>> reference, SlingTypeResolver<Object> slingTypeResolver) {
        if (hasValidName(reference, slingTypeResolver)) {
            synchronized (typeResolvers) {
                String name = (String) reference.getProperty(SlingTypeResolver.NAME_SERVICE_PROPERTY);
                TreeSet<ServiceReferenceObjectTuple<SlingTypeResolver<Object>>> resolvers = typeResolvers.computeIfAbsent(name,
                        key -> new TreeSet<>());
                resolvers.add(new ServiceReferenceObjectTuple<>(reference, slingTypeResolver));
            }
        }
    }

    @SuppressWarnings("unused")
    private void unbindSlingTypeResolver(ServiceReference<SlingTypeResolver<Object>> reference) {
        String name = (String) reference.getProperty(SlingTypeResolver.NAME_SERVICE_PROPERTY);
        if (StringUtils.isNotEmpty(name)) {
            synchronized (typeResolvers) {
                TreeSet<ServiceReferenceObjectTuple<SlingTypeResolver<Object>>> resolvers = typeResolvers.get(name);
                if (resolvers != null) {
                    Optional<ServiceReferenceObjectTuple<SlingTypeResolver<Object>>> tupleToRemove =
                            resolvers.stream().filter(tuple -> reference.equals(tuple.getServiceReference())).findFirst();
                    tupleToRemove.ifPresent(resolvers::remove);
                }
            }
        }
    }

}
