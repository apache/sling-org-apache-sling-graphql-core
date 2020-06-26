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

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.junit.Test;

public class CustomScalarsTest extends ResourceQueryTestBase {
    @Test
    public void TODOdoesNotTestScalarsSoFar() throws Exception {
        final String json = queryJSON("{ currentResource { path resourceType } }");
        assertThat(json, hasJsonPath("$.data.currentResource"));
        assertThat(json, hasJsonPath("$.data.currentResource.path", equalTo(resource.getPath())));
        assertThat(json, hasJsonPath("$.data.currentResource.resourceType", equalTo(resource.getResourceType())));
    }

}