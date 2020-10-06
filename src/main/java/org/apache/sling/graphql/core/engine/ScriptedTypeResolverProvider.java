
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

import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;

/**
 * Resolves a SlingTypeResolver name to a SlingScript if available.
 */
@Component(service = ScriptedTypeResolverProvider.class)
public class ScriptedTypeResolverProvider {
    public static final String SCRIPT_NAME = "resolver";
    public static final String FAKE_RESOURCE_TYPE_PREFIX = "graphql/resolver/";

    @Reference
    private ServletResolver servletResolver;

    private static class FakeResource extends AbstractResource {

        private final String resourceType;

        FakeResource(String resourceType) {
            this.resourceType = resourceType;
        }

        @Override
        public @NotNull String getPath() {
            return "FAKE_RESOURCE_PATH";
        }

        @Override
        public @NotNull String getResourceType() {
            return resourceType;
        }

        @Override
        public String getResourceSuperType() {
            return null;
        }

        @Override
        public @NotNull ResourceMetadata getResourceMetadata() {
            throw new UnsupportedOperationException("Shouldn't be needed");
        }

        @Override
        public @NotNull ResourceResolver getResourceResolver() {
            throw new UnsupportedOperationException("Shouldn't be needed");
        }
    }

    @Nullable
    SlingTypeResolver<Object> getTypeResolver(@NotNull String name) {
        final Resource r = new FakeResource(FAKE_RESOURCE_TYPE_PREFIX + name);
        final Servlet s = servletResolver.resolveServlet(r, SCRIPT_NAME);
        if (s instanceof SlingScript) {
            return new SlingTypeResolverScriptWrapper((SlingScript) s);
        }
        return null;
    }
}
