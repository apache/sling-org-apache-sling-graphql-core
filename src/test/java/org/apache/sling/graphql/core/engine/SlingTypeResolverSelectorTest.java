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

import com.example.fetchers.DoNothingTypeResolver;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.mocks.UnionTypeResolver;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class SlingTypeResolverSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingTypeResolverSelector selector;

    @Before
    public void setup() {
        final ScriptedTypeResolverProvider sdfp = Mockito.mock(ScriptedTypeResolverProvider.class);
        context.bundleContext().registerService(ScriptedTypeResolverProvider.class, sdfp, null);
        context.registerInjectActivateService(new SlingTypeResolverSelector());
        selector = context.getService(SlingTypeResolverSelector.class);

        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/union", new UnionTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/shouldFail", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "example/ok", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/duplicate", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/duplicate", new DoNothingTypeResolver());
    }

    @Test
    public void acceptableName() throws Exception {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("example/ok");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameOk() throws Exception {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("sling/union");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameError() throws Exception {
        try {
            selector.getSlingTypeResolver("sling/shouldFail");
            fail("Expected getSlingTypeResolver to fail");
        } catch (Exception e) {
            TestUtil.assertNestedException(e, IOException.class, DoNothingTypeResolver.class.getName());
            TestUtil.assertNestedException(e, IOException.class,
                    "starting with 'sling/' are reserved for Apache Sling");
        }
    }

    @Test(expected = IOException.class)
    public void duplicateTypeResolverError() throws Exception {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("sling/duplicate");
    }
}