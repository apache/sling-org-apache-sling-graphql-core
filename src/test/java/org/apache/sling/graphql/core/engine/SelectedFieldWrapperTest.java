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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class SelectedFieldWrapperTest {

    private static final String FIELD_SIMPLE_NAME = "simpleName";
    private static final String FIELD_SUB_SIMPLE_NAME_1 = "subSimpleName1";
    private static final String FIELD_SUB_SIMPLE_NAME_2 = "subSimpleName2";
    private static final String FIELD_QUALIFIED_NAME = "qualifiedName";
    private static final String FIELD_FULLY_QUALIFIED_NAME = "test/fullyQualifiedName";
    private static final String FIELD_SUB_FULLY_QUALIFIED_NAME_1 = "test/subFullyQualifiedName1";
    private static final String FIELD_SUB_FULLY_QUALIFIED_NAME_2 = "test/subFullyQualifiedName2";
    private static final String FIELD_SUB_FULLY_QUALIFIED_NAME_3 = "test/subFullyQualifiedName3";
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
        assertEquals("Wrong First Field by Simple Name", foundField1.getName(), FIELD_SUB_SIMPLE_NAME_1);
        SelectedField foundFQNField1 = targetParent.getSubSelectedFieldByFQN(FIELD_SUB_FULLY_QUALIFIED_NAME_1);
        assertNotNull("First Field (by FQN) not found", foundFQNField1);
        assertEquals("Wrong First Field by FQN Name", foundFQNField1.getName(), FIELD_SUB_SIMPLE_NAME_1);
        SelectedField foundField2 = targetParent.getFirstSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_2);
        assertNotNull("Second Field not found", foundField2);
        assertEquals("Wrong Second Field by Simple Name", foundField2.getName(), FIELD_SUB_SIMPLE_NAME_2);
        SelectedField foundFQNField2 = targetParent.getSubSelectedFieldByFQN(FIELD_SUB_FULLY_QUALIFIED_NAME_2);
        assertNotNull("Second Field (by FQN) not found", foundFQNField2);
        assertEquals("Wrong Second Field by FQN Name", foundFQNField2.getName(), FIELD_SUB_SIMPLE_NAME_2);
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
        Collection<SelectedField> foundFields1 = targetParent.getSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_1);
        assertNotNull("First Fields not found", foundFields1);
        assertEquals("Expected 2 First Fields", foundFields1.size(), 2);
        Collection<SelectedField> foundFields2 = targetParent.getSubSelectedFieldByName(FIELD_SUB_SIMPLE_NAME_2);
        assertNotNull("Second Fields not found", foundFields2);
        assertEquals("Expected No Second Fields", foundFields2.size(), 0);
    }
}
