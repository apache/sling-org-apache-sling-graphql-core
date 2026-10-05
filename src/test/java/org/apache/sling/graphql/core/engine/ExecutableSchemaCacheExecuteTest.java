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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end execute() coverage with the executable schema cache enabled,
 * verifying GraphQLContext Resource isolation across distinct request resources.
 */
public class ExecutableSchemaCacheExecuteTest extends ResourceQueryTestBase {

    @Override
    protected Map<String, Object> getQueryExecutorProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("executableSchemaCacheEnabled", true);
        props.put("schemaCacheSize", 32);
        return props;
    }

    @Override
    protected void setupAdditionalServices() {
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "echoNS/echo", new EchoDataFetcher(null));
    }

    @Test
    public void cachedSchemaUsesPerRequestResource() {
        final QueryExecutor queryExecutor = context.getService(QueryExecutor.class);
        assertNotNull(queryExecutor);

        Resource resourceA = mockResource("/content/a-" + UUID.randomUUID(), "type/a");
        Resource resourceB = mockResource("/content/b-" + UUID.randomUUID(), "type/b");

        final String query = "{ currentResource { path resourceType } }";
        Map<String, Object> resultA =
                queryExecutor.execute(query, java.util.Collections.emptyMap(), resourceA, new String[] {});
        Map<String, Object> resultB =
                queryExecutor.execute(query, java.util.Collections.emptyMap(), resourceB, new String[] {});

        String jsonA = jakarta.json.Json.createObjectBuilder(resultA).build().toString();
        String jsonB = jakarta.json.Json.createObjectBuilder(resultB).build().toString();

        assertThat(jsonA, hasJsonPath("$.data.currentResource.path", equalTo(resourceA.getPath())));
        assertThat(jsonA, hasJsonPath("$.data.currentResource.resourceType", equalTo(resourceA.getResourceType())));
        assertThat(jsonB, hasJsonPath("$.data.currentResource.path", equalTo(resourceB.getPath())));
        assertThat(jsonB, hasJsonPath("$.data.currentResource.resourceType", equalTo(resourceB.getResourceType())));
        assertThat(resourceA.getPath(), not(equalTo(resourceB.getPath())));
    }

    private static Resource mockResource(String path, String resourceType) {
        Resource r = mock(Resource.class);
        when(r.getPath()).thenReturn(path);
        when(r.getResourceType()).thenReturn(resourceType);
        return r;
    }
}
