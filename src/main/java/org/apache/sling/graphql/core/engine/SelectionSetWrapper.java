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

import graphql.schema.DataFetchingFieldSelectionSet;
import org.apache.sling.graphql.api.SelectedField;
import org.apache.sling.graphql.api.SelectionSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implement a wrapper for GraphQL DataFetchingFieldSelectionSet.
 */
public class SelectionSetWrapper implements SelectionSet {

    private List<SelectedField> fields = Collections.EMPTY_LIST;

    public SelectionSetWrapper(@Nullable DataFetchingFieldSelectionSet selectionSet) {
        if (selectionSet != null) {
            this.fields = selectionSet.getFields().stream().map(SelectedFieldWrapper::new).collect(Collectors.toList());
        }
    }

    @Override
    public List<SelectedField> getFields() {
        return fields;
    }

    @Override
    public boolean hasFields() {
        return !fields.isEmpty();
    }
}
