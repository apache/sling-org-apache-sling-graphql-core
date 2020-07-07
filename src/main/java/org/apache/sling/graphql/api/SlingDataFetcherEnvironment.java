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

package org.apache.sling.graphql.api;

import java.util.Map;
import org.apache.sling.api.resource.Resource;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/** Provides contextual information to the {#link SlingDataFetcher} */
@SuppressWarnings("TypeParameterUnusedInFormals")

@ProviderType
public interface SlingDataFetcherEnvironment {

    /** @return the parent object of the field that's being retrieved */
    @Nullable
    Object getParentObject();

    /** @return the arguments passed to the GraphQL query */
    @Nullable
    Map<String, Object> getArguments();

    /**
     * @param <T> the argument type
     * @param name the name of the argument to return
     * @return a single argument, passed to the GraphQL query */
    @Nullable
    <T> T getArgument(String name);

    /**
     * @param <T> the argument type
     * @param name the name of the argument to return
     * @param defaultValue the default value to return
     * @return a single argument, passed to the GraphQL query */
    @Nullable
    <T> T getArgument(String name, T defaultValue);

    /** @return the current Sling resource */
    @Nullable
    Resource getCurrentResource();

    /** @return the options, if set by the schema directive */
    @Nullable
    String getFetcherOptions();

    /** @return the source, if set by the schema directive */
    @Nullable
    String getFetcherSource();
}
