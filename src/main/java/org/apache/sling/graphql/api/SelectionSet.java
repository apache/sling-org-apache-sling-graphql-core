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
 * Interface to wrap information from <a href="https://javadoc.io/doc/com.graphql-java/graphql-java/latest/graphql/schema/DataFetchingFieldSelectionSet.html">GraphQL DataFetchingFieldSelectionSet</a>.
 * <p>Mainly it keeps information about fields name that got selected.</p>
 * <pre>
 * For example:
 * {@code
 *   queryName {
 *       field1
 *       field2 {
 *           ... on Type1 {
 *               field3
 *           }
 *       }
 *       field4
 *       field5 {
 *           field6
 *           field7 {
 *               field8
 *           }
 *       }
 *   }
 * }
 * </pre>
 *
 * <p>Would result in a mapping with corresponding SelectedField(s).</p>
 * <p><b>field1</b> would be accessible with qualified name "field1"
 * while <b>field3</b> would be accessible with qualified name "field2/Type1/field3"
 * and <b>field8</b> would be accessible with qualified name "field5/field7/field8"
 * </p>
 * <p><b>Type1</b> would be a SelectedField with isInline() returning true</p>
 */
@ProviderType
public interface SelectionSet {

    /**
     * @return the immediate list of fields in the selection.
     */
    @NotNull
    List<SelectedField> getFields();

    /**
     * @return true if the field qualified name exist.
     */
    boolean contains(String qualifiedName);

    /**
     * @return SelectedField for qualified name.
     */
    @Nullable
    SelectedField get(String qualifiedName);
}
