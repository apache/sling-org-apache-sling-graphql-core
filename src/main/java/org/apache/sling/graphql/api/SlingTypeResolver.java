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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * Resolves the GraphQL object type of a given result item. Services must be
 * registered with a NAME property with a unique value that's matched with the
 * corresponding @directive in the GraphQL Schema.
 */
@ConsumerType
public interface SlingTypeResolver<T> {
    String NAME_SERVICE_PROPERTY = "name";

    @Nullable
    T getType(@NotNull SlingTypeResolverEnvironment e);
}
