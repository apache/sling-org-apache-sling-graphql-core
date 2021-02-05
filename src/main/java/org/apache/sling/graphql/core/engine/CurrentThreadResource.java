/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.graphql.core.engine;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class CurrentThreadResource implements Resource {

    private static final Map<Thread, Resource> mappedResources = Collections.synchronizedMap(new HashMap<>());

    /**
     * @return the resource assigned to current thread
     */
    private static Resource getCurrentResource() {
        return mappedResources.get(Thread.currentThread());
    }

    /**
     * @param resource the resource assigned to current thread
     */
    static void setCurrentResource(final Resource resource) {
        mappedResources.putIfAbsent(Thread.currentThread(), resource);
    }

    /**
     * Remove existing map entry for current thread
     */
    static void dispose() {
        mappedResources.remove(Thread.currentThread());
    }

    @Override
    public @NotNull String getPath() {
        return getCurrentResource().getPath();
    }

    @Override
    public @NotNull String getName() {
        return getCurrentResource().getName();
    }

    @Override
    public @Nullable Resource getParent() {
        return getCurrentResource().getParent();
    }

    @Override
    public @NotNull Iterator<Resource> listChildren() {
        return getCurrentResource().listChildren();
    }

    @Override
    public @NotNull Iterable<Resource> getChildren() {
        return getCurrentResource().getChildren();
    }

    @Override
    public @Nullable Resource getChild(@NotNull String s) {
        return getCurrentResource().getChild(s);
    }

    @Override
    public @NotNull String getResourceType() {
        return getCurrentResource().getResourceType();
    }

    @Override
    public @Nullable String getResourceSuperType() {
        return getCurrentResource().getResourceSuperType();
    }

    @Override
    public boolean hasChildren() {
        return getCurrentResource().hasChildren();
    }

    @Override
    public boolean isResourceType(String s) {
        return getCurrentResource().isResourceType(s);
    }

    @Override
    public @NotNull ResourceMetadata getResourceMetadata() {
        return getCurrentResource().getResourceMetadata();
    }

    @Override
    public @NotNull ResourceResolver getResourceResolver() {
        return getCurrentResource().getResourceResolver();
    }

    @Override
    public @NotNull ValueMap getValueMap() {
        return getCurrentResource().getValueMap();
    }

    @Override
    public <AdapterType> @Nullable AdapterType adaptTo(@NotNull Class<AdapterType> aClass) {
        return getCurrentResource().adaptTo(aClass);
    }
}
