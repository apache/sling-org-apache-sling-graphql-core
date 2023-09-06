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

import graphql.com.google.common.collect.ImmutableList;
import graphql.schema.DataFetchingFieldSelectionSet;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.sling.graphql.api.SelectedField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implement a wrapper for GraphQL SelectedField.
 *
 * ATTENTION: here we are assuming that fields added are unqiue by the Fully Qualified Name (FQN).
 *
 * This updated version is keeping duplicate fields by field's simple name. Use getFirstSubSelectedFieldByName() if
 * you are sure that there is only one field with that simple name otherwise use hasDuplicateFieldByName() to determine
 * if there are duplicates or use the fully qualified name (FQN) to find a field.
 */
public class SelectedFieldWrapper implements SelectedField {

    private final String name;
    private final String fullyQualifiedName;
    private final String qualifiedName;
    private final boolean conditional;
    private final int level;
    private final String alias;
    private final String resultKey;
    private final List<String> objectTypeNames;
    private final MultiValuedMap<String, SelectedField> subFieldMap = new HashSetValuedHashMap<>();
    private final Map<String, SelectedField> subFQNFieldMap = new HashMap<>();
    private final List<SelectedField> subFields;

    public SelectedFieldWrapper(graphql.schema.SelectedField selectedField) {
        this.name = selectedField.getName();
        this.qualifiedName = selectedField.getQualifiedName();
        this.fullyQualifiedName = selectedField.getFullyQualifiedName();
        this.objectTypeNames = selectedField.getObjectTypeNames() == null ? Collections.emptyList() : new ArrayList<>(selectedField.getObjectTypeNames());
        this.conditional = selectedField.isConditional();
        this.level = selectedField.getLevel();
        this.alias = selectedField.getAlias();
        this.resultKey = selectedField.getResultKey();
        DataFetchingFieldSelectionSet selectionSet = selectedField.getSelectionSet();
        if (selectionSet != null) {
            selectionSet.getImmediateFields().forEach(sf -> {
                SelectedFieldWrapper selectedChildField = new SelectedFieldWrapper(sf);
                subFieldMap.put(sf.getName(), selectedChildField);
                subFQNFieldMap.put(sf.getFullyQualifiedName(), selectedChildField);
            });
        }
        // Fields are not taken from the FQN Map to avoid dropping fields with the same name
        subFields = new ArrayList<>(subFQNFieldMap.values());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable String getQualifiedName() {
        return qualifiedName;
    }

    @Override
    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public boolean isConditional() {
        return conditional;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public String getResultKey() {
        return resultKey;
    }

    @Override
    @NotNull
    public List<SelectedField> getSubSelectedFields() {
        return subFields;
    }

    @Override
    @NotNull
    public Collection<SelectedField> getSubSelectedFieldByName(@NotNull String name) {
        return subFieldMap.get(name);
    }

    @Override
    @Nullable
    public SelectedField getFirstSubSelectedFieldByName(@NotNull String name) {
        Collection<SelectedField> fields = getSubSelectedFieldByName(name);
        return fields.isEmpty() ? null : fields.iterator().next();
    }

    @Override
    @Nullable
    public SelectedField getSubSelectedFieldByFQN(@NotNull String fullyQualifiedName) {
        return subFQNFieldMap.get(fullyQualifiedName);
    }

    @Override
    public boolean hasDuplicateFieldByName(@NotNull String name) {
        return subFieldMap.get(name).size() > 1;
    }

    @Override
    public boolean hasSubSelectedFieldsByName(@NotNull String... name) {
        return Arrays.stream(name).anyMatch(subFieldMap::containsKey);
    }

    @Override
    public boolean hasSubSelectedFieldsByFQN(@NotNull String... fullyQualifiedName) {
        return Arrays.stream(fullyQualifiedName).anyMatch(subFQNFieldMap::containsKey);
    }

    @Deprecated
    @Override
    public SelectedField getSubSelectedField(@NotNull String name) {
        return name.indexOf('.') >= 0 ?
                getSubSelectedFieldByFQN(name) :
                getFirstSubSelectedFieldByName(name);
    }

    @Deprecated
    @Override
    public boolean hasSubSelectedFields(@NotNull String... name) {
        return name[0].indexOf('.') >= 0 ?
                Arrays.stream(name).anyMatch(subFQNFieldMap::containsKey) :
                Arrays.stream(name).anyMatch(subFieldMap::containsKey);
    }

    @Deprecated
    @Override
    public boolean isInline() {
        return false;
    }

    @Override
    @NotNull
    public List<String> getObjectTypeNames() {
        return ImmutableList.copyOf(objectTypeNames);
    }
}
