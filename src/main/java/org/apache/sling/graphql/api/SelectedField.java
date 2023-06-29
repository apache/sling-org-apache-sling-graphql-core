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

    /**
     * @return the sub selected fields.
     */
    @NotNull
    List<SelectedField> getSubSelectedFields();

    /**
     * @param name the sub selected field name.
     * @return the object or null if that doesn't exist.
     */
    @Nullable
    SelectedField getSubSelectedField(String name);

    /**
     * @param name the sub selected field name(s).
     * @return true if any of the sub selected fields exists.
     */
    boolean hasSubSelectedFields(String ...name);

    /**
     * @return true if this field is an inline (i.e: ... on Something { }).
     */
    boolean isInline();

    List<String> getObjectTypeNames();
}
