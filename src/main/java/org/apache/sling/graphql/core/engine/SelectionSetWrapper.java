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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implement a wrapper for GraphQL DataFetchingFieldSelectionSet.
 */
public class SelectionSetWrapper implements SelectionSet {

    private List<SelectedField> fields = new ArrayList<>();

    private Map<String, SelectedField> fieldsMap = new HashMap<>();

    public SelectionSetWrapper(@Nullable DataFetchingFieldSelectionSet selectionSet) {
        if (selectionSet != null) {
            selectionSet.get().getSubFields().forEach((k, v) -> {
                SelectedFieldWrapper selectedField = new SelectedFieldWrapper(v.getSingleField());
                fieldsMap.put(k, selectedField);
                if (!k.contains("/")) {
                    fields.add(selectedField);
                }
            });
            initFlatMap(fields, "");
        }
    }

    private void initFlatMap(List<SelectedField> parentList, String qualifiedPath) {
        parentList.forEach(s -> {
           String qualifiedName = qualifiedPath + s.getName();
           if (!fieldsMap.containsKey(qualifiedName)) {
               fieldsMap.put(qualifiedName, s);
           }
           initFlatMap(s.getSubSelectedFields(), qualifiedName + "/");
        });
    }

    @Override
    @NotNull
    public List<SelectedField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    @Override
    public boolean contains(String qualifiedName) {
        return fieldsMap.containsKey(qualifiedName);
    }

    @Override
    public SelectedField get(String qualifiedName) {
        return fieldsMap.get(qualifiedName);
    }
}
