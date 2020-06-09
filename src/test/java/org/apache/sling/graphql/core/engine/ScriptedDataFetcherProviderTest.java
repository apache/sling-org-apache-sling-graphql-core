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

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;
import java.util.UUID;

import javax.servlet.Servlet;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.ServletResolver;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.core.mocks.MockScriptServlet;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class ScriptedDataFetcherProviderTest {

    private ScriptedDataFetcherProvider provider;
    private static final String FETCHER_NAME = "this/fetcher";

    private ServletResolver servletResolver;
    private Servlet servletOnly;
    private Servlet servletAndScript;

    @Rule
    public final OsgiContext context = new OsgiContext();

    @Before
    public void setup() {
        servletOnly = Mockito.mock(Servlet.class);
        servletAndScript = new MockScriptServlet();
        servletResolver = Mockito.mock(ServletResolver.class);

        context.bundleContext().registerService(ServletResolver.class, servletResolver, null);
        context.registerInjectActivateService(new ScriptedDataFetcherProvider());
        provider = context.getService(ScriptedDataFetcherProvider.class);
    }

    private void setReturnedServlet(Servlet s) {
        Mockito.when(servletResolver.resolveServlet(Mockito.any(Resource.class), Mockito.any(String.class))).thenReturn(s);
    }

    private void assertServletResolverCall() {
        ArgumentCaptor<Resource> resourceArgument = ArgumentCaptor.forClass(Resource.class);
        ArgumentCaptor<String> scriptNameArgument = ArgumentCaptor.forClass(String.class);
        Mockito.verify(servletResolver).resolveServlet(resourceArgument.capture(), scriptNameArgument.capture());
        assertThat(resourceArgument.getValue().getResourceType(), equalTo("graphql/fetchers/" + FETCHER_NAME));
        assertThat(scriptNameArgument.getValue(), equalTo("fetcher"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFetcherProviderFound() throws Exception {
        setReturnedServlet(servletAndScript);
        final SlingDataFetcher<Object> f = provider.getDataFetcher(FETCHER_NAME);
        assertServletResolverCall();
        assertThat("Expecting a SlingDataFetcher", f, not(nullValue()));
        final SlingDataFetcherEnvironment env = Mockito.mock(SlingDataFetcherEnvironment.class);
        final Map<String, Object> result = (Map<String, Object>)f.get(env);
        assertThat(result.get("resourcePath"), nullValue());
    }

    @Test
    public void testServletNotFound() throws Exception {
        setReturnedServlet(null);
        final SlingDataFetcher<Object> f = provider.getDataFetcher(FETCHER_NAME);
        assertServletResolverCall();
        assertThat(f, nullValue());
    }

    @Test
    public void testServletFoundButNotAScript() throws Exception {
        setReturnedServlet(servletOnly);
        final SlingDataFetcher<Object> f = provider.getDataFetcher(FETCHER_NAME);
        assertServletResolverCall();
        assertThat(f, nullValue());
    }
}