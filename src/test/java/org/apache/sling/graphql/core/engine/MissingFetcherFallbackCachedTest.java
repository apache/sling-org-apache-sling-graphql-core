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

import org.junit.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * The property fallback must survive schema reuse: with the executable schema cache enabled the
 * wrapper is wired once and invoked on every later request, so re-run the fallback assertions against
 * a cached schema as well.
 */
public class MissingFetcherFallbackCachedTest extends MissingFetcherFallbackTest {

    @Override
    protected Map<String, Object> getQueryExecutorProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("executableSchemaCacheEnabled", true);
        props.put("executableSchemaCacheSize", 8);
        props.put("schemaCacheSize", 32);
        return props;
    }

    @Test
    public void fallbackIsStableAcrossRequestsSharingACachedSchema() throws Exception {
        final String first = queryJSON("{ currentResource { path resourceType } }");
        final String second = queryJSON("{ currentResource { path resourceType } }");
        assertThat(first, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(second, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(second, hasJsonPath("$.data.currentResource.resourceType", equalTo(resource.getResourceType())));
    }
}
