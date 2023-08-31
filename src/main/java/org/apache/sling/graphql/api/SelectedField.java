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
package org.apache.sling.graphql.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

import java.util.List;

/**
 * Interface to wrap information from <a href="https://javadoc.io/doc/com.graphql-java/graphql-java/latest/graphql/schema/SelectedField.html">GraphQL SelectedField</a>.
 *
 * <p>As described in {@link org.apache.sling.graphql.api.SelectionSet SelectionSet}, it is aimed to map the SelectedField to the minimum information
 * required when processing the query.</p><p>InlineFragment are mapped so that its isInline() is return true.</p>
 */
@ProviderType
public interface SelectedField {

    /**
     * @return the name as defined in the selection set.
     */
    @Nullable
    String getName();

    /** @return the simple qualified name of the selected field **/
    @Nullable
    String getQualifiedName();

    /**
     * @return the fully qualified name of the item
     */
    @Nullable
    String getFullyQualifiedName();

    /** @return level of the selected field within the query **/
    int getLevel();

    /** @return whether the field is conditionally present **/
    boolean isConditional();

    /**
     * @return the alias of the selected field or null if not alias was used
     */
    @Nullable
    String getAlias();

    /**
     * The result key is either the field query alias OR the field name in that preference order
     *
     * @return the result key of the selected field
     */
    @Nullable
    String getResultKey();

    /**
     * @return the sub selected fields.
     */
    @NotNull
    List<SelectedField> getSubSelectedFields();

    /**
     * @param name the sub selected field name.
     *             Note: If the name contains a dot it is looked up in the map of fully
     *             qualified names otherwise from the map with the regular names
     * @return the object or null if that doesn't exist.
     */
    @Nullable
    SelectedField getSubSelectedField(@NotNull String name);

    /**
     * @param name the sub selected field name(s) and they cannot be null
     *             Note: If the name contains a dot it is looked up in the map of fully
     *             qualified names otherwise from the map with the regular names
     * @return true if any of the sub selected fields exists.
     */
    boolean hasSubSelectedFields(@NotNull String ...name);

    /**
     * @return true if this field is an inline (i.e: ... on Something { }).
     * @deprecated There are no more inlined fragments anymore so this is always false
     */
    @Deprecated
    boolean isInline();

    /**
     * The Object Type Name is taken from the Normalized Field from GraphQL Java and denotes
     * any inlined fragment type that field can be part of
     *
     * @return List of Object Type Names which is always a List but might be empty
     */
    @NotNull
    List<String> getObjectTypeNames();
}
