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

import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import org.apache.sling.graphql.api.SelectedField;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implement a wrapper for GraphQL SelectedField.
 */
public class SelectedFieldWrapper implements SelectedField {

    private String name;
    private boolean isInline;
    private HashMap<String, SelectedField> subFieldMap = new HashMap<>();
    private List<SelectedField> subFields;

    public SelectedFieldWrapper(Selection selection) {
        SelectionSet selectionSet = null;
        if (selection instanceof InlineFragment) {
            InlineFragment inline = (InlineFragment) selection;
            this.name = inline.getTypeCondition().getName();
            this.isInline = true;
            selectionSet = inline.getSelectionSet();
        }
        if (selection instanceof Field) {
            Field subField = (Field) selection;
            this.name = subField.getName();
            selectionSet = subField.getSelectionSet();
        }
        if (selectionSet != null) {
            selectionSet.getSelections().forEach(s -> {
                SelectedFieldWrapper wrappedField = new SelectedFieldWrapper(s);
                subFieldMap.put(wrappedField.getName(), wrappedField);
            });
        }
        subFields = subFieldMap.values().stream().collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<SelectedField> getSubSelectedFields() {
        return subFields;
    }

    @Override
    public SelectedField getSubSelectedField(String name) {
        return subFieldMap.get(name);
    }

    @Override
    public boolean hasSubSelectedFields(String... name) {
        return Arrays.stream(name).anyMatch(subFieldMap::containsKey);
    }

    @Override
    public boolean isInline() {
        return isInline;
    }
}
