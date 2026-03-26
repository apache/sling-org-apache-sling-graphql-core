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

import graphql.schema.DataFetchingFieldSelectionSet;
import org.apache.sling.graphql.api.SelectedField;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class SelectedFieldWrapperTest {

    private static final String FIELD_SIMPLE_NAME = "simpleName";
    private static final String FIELD_SUB_SIMPLE_NAME_1 = "subSimpleName1";
    private static final String FIELD_SUB_SIMPLE_NAME_2 = "subSimpleName2";
    private static final String FIELD_QUALIFIED_NAME = "qualifiedName";
    private static final String FIELD_FULLY_QUALIFIED_NAME = "test.fullyQualifiedName";
    private static final String FIELD_SUB_FULLY_QUALIFIED_NAME_1 = "test.su0bFullyQualifiedName1";
    private static final String FIELD_SUB_FULLY_QUALIFIED_NAME_2 = "test.subFullyQualifiedName2";
    private static final boolean FIELD_CONDITIONAL = false;
    private static final int FIELD_LEVEL = 2;
    private static final String FIELD_ALIAS = "testAlias";
    private static final String FIELD_RESULT_KEY = "testResultKey";
    private static final List<String> FIELD_OBJECT_TYPE_NAMES = Arrays.asList("testObjectTypeName1", "testObjectTypeName2");

    @Test
    public void testFields() {
        graphql.schema.SelectedField source = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SIMPLE_NAME).when(source).getName();
        doReturn(FIELD_QUALIFIED_NAME).when(source).getQualifiedName();
        doReturn(FIELD_FULLY_QUALIFIED_NAME).when(source).getFullyQualifiedName();
        doReturn(FIELD_CONDITIONAL).when(source).isConditional();
        doReturn(FIELD_LEVEL).when(source).getLevel();
        doReturn(FIELD_ALIAS).when(source).getAlias();
        doReturn(FIELD_RESULT_KEY).when(source).getResultKey();
        doReturn(FIELD_OBJECT_TYPE_NAMES).when(source).getObjectTypeNames();

        SelectedFieldWrapper target = new SelectedFieldWrapper(source);

        assertEquals("Wrong Simple Field Name", FIELD_SIMPLE_NAME, target.getName());
        assertEquals("Wrong Qualified Field Name", FIELD_QUALIFIED_NAME, target.getQualifiedName());
        assertEquals("Wrong Fully Qualified Field Name", FIELD_FULLY_QUALIFIED_NAME, target.getFullyQualifiedName());
        assertEquals("Wrong Conditional Flag", FIELD_CONDITIONAL, target.isConditional());
        assertEquals("Wrong Field Level", FIELD_LEVEL, target.getLevel());
        assertEquals("Wrong Field Alias", FIELD_ALIAS, target.getAlias());
        assertEquals("Wrong Field Result Key", FIELD_RESULT_KEY, target.getResultKey());
        assertEquals("Wrong Field Object Type Names", FIELD_OBJECT_TYPE_NAMES, target.getObjectTypeNames());
        assertTrue("Sub Fields must be empty", target.getSubSelectedFields().isEmpty());
    }

    @Test
    public void testSimpleSubFields() {
        graphql.schema.SelectedField sourceParent = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SIMPLE_NAME).when(sourceParent).getName();
        doReturn(FIELD_SIMPLE_NAME).when(sourceParent).getQualifiedName();
        doReturn(FIELD_FULLY_QUALIFIED_NAME).when(sourceParent).getFullyQualifiedName();
        graphql.schema.SelectedField sourceSub1 = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub1).getName();
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub1).getQualifiedName();
        doReturn(FIELD_SUB_FULLY_QUALIFIED_NAME_1).when(sourceSub1).getFullyQualifiedName();
        graphql.schema.SelectedField sourceSub2 = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SUB_SIMPLE_NAME_2).when(sourceSub2).getName();
        doReturn(FIELD_SUB_SIMPLE_NAME_2).when(sourceSub2).getQualifiedName();
        doReturn(FIELD_SUB_FULLY_QUALIFIED_NAME_2).when(sourceSub2).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(selectionSet).when(sourceParent).getSelectionSet();
        List<graphql.schema.SelectedField> immediateFields = Arrays.asList(sourceSub1, sourceSub2);
        doReturn(immediateFields).when(selectionSet).getImmediateFields();

        SelectedFieldWrapper targetParent = new SelectedFieldWrapper(sourceParent);
        assertFalse("No Duplicate Fields expected for Field 1", targetParent.hasDuplicateFieldByName(FIELD_SUB_SIMPLE_NAME_1));
        assertFalse("No Duplicate Fields expected for Field 2", targetParent.hasDuplicateFieldByName(FIELD_SUB_SIMPLE_NAME_1));
        SelectedField foundField1 = targetParent.getFirstSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_1);
        assertNotNull("First Field not found", foundField1);
        assertEquals("Wrong First Field by Simple Name", FIELD_SUB_SIMPLE_NAME_1, foundField1.getName());
        SelectedField foundFQNField1 = targetParent.getSubSelectedFieldByFQN(FIELD_SUB_FULLY_QUALIFIED_NAME_1);
        assertNotNull("First Field (by FQN) not found", foundFQNField1);
        assertEquals("Wrong First Field by FQN Name", FIELD_SUB_SIMPLE_NAME_1, foundFQNField1.getName());
        SelectedField foundField2 = targetParent.getFirstSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_2);
        assertNotNull("Second Field not found", foundField2);
        assertEquals("Wrong Second Field by Simple Name", FIELD_SUB_SIMPLE_NAME_2, foundField2.getName());
        SelectedField foundFQNField2 = targetParent.getSubSelectedFieldByFQN(FIELD_SUB_FULLY_QUALIFIED_NAME_2);
        assertNotNull("Second Field (by FQN) not found", foundFQNField2);
        assertEquals("Wrong Second Field by FQN Name", FIELD_SUB_SIMPLE_NAME_2, foundFQNField2.getName());
    }

    @Test
    public void testDuplicateSubFields() {
        graphql.schema.SelectedField sourceParent = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SIMPLE_NAME).when(sourceParent).getName();
        doReturn(FIELD_SIMPLE_NAME).when(sourceParent).getQualifiedName();
        doReturn(FIELD_FULLY_QUALIFIED_NAME).when(sourceParent).getFullyQualifiedName();
        graphql.schema.SelectedField sourceSub1 = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub1).getName();
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub1).getQualifiedName();
        doReturn(FIELD_SUB_FULLY_QUALIFIED_NAME_1).when(sourceSub1).getFullyQualifiedName();
        graphql.schema.SelectedField sourceSub2 = mock(graphql.schema.SelectedField.class);
        // Use the same Simple Field Name to have a duplicate
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub2).getName();
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub2).getQualifiedName();
        doReturn(FIELD_SUB_FULLY_QUALIFIED_NAME_2).when(sourceSub2).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(selectionSet).when(sourceParent).getSelectionSet();
        List<graphql.schema.SelectedField> immediateFields = Arrays.asList(sourceSub1, sourceSub2);
        doReturn(immediateFields).when(selectionSet).getImmediateFields();

        SelectedFieldWrapper targetParent = new SelectedFieldWrapper(sourceParent);
        assertTrue("Duplicate Fields expected for Field 1", targetParent.hasDuplicateFieldByName(FIELD_SUB_SIMPLE_NAME_1));
        assertFalse("No Duplicate Fields expected for Field 2", targetParent.hasDuplicateFieldByName(FIELD_SUB_SIMPLE_NAME_2));
        assertTrue("First Field not found by Simple Name", targetParent.hasSubSelectedFieldsByName(FIELD_SUB_SIMPLE_NAME_1));
        assertFalse("Second Field unexpectedly found by Simple Name", targetParent.hasSubSelectedFieldsByName(FIELD_SUB_SIMPLE_NAME_2));
        Collection<SelectedField> foundFields1 = targetParent.getSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_1);
        assertNotNull("First Fields not found", foundFields1);
        assertEquals("Expected 2 First Fields", 2, foundFields1.size());
        Collection<SelectedField> foundFields2 = targetParent.getSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_2);
        assertNotNull("Second Fields not found", foundFields2);
        assertEquals("Expected No Second Fields", 0, foundFields2.size());
        assertTrue("First Field not found by FQN", targetParent.hasSubSelectedFieldsByFQN(FIELD_SUB_FULLY_QUALIFIED_NAME_1));
        assertTrue("Second Field not found by FQN", targetParent.hasSubSelectedFieldsByFQN(FIELD_SUB_FULLY_QUALIFIED_NAME_2));
    }

    @Test
    public void testDeprecation() {
        graphql.schema.SelectedField sourceParent = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SIMPLE_NAME).when(sourceParent).getName();
        doReturn(FIELD_SIMPLE_NAME).when(sourceParent).getQualifiedName();
        doReturn(FIELD_FULLY_QUALIFIED_NAME).when(sourceParent).getFullyQualifiedName();
        graphql.schema.SelectedField sourceSub1 = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub1).getName();
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub1).getQualifiedName();
        doReturn(FIELD_SUB_FULLY_QUALIFIED_NAME_1).when(sourceSub1).getFullyQualifiedName();
        graphql.schema.SelectedField sourceSub2 = mock(graphql.schema.SelectedField.class);
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub2).getName();
        doReturn(FIELD_SUB_SIMPLE_NAME_1).when(sourceSub2).getQualifiedName();
        doReturn(FIELD_SUB_FULLY_QUALIFIED_NAME_2).when(sourceSub2).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selectionSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(selectionSet).when(sourceParent).getSelectionSet();
        List<graphql.schema.SelectedField> immediateFields = Arrays.asList(sourceSub1, sourceSub2);
        doReturn(immediateFields).when(selectionSet).getImmediateFields();

        SelectedFieldWrapper targetParent = new SelectedFieldWrapper(sourceParent);

        assertTrue("First Field not found by Simple Name", targetParent.hasSubSelectedFields(FIELD_SUB_SIMPLE_NAME_1));
        assertTrue("First Field not found by FQN", targetParent.hasSubSelectedFields(FIELD_SUB_FULLY_QUALIFIED_NAME_1));
        assertFalse("Second Field found but not expected", targetParent.hasSubSelectedFields(FIELD_SUB_SIMPLE_NAME_2));
        assertTrue("Second Field not found by FQN", targetParent.hasSubSelectedFields(FIELD_SUB_FULLY_QUALIFIED_NAME_2));

        assertNotNull("First Field not found by Simple Name", targetParent.getSubSelectedField(FIELD_SUB_SIMPLE_NAME_1));
        assertNotNull("First Field not found by FQN", targetParent.getSubSelectedField(FIELD_SUB_FULLY_QUALIFIED_NAME_1));
        assertNull("Second Field unexpectedly found by FQN", targetParent.getSubSelectedField(FIELD_SUB_SIMPLE_NAME_2));
        assertNotNull("Second Field not found by FQN", targetParent.getSubSelectedField(FIELD_SUB_FULLY_QUALIFIED_NAME_2));
    }

    /**
     * Tests the mergeSubFields behavior (SITES-42449): when graphql-java returns
     * two immediate children with the same FQN (e.g. two "items" entries from aliased
     * inline fragments), their sub-fields must be merged rather than the first being
     * overwritten by the second.
     *
     * Simulates: { parentList { items { ... on ModelA { fieldA } ... on ModelB { fieldB } } } }
     * where graphql-java produces two "items" entries with FQN "Parent.items", each
     * carrying different sub-fields (ModelA.fieldA vs ModelB.fieldB).
     */
    @Test
    public void testMergeSubFieldsForDuplicateFQN() {
        // FQN shared by both "items" entries
        String itemsFqn = "Parent.items";
        String itemsName = "items";

        // Sub-fields from the first inline fragment (ModelA)
        String subFqnA = "ModelA.fieldA";
        String subNameA = "fieldA";
        graphql.schema.SelectedField sourceSubA = mock(graphql.schema.SelectedField.class);
        doReturn(subNameA).when(sourceSubA).getName();
        doReturn(subNameA).when(sourceSubA).getQualifiedName();
        doReturn(subFqnA).when(sourceSubA).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selSetItems1 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(sourceSubA)).when(selSetItems1).getImmediateFields();

        graphql.schema.SelectedField sourceItems1 = mock(graphql.schema.SelectedField.class);
        doReturn(itemsName).when(sourceItems1).getName();
        doReturn(itemsName).when(sourceItems1).getQualifiedName();
        doReturn(itemsFqn).when(sourceItems1).getFullyQualifiedName();
        doReturn(selSetItems1).when(sourceItems1).getSelectionSet();

        // Sub-fields from the second inline fragment (ModelB)
        String subFqnB = "ModelB.fieldB";
        String subNameB = "fieldB";
        graphql.schema.SelectedField sourceSubB = mock(graphql.schema.SelectedField.class);
        doReturn(subNameB).when(sourceSubB).getName();
        doReturn(subNameB).when(sourceSubB).getQualifiedName();
        doReturn(subFqnB).when(sourceSubB).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selSetItems2 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(sourceSubB)).when(selSetItems2).getImmediateFields();

        graphql.schema.SelectedField sourceItems2 = mock(graphql.schema.SelectedField.class);
        doReturn(itemsName).when(sourceItems2).getName();
        doReturn(itemsName).when(sourceItems2).getQualifiedName();
        doReturn(itemsFqn).when(sourceItems2).getFullyQualifiedName();
        doReturn(selSetItems2).when(sourceItems2).getSelectionSet();

        // Parent field with both "items" entries as immediate children
        graphql.schema.SelectedField sourceParent = mock(graphql.schema.SelectedField.class);
        doReturn("parent").when(sourceParent).getName();
        doReturn("parent").when(sourceParent).getQualifiedName();
        doReturn("Query.parent").when(sourceParent).getFullyQualifiedName();

        DataFetchingFieldSelectionSet parentSelSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(sourceItems1, sourceItems2)).when(parentSelSet).getImmediateFields();
        doReturn(parentSelSet).when(sourceParent).getSelectionSet();

        SelectedFieldWrapper parent = new SelectedFieldWrapper(sourceParent);

        // The parent should have exactly one "items" child (merged, not duplicated)
        SelectedField itemsField = parent.getSubSelectedFieldByFQN(itemsFqn);
        assertNotNull("Items field not found by FQN", itemsField);

        // The merged "items" field should contain sub-fields from BOTH inline fragments
        assertTrue("Sub-field from first inline fragment (ModelA.fieldA) missing",
                itemsField.hasSubSelectedFieldsByFQN(subFqnA));
        assertTrue("Sub-field from second inline fragment (ModelB.fieldB) missing",
                itemsField.hasSubSelectedFieldsByFQN(subFqnB));

        // Verify by direct lookup
        SelectedField foundA = itemsField.getSubSelectedFieldByFQN(subFqnA);
        assertNotNull("fieldA not found by FQN", foundA);
        assertEquals("Wrong name for fieldA", subNameA, foundA.getName());

        SelectedField foundB = itemsField.getSubSelectedFieldByFQN(subFqnB);
        assertNotNull("fieldB not found by FQN", foundB);
        assertEquals("Wrong name for fieldB", subNameB, foundB.getName());

        // Total sub-fields count should be 2 (one from each inline fragment)
        assertEquals("Expected 2 merged sub-fields", 2, itemsField.getSubSelectedFields().size());
    }
}
