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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.core.mocks.DroidDTO;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A union or interface whose {@code @resolver} names a service that is not registered must report an
 * error. It must never resolve the concrete type by guessing, because that turns a missing service
 * into a silently incomplete response.
 *
 * <p>The schema names its union members after the mock DTO classes on purpose, so a resolution
 * strategy based on the runtime class simple name would succeed here and this test would catch it.
 */
public class MissingTypeResolverTest extends ResourceQueryTestBase {

    @Override
    protected String getTestSchemaName() {
        return "missing-type-resolver-schema";
    }

    @Override
    protected void setupAdditionalServices() {
        final List<Object> characters = new ArrayList<>();
        characters.add(new HumanDTO("human-1", "Luke", "Tatooine"));
        characters.add(new DroidDTO("droid-1", "R2-D2", "whistle"));
        final Dictionary<String, Object> data = new Hashtable<>();
        data.put("characters", characters);
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "character/fetcher", new EchoDataFetcher(data));
    }

    @Test
    public void missingResolverIsReportedAndNeverGuessed() {
        final QueryExecutor queryExecutor = context.getService(QueryExecutor.class);
        assertNotNull(queryExecutor);

        // Inspect the raw specification rather than serializing to JSON: an errors-only result can
        // carry null data entries, which the JSON-P object builder rejects.
        final Map<String, Object> result = queryExecutor.execute(
                "{ unionQuery { characters { ... on HumanDTO { name } } } }",
                Collections.emptyMap(),
                resource,
                new String[] {});

        final Object errors = result.get("errors");
        assertTrue(
                "A missing type resolver must be reported, got " + result,
                errors instanceof List && !((List<?>) errors).isEmpty());
        assertTrue(
                "A missing type resolver must not be resolved by guessing, got " + result,
                !String.valueOf(result.get("data")).contains("Luke"));
    }
}
