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
import static org.hamcrest.Matchers.contains;

import org.junit.Test;

public class IntrospectionQueryTest extends ResourceQueryTestBase {
    
    @Test
    public void schemaIntrospectionTest() throws Exception {
        final String json = queryJSON("{ __schema { types { name } directives { description }}}");
        assertThat(json, hasJsonPath("$.data.__schema"));
        assertThat(json, hasJsonPath("$.data.__schema.types"));
        assertThat(json, hasJsonPath("$.data.__schema.types..name"));
        assertThat(json, hasJsonPath("$.data.__schema.directives"));
        assertThat(json, hasJsonPath("$.data.__schema.directives..description"));
    }

}
