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

import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.jcr.Value;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CurrentThreadResourceTest {

    private static final String MOCKED_NAME = "MockedName";
    private static final String MOCKED_RT = "MockedRT";
    private static final String MOCKED_SUPER_RT = "MockedSuperRT";
    private static final String MOCKED_PATH = "/a/b/c";
    private static final Boolean MOCKED_HAS_CHILDREN = true;
    private static final boolean MOCKED_IS_RT = true;
    private CurrentThreadResource threadResource;
    private Resource parentResourceMock;
    private Resource mockedChildResource;
    private ResourceMetadata mockedResourceMetadata;
    private ResourceResolver mockedResourceResolver;
    private ValueMap mockedValueMap;
    private Long mockedAdapted;

    @Before
    public void setup() {
        Resource mockedResource = mock(Resource.class);
        mockedChildResource = mock(Resource.class);
        parentResourceMock = mock(Resource.class);
        mockedResourceResolver = mock(ResourceResolver.class);
        mockedResourceMetadata = mock(ResourceMetadata.class);
        mockedValueMap = mock(ValueMap.class);
        mockedAdapted = 1000L;
        when(mockedResource.getName()).thenReturn(MOCKED_NAME);
        when(mockedResource.getPath()).thenReturn(MOCKED_PATH);
        when(mockedResource.getParent()).thenReturn(parentResourceMock);
        when(mockedResource.getResourceType()).thenReturn(MOCKED_RT);
        when(mockedResource.getResourceSuperType()).thenReturn(MOCKED_SUPER_RT);
        when(mockedResource.getChildren()).thenReturn(Collections.singletonList(mockedChildResource));
        when(mockedResource.listChildren()).thenReturn(Collections.singletonList(mockedChildResource).iterator());
        when(mockedResource.hasChildren()).thenReturn(MOCKED_HAS_CHILDREN);
        when(mockedResource.isResourceType(anyString())).thenReturn(MOCKED_IS_RT);
        when(mockedResource.getChild(anyString())).thenReturn(mockedChildResource);
        when(mockedResource.getResourceMetadata()).thenReturn(mockedResourceMetadata);
        when(mockedResource.getResourceResolver()).thenReturn(mockedResourceResolver);
        when(mockedResource.getValueMap()).thenReturn(mockedValueMap);
        when(mockedResource.adaptTo(any(Class.class))).thenReturn(mockedAdapted);
        CurrentThreadResource.setCurrentResource(mockedResource);
        threadResource = new CurrentThreadResource();
    }

    @After
    public void tearDown() {
        CurrentThreadResource.dispose();
        // check on dispose
        if (threadResource != null) {
            boolean didNPE = false;
            try {
                threadResource.getName();
            } catch (NullPointerException e) {
                didNPE = true;
            }
            assertTrue("Expected dispose to have unlinked the resource with the current thread", didNPE);
        }
    }

    @Test
    public void testGetPath() {
        assertEquals(MOCKED_PATH, threadResource.getPath());
    }

    @Test
    public void testGetName() {
        assertEquals(MOCKED_NAME, threadResource.getName());
    }

    @Test
    public void testGetParent() {
        assertSame(parentResourceMock, threadResource.getParent());
    }

    @Test
    public void testListChildren() {
        assertEquals(1, IteratorUtils.size(threadResource.listChildren()));
    }

    @Test
    public void testGetChildren() {
        assertEquals(1, IterableUtils.size(threadResource.getChildren()));
    }

    @Test
    public void testGetChild() {
        assertSame(mockedChildResource, threadResource.getChild("/some/child"));
    }

    @Test
    public void testGetResourceType() {
        assertSame(MOCKED_RT, threadResource.getResourceType());
    }

    @Test
    public void testGetResourceSuperType() {
        assertSame(MOCKED_SUPER_RT, threadResource.getResourceSuperType());
    }

    @Test
    public void testHasChildren() {
        assertEquals(MOCKED_HAS_CHILDREN, threadResource.hasChildren());
    }

    @Test
    public void testIsResourceType() {
        assertEquals(MOCKED_IS_RT, threadResource.isResourceType("ANY_RT"));
    }

    @Test
    public void testGetResourceMetadata() {
        assertSame(mockedResourceMetadata, threadResource.getResourceMetadata());
    }

    @Test
    public void testGetResourceResolver() {
        assertSame(mockedResourceResolver, threadResource.getResourceResolver());
    }

    @Test
    public void testGetValueMap() {
        assertSame(mockedValueMap, threadResource.getValueMap());
    }

    @Test
    public void testAdaptTo() {
        assertSame(mockedAdapted, threadResource.adaptTo(Long.class));
    }
}