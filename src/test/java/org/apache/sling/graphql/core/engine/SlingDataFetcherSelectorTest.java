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
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;

import com.example.fetchers.DoNothingFetcher;

import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class SlingDataFetcherSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingDataFetcherSelector selector;

    @Before
    public void setup() {
        final ScriptedDataFetcherProvider sdfp = Mockito.mock(ScriptedDataFetcherProvider.class);
        context.bundleContext().registerService(ScriptedDataFetcherProvider.class, sdfp, null);
        context.registerInjectActivateService(new SlingDataFetcherSelector());
        selector = context.getService(SlingDataFetcherSelector.class);

        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/digest", new DigestDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/shouldFail", new DoNothingFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "example/ok", new DoNothingFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", new EchoDataFetcher(451));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", new DigestDataFetcher());
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
    public void reservedNameError() throws Exception {
        try {
            selector.getSlingFetcher("sling/shouldFail");
            fail("Expected getSlingFetcher to fail");
        } catch(Exception e) {
            TestUtil.assertNestedException(e, IOException.class, DoNothingFetcher.class.getName());
            TestUtil.assertNestedException(e, IOException.class, "starting with 'sling/' are reserved for Apache Sling");
        }
    }

    @Test(expected=IOException.class)
    public void duplicateFetcherError() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("sling/duplicate");
    }
}