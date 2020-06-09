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
package org.apache.sling.graphql.core.it;

import javax.inject.Inject;
import javax.script.ScriptEngineFactory;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;

import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.core.mocks.ReplacingSchemaProvider;
import org.apache.sling.resource.presence.ResourcePresence;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.factoryConfiguration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ServerSideQueryIT extends GraphQLCoreTestSupport {

    @Inject
    @Filter(value = "(names=graphql)")
    protected ScriptEngineFactory scriptEngineFactory;

    @Inject
    @Filter(value = "(path=/apps/graphql/test/one/json.gql)")
    private ResourcePresence resourcePresence;

    @Inject
    private BundleContext bundleContext;

    @Inject
    private SchemaProvider defaultSchemaProvider;

    @Configuration
    public Option[] configuration() {
        return new Option[]{
            baseConfiguration(),
            pipeDataFetcher(),
            factoryConfiguration("org.apache.sling.resource.presence.internal.ResourcePresenter")
                .put("path", "/apps/graphql/test/one/json.gql")
                .asOption(),
        };
    }

    private void assertDefaultContent(String selector, String fieldName) throws Exception {
        final String path = "/graphql/one";
        final String json = getContent(path + selector + ".json");
        assertThat(json, hasJsonPath("$.data." + fieldName));
        assertThat(json, hasJsonPath("$.data." + fieldName + ".path", equalTo("/content/graphql/one")));
        assertThat(json, hasJsonPath("$.data." + fieldName + ".resourceType", equalTo("graphql/test/one")));
    }

    @Test
    public void testJsonContent() throws Exception {
        assertDefaultContent("", "scriptedSchemaResource");
    }

    @Test
    public void testMultipleSchemaProviders() throws Exception {
        new ReplacingSchemaProvider("scriptedSchemaResource", "REPLACED").register(bundleContext, defaultSchemaProvider, 1);
        new ReplacingSchemaProvider("scriptedSchemaResource", "NOT_THIS_ONE").register(bundleContext, defaultSchemaProvider, Integer.MAX_VALUE);
        assertDefaultContent(".REPLACED", "REPLACED");
    }

    @Test
    public void testScriptedDataFetcher() throws Exception {
        final String json = getContent("/graphql/one.scripted.json");
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo("graphql/test/one")));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.boolValue", equalTo(true)));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.resourcePath", equalTo("From the test script: /content/graphql/one")));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.testingArgument", equalTo("1,2,42")));
        assertThat(json, hasJsonPath("$.data.scriptedFetcher.anotherValue", equalTo(451)));
    }
}
