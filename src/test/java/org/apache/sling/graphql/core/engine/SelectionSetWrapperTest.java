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

import java.util.Arrays;
import java.util.List;

import graphql.schema.DataFetchingFieldSelectionSet;
import org.apache.sling.graphql.api.SelectedField;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class SelectionSetWrapperTest {

    /** Null selectionSet must yield an empty wrapper without NPE. */
    @Test
    public void testNullSelectionSet() {
        SelectionSetWrapper wrapper = new SelectionSetWrapper(null);
        assertTrue("fields must be empty for null input", wrapper.getFields().isEmpty());
    }

    /** Two fields with different names are both kept independently. */
    @Test
    public void testDistinctFields() {
        graphql.schema.SelectedField sfA = mockField("fieldA", "Model.fieldA");
        graphql.schema.SelectedField sfB = mockField("fieldB", "Model.fieldB");

        DataFetchingFieldSelectionSet selSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(sfA, sfB)).when(selSet).getImmediateFields();

        SelectionSetWrapper wrapper = new SelectionSetWrapper(selSet);

        List<SelectedField> fields = wrapper.getFields();
        assertEquals("Expected 2 top-level fields", 2, fields.size());
        assertNotNull("fieldA must be accessible by name", wrapper.get("fieldA"));
        assertNotNull("fieldB must be accessible by name", wrapper.get("fieldB"));
        assertTrue("contains(fieldA)", wrapper.contains("fieldA"));
        assertTrue("contains(fieldB)", wrapper.contains("fieldB"));
    }

    /**
     * Two top-level entries with the same simple name (different aliases pointing at the same field).
     * The second entry must be merged into the first, not added as a second top-level field.
     * Sub-fields present in only one of the entries must be visible after the merge.
     */
    @Test
    public void testDuplicateFieldNameMergesSubFields() {
        // Sub-field present only in the first alias
        graphql.schema.SelectedField subA = mockField("subA", "ItemModel.subA");
        // Sub-field present only in the second alias
        graphql.schema.SelectedField subB = mockField("subB", "ItemModel.subB");

        // First alias selects only subA
        DataFetchingFieldSelectionSet selSet1 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(subA)).when(selSet1).getImmediateFields();
        graphql.schema.SelectedField item1 = mockFieldWithSelectionSet("item", "HolderModel.item", selSet1);

        // Second alias selects only subB
        DataFetchingFieldSelectionSet selSet2 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(subB)).when(selSet2).getImmediateFields();
        graphql.schema.SelectedField item2 = mockFieldWithSelectionSet("item", "HolderModel.item", selSet2);

        DataFetchingFieldSelectionSet root = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(item1, item2)).when(root).getImmediateFields();

        SelectionSetWrapper wrapper = new SelectionSetWrapper(root);

        // Only one top-level field named "item"
        assertEquals(
                "Duplicate-named entries must be merged to a single top-level field",
                1,
                wrapper.getFields().size());

        // The merged field must expose sub-fields from BOTH aliases
        SelectedField merged = wrapper.get("item");
        assertNotNull("merged item field not found", merged);
        assertTrue(
                "subA (from first alias) must be present after merge",
                merged.hasSubSelectedFieldsByFQN("ItemModel.subA"));
        assertTrue(
                "subB (from second alias) must be present after merge",
                merged.hasSubSelectedFieldsByFQN("ItemModel.subB"));
        assertEquals(
                "merged item must have 2 sub-fields",
                2,
                merged.getSubSelectedFields().size());
    }

    /**
     * A field whose name contains "/" is treated as a flat-map path entry only and must
     * not appear in the top-level fields list.
     */
    @Test
    public void testSlashFieldExcludedFromTopLevel() {
        graphql.schema.SelectedField slashField = mockField("parent/child", "Model.parentChild");

        DataFetchingFieldSelectionSet selSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(slashField)).when(selSet).getImmediateFields();

        SelectionSetWrapper wrapper = new SelectionSetWrapper(selSet);

        assertTrue(
                "Slash-named field must not appear in top-level fields list",
                wrapper.getFields().isEmpty());
    }

    /** Nested sub-fields must be accessible via slash-qualified flat-map paths. */
    @Test
    public void testNestedFieldAccessibleByQualifiedPath() {
        graphql.schema.SelectedField leaf = mockField("leaf", "ChildModel.leaf");
        DataFetchingFieldSelectionSet childSelSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(leaf)).when(childSelSet).getImmediateFields();

        graphql.schema.SelectedField parent = mockFieldWithSelectionSet("parent", "Model.parent", childSelSet);

        DataFetchingFieldSelectionSet root = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(parent)).when(root).getImmediateFields();

        SelectionSetWrapper wrapper = new SelectionSetWrapper(root);

        assertNotNull("parent must be found by simple name", wrapper.get("parent"));
        assertNotNull("leaf must be found by flat qualified path parent/leaf", wrapper.get("parent/leaf"));
        assertNull("unknown path must return null", wrapper.get("parent/unknown"));
    }

    // --- helpers ---

    private static graphql.schema.SelectedField mockField(String name, String fqn) {
        graphql.schema.SelectedField sf = mock(graphql.schema.SelectedField.class);
        doReturn(name).when(sf).getName();
        doReturn(name).when(sf).getQualifiedName();
        doReturn(fqn).when(sf).getFullyQualifiedName();
        return sf;
    }

    private static graphql.schema.SelectedField mockFieldWithSelectionSet(
            String name, String fqn, DataFetchingFieldSelectionSet selSet) {
        graphql.schema.SelectedField sf = mockField(name, fqn);
        doReturn(selSet).when(sf).getSelectionSet();
        return sf;
    }
}
