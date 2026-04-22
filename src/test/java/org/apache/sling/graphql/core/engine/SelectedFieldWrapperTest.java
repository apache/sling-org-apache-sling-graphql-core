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
     * Tests merge when two immediate children share the same FQN but carry
     * different sub-fields (e.g. two aliased selections of the same field with
     * different inline fragments). The merged wrapper must contain sub-fields
     * from both entries, and hasDuplicateFieldByName must still report the
     * duplicate.
     */
    @Test
    public void testMergeSubFieldsForDuplicateFQN() {
        String itemsFqn = "Parent.items";
        String itemsName = "items";

        // First "items" entry carries sub-field fieldA
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

        // Second "items" entry carries sub-field fieldB
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

        // Parent with both items entries
        graphql.schema.SelectedField sourceParent = mock(graphql.schema.SelectedField.class);
        doReturn("parent").when(sourceParent).getName();
        doReturn("parent").when(sourceParent).getQualifiedName();
        doReturn("Query.parent").when(sourceParent).getFullyQualifiedName();

        DataFetchingFieldSelectionSet parentSelSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(sourceItems1, sourceItems2)).when(parentSelSet).getImmediateFields();
        doReturn(parentSelSet).when(sourceParent).getSelectionSet();

        SelectedFieldWrapper parent = new SelectedFieldWrapper(sourceParent);

        // hasDuplicateFieldByName must still detect both entries
        assertTrue("items should be reported as duplicate by name",
                parent.hasDuplicateFieldByName(itemsName));

        // Merged items must contain sub-fields from BOTH entries
        SelectedField itemsField = parent.getSubSelectedFieldByFQN(itemsFqn);
        assertNotNull("Items field not found by FQN", itemsField);
        assertTrue("fieldA missing", itemsField.hasSubSelectedFieldsByFQN(subFqnA));
        assertTrue("fieldB missing", itemsField.hasSubSelectedFieldsByFQN(subFqnB));
        assertEquals("Expected 2 merged sub-fields", 2, itemsField.getSubSelectedFields().size());
    }

    /**
     * Tests recursive merge: two aliased selections of the same field where
     * both contain a child with the same FQN (e.g. policyCategories) but with
     * different deeper sub-fields. The merge must recurse so that the deeper
     * sub-fields from both branches are visible.
     *
     * Simulates: policyTabList: policy { policyCategories { id, name } }
     *            policyTabDetailList: policy { policyCategories { id, name, policies { ... } } }
     */
    @Test
    public void testRecursiveMergeForSharedChildFQN() {
        String policyFqn = "PoliciesModel.policy";
        String policyName = "policy";
        String catFqn = "PolicyModel.policyCategories";
        String catName = "policyCategories";

        // --- Shared leaf fields under policyCategories ---
        graphql.schema.SelectedField idField = mock(graphql.schema.SelectedField.class);
        doReturn("policyCategoryId").when(idField).getName();
        doReturn("policyCategoryId").when(idField).getQualifiedName();
        doReturn("CategoryModel.policyCategoryId").when(idField).getFullyQualifiedName();

        graphql.schema.SelectedField nameField = mock(graphql.schema.SelectedField.class);
        doReturn("policyCategoryName").when(nameField).getName();
        doReturn("policyCategoryName").when(nameField).getQualifiedName();
        doReturn("CategoryModel.policyCategoryName").when(nameField).getFullyQualifiedName();

        // --- Extra deep field only in second alias ---
        graphql.schema.SelectedField policiesField = mock(graphql.schema.SelectedField.class);
        doReturn("policies").when(policiesField).getName();
        doReturn("policies").when(policiesField).getQualifiedName();
        doReturn("CategoryModel.policies").when(policiesField).getFullyQualifiedName();

        // First policyCategories (shallow: id + name)
        DataFetchingFieldSelectionSet catSelSet1 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(idField, nameField)).when(catSelSet1).getImmediateFields();
        graphql.schema.SelectedField cat1 = mock(graphql.schema.SelectedField.class);
        doReturn(catName).when(cat1).getName();
        doReturn(catName).when(cat1).getQualifiedName();
        doReturn(catFqn).when(cat1).getFullyQualifiedName();
        doReturn(catSelSet1).when(cat1).getSelectionSet();

        // Second policyCategories (deep: id + name + policies)
        DataFetchingFieldSelectionSet catSelSet2 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(idField, nameField, policiesField)).when(catSelSet2).getImmediateFields();
        graphql.schema.SelectedField cat2 = mock(graphql.schema.SelectedField.class);
        doReturn(catName).when(cat2).getName();
        doReturn(catName).when(cat2).getQualifiedName();
        doReturn(catFqn).when(cat2).getFullyQualifiedName();
        doReturn(catSelSet2).when(cat2).getSelectionSet();

        // First policy entry (alias policyTabList)
        DataFetchingFieldSelectionSet policySelSet1 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(cat1)).when(policySelSet1).getImmediateFields();
        graphql.schema.SelectedField policy1 = mock(graphql.schema.SelectedField.class);
        doReturn(policyName).when(policy1).getName();
        doReturn(policyName).when(policy1).getQualifiedName();
        doReturn(policyFqn).when(policy1).getFullyQualifiedName();
        doReturn(policySelSet1).when(policy1).getSelectionSet();

        // Second policy entry (alias policyTabDetailList)
        DataFetchingFieldSelectionSet policySelSet2 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(cat2)).when(policySelSet2).getImmediateFields();
        graphql.schema.SelectedField policy2 = mock(graphql.schema.SelectedField.class);
        doReturn(policyName).when(policy2).getName();
        doReturn(policyName).when(policy2).getQualifiedName();
        doReturn(policyFqn).when(policy2).getFullyQualifiedName();
        doReturn(policySelSet2).when(policy2).getSelectionSet();

        // Parent item containing both policy selections
        graphql.schema.SelectedField parentField = mock(graphql.schema.SelectedField.class);
        doReturn("item").when(parentField).getName();
        doReturn("item").when(parentField).getQualifiedName();
        doReturn("Query.item").when(parentField).getFullyQualifiedName();
        DataFetchingFieldSelectionSet parentSelSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(policy1, policy2)).when(parentSelSet).getImmediateFields();
        doReturn(parentSelSet).when(parentField).getSelectionSet();

        SelectedFieldWrapper parent = new SelectedFieldWrapper(parentField);

        // policy should be flagged as duplicate
        assertTrue("policy should be duplicate by name",
                parent.hasDuplicateFieldByName(policyName));

        // Merged policy → policyCategories must contain all 3 sub-fields
        SelectedField policyField = parent.getSubSelectedFieldByFQN(policyFqn);
        assertNotNull("policy not found by FQN", policyField);

        SelectedField catField = policyField.getSubSelectedFieldByFQN(catFqn);
        assertNotNull("policyCategories not found", catField);

        assertTrue("policyCategoryId missing",
                catField.hasSubSelectedFieldsByFQN("CategoryModel.policyCategoryId"));
        assertTrue("policyCategoryName missing",
                catField.hasSubSelectedFieldsByFQN("CategoryModel.policyCategoryName"));
        assertTrue("policies missing (recursive merge failed)",
                catField.hasSubSelectedFieldsByFQN("CategoryModel.policies"));
        assertEquals("Expected 3 sub-fields after recursive merge",
                3, catField.getSubSelectedFields().size());
    }

    /**
     * Tests that mergeSubFields silently skips sub-fields whose FQN is null.
     * Such entries must not be added to the merged wrapper.
     */
    @Test
    public void testMergeSubFieldsSkipsNullFqn() {
        String itemsFqn = "Parent.items";
        String itemsName = "items";

        // First "items" entry with a real sub-field
        graphql.schema.SelectedField realSub = mock(graphql.schema.SelectedField.class);
        doReturn("realField").when(realSub).getName();
        doReturn("realField").when(realSub).getQualifiedName();
        doReturn("Model.realField").when(realSub).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selSet1 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(realSub)).when(selSet1).getImmediateFields();

        graphql.schema.SelectedField items1 = mock(graphql.schema.SelectedField.class);
        doReturn(itemsName).when(items1).getName();
        doReturn(itemsName).when(items1).getQualifiedName();
        doReturn(itemsFqn).when(items1).getFullyQualifiedName();
        doReturn(selSet1).when(items1).getSelectionSet();

        // Second "items" entry with a sub-field whose FQN is null
        graphql.schema.SelectedField nullFqnSub = mock(graphql.schema.SelectedField.class);
        doReturn("ghostField").when(nullFqnSub).getName();
        doReturn("ghostField").when(nullFqnSub).getQualifiedName();
        doReturn(null).when(nullFqnSub).getFullyQualifiedName();

        DataFetchingFieldSelectionSet selSet2 = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(nullFqnSub)).when(selSet2).getImmediateFields();

        graphql.schema.SelectedField items2 = mock(graphql.schema.SelectedField.class);
        doReturn(itemsName).when(items2).getName();
        doReturn(itemsName).when(items2).getQualifiedName();
        doReturn(itemsFqn).when(items2).getFullyQualifiedName();
        doReturn(selSet2).when(items2).getSelectionSet();

        // Parent with both items entries
        graphql.schema.SelectedField sourceParent = mock(graphql.schema.SelectedField.class);
        doReturn("parent").when(sourceParent).getName();
        doReturn("parent").when(sourceParent).getQualifiedName();
        doReturn("Query.parent").when(sourceParent).getFullyQualifiedName();
        DataFetchingFieldSelectionSet parentSelSet = mock(DataFetchingFieldSelectionSet.class);
        doReturn(Arrays.asList(items1, items2)).when(parentSelSet).getImmediateFields();
        doReturn(parentSelSet).when(sourceParent).getSelectionSet();

        SelectedFieldWrapper parent = new SelectedFieldWrapper(sourceParent);

        SelectedField itemsField = parent.getSubSelectedFieldByFQN(itemsFqn);
        assertNotNull("items not found by FQN", itemsField);

        // realField must be present
        assertTrue("realField missing after merge", itemsField.hasSubSelectedFieldsByFQN("Model.realField"));
        // null-FQN ghost must be silently skipped — only 1 sub-field in merged wrapper
        assertEquals("Null-FQN sub-field must be skipped during merge", 1, itemsField.getSubSelectedFields().size());
    }
}
