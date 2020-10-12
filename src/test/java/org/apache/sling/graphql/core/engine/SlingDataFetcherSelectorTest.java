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

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import com.example.fetchers.DoNothingFetcher;

import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class SlingDataFetcherSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingDataFetcherSelector selector;

    @Before
    public void setup() {
        context.registerInjectActivateService(new SlingDataFetcherSelector());
        selector = context.getService(SlingDataFetcherSelector.class);

        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/digest", new DigestDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/shouldFail", new DoNothingFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "example/ok", new DoNothingFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", 0, new DigestDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", 10, new EchoDataFetcher(451));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", 5, new EchoDataFetcher(452));
    }

    @Test
    public void acceptableName() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("example/ok");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameOk() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("sling/digest");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameError() {
        assertNull(selector.getSlingFetcher("sling/shouldFail"));
    }

    @Test
    public void sameNameFetcher() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("sling/duplicate");
        assertNotNull(sdf);
        assertEquals(EchoDataFetcher.class, sdf.getClass());
        assertEquals(451, sdf.get(mock(SlingDataFetcherEnvironment.class)));
    }
}
