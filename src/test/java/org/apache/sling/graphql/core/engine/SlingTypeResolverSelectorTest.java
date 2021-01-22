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

import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.api.SlingTypeResolverEnvironment;
import org.apache.sling.graphql.core.mocks.DummyTypeResolver;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.example.resolvers.DoNothingTypeResolver;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class SlingTypeResolverSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingTypeResolverSelector selector;

    @Before
    public void setup() {
        context.registerInjectActivateService(new SlingTypeResolverSelector());
        selector = context.getService(SlingTypeResolverSelector.class);

        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/character", new CharacterTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/shouldFail", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "example/ok", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/duplicate", 1, new DummyTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/duplicate", 0, new CharacterTypeResolver());
    }

    @Test
    public void acceptableName() {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("example/ok");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameOk() {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("sling/character");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameError() {
        assertNull(selector.getSlingTypeResolver("sling/shouldFail"));
    }

    @Test
    public void sameNameTypeResolver() {
        final SlingTypeResolver<Object> str = selector.getSlingTypeResolver("sling/duplicate");
        assertNotNull(str);
        assertEquals(DummyTypeResolver.class, str.getClass());
        assertNull(str.getType(mock(SlingTypeResolverEnvironment.class)));
    }
}
