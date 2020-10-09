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
import org.apache.sling.graphql.api.SlingDataFetcher;
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

/** Selects a SlingDataFetcher used to retrieve data, based
 *  on a name specified by a GraphQL schema directive.
 */
@Component(
        service=SlingDataFetcherSelector.class
)
public class SlingDataFetcherSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlingDataFetcherSelector.class);
    static final Pattern FETCHER_NAME_PATTERN = Pattern.compile("\\w+(/\\w+)+");

    private final Map<String, TreeSet<ServiceReferenceObjectTuple<SlingDataFetcher<Object>>>> dataFetchers = new HashMap<>();

    /** Fetchers which have a name starting with this prefix must be
     *  under the {#link RESERVED_PACKAGE_PREFIX} package.
     */
    public static final String RESERVED_NAME_PREFIX = "sling/";

    /** Package name prefix for fetchers which have names starting
     *  with the {#link RESERVED_NAME_PREFIX}.
     */
    public static final String RESERVED_PACKAGE_PREFIX = "org.apache.sling.";

    @Reference
    private ScriptedDataFetcherProvider scriptedDataFetcherProvider;

    /** @return a SlingDataFetcher, or null if none available. First tries to get an
     *  OSGi SlingDataFetcher service, and if not found tries to find a scripted SlingDataFetcher.
     */
    @Nullable
    public SlingDataFetcher<Object> getSlingFetcher(@NotNull String name) {
        SlingDataFetcher<Object> result = getOsgiServiceFetcher(name);
        if(result == null) {
            result = scriptedDataFetcherProvider.getDataFetcher(name);
        }
        return result;
    }

    /**
     * Returns a SlingFetcher from the available OSGi services, if there's one registered with the supplied name.
     */
    private SlingDataFetcher<Object> getOsgiServiceFetcher(@NotNull String name) {
        TreeSet<ServiceReferenceObjectTuple<SlingDataFetcher<Object>>> fetcherSet = dataFetchers.get(name);
        if (fetcherSet != null && !fetcherSet.isEmpty()) {
            return fetcherSet.last().getServiceObject();
        }
        return null;
    }

    private boolean hasValidName(@NotNull ServiceReference<SlingDataFetcher<Object>> serviceReference, @NotNull SlingDataFetcher<Object> fetcher) {
        String name = PropertiesUtil.toString(serviceReference.getProperty(SlingDataFetcher.NAME_SERVICE_PROPERTY), null);
        if (StringUtils.isNotEmpty(name)) {
            if (!nameMatchesPattern(name)) {
                LOGGER.error("Invalid SlingDataFetcher {}: fetcher name is not namespaced (e.g. ns/myFetcher)",
                        fetcher.getClass().getName());
                return false;
            }
            if (name.startsWith(RESERVED_NAME_PREFIX)) {
                final String className = fetcher.getClass().getName();
                if (!fetcher.getClass().getName().startsWith(RESERVED_PACKAGE_PREFIX)) {
                    LOGGER.error(
                            "Invalid SlingDataFetcher {}: fetcher names starting with '{}' are reserved for Apache Sling Java packages",
                            className, RESERVED_NAME_PREFIX);
                    return false;
                }
            }
        } else {
            LOGGER.error("Invalid {} implementation: fetcher {} is missing the mandatory value for its {} service property.",
                    SlingDataFetcher.class.getName(), fetcher.getClass().getName(), SlingDataFetcher.NAME_SERVICE_PROPERTY);
            return false;
        }
        return true;
    }

    static boolean nameMatchesPattern(String name) {
        if (StringUtils.isNotEmpty(name)) {
            return FETCHER_NAME_PATTERN.matcher(name).matches();
        }
        return false;
    }

    @Reference(
            service = SlingDataFetcher.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC
    )
    private void bindSlingDataFetcher(ServiceReference<SlingDataFetcher<Object>> reference, SlingDataFetcher<Object> slingDataFetcher) {
        if (hasValidName(reference, slingDataFetcher)) {
            synchronized (dataFetchers) {
                String name = (String) reference.getProperty(SlingDataFetcher.NAME_SERVICE_PROPERTY);
                TreeSet<ServiceReferenceObjectTuple<SlingDataFetcher<Object>>> fetchers = dataFetchers.computeIfAbsent(name,
                        key -> new TreeSet<>());
                fetchers.add(new ServiceReferenceObjectTuple<>(reference, slingDataFetcher));
            }
        }
    }

    @SuppressWarnings("unused")
    private void unbindSlingDataFetcher(ServiceReference<SlingDataFetcher<Object>> reference) {
        synchronized (dataFetchers) {
            String name = (String) reference.getProperty(SlingDataFetcher.NAME_SERVICE_PROPERTY);
            if (StringUtils.isNotEmpty(name)) {
                TreeSet<ServiceReferenceObjectTuple<SlingDataFetcher<Object>>> fetchers = dataFetchers.get(name);
                if (fetchers != null) {
                    Optional<ServiceReferenceObjectTuple<SlingDataFetcher<Object>>> tupleToRemove =
                            fetchers.stream().filter(tuple -> reference.equals(tuple.getServiceReference())).findFirst();
                    tupleToRemove.ifPresent(fetchers::remove);
                }
            }
        }
    }
}
