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
package org.apache.sling.graphql.core.pagination;

import java.util.ArrayList;
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.sling.graphql.core.mocks.TestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.sling.graphql.core.engine.ResourceQueryTestBase;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.api.pagination.Connection;
import org.apache.sling.graphql.api.pagination.Cursor;
import org.apache.sling.graphql.api.pagination.helpers.GenericConnection;
import org.junit.Test;

public class PaginatedHumansTest extends ResourceQueryTestBase {

    protected String getTestSchemaName() {
        return "paginated-humans-schema";
    }

    static class HumansPageFetcher implements SlingDataFetcher<Connection<HumanDTO>> {
        private List<HumanDTO> humans = new ArrayList<>();

        HumansPageFetcher(List<HumanDTO> humans) {
            this.humans = humans;
        }

        @Override
        public @Nullable Connection<HumanDTO> get(@NotNull SlingDataFetcherEnvironment e) throws Exception {
            final Cursor afterCursor = Cursor.fromEncodedString(e.getArgument("after"));
            final int limit = e.getArgument("limit", 2);
            return new GenericConnection<>(humans.iterator(), HumanDTO::getId, afterCursor, limit);
        }
    }

    @Override
    protected void setupAdditionalServices() {
        final List<HumanDTO> humans = new ArrayList<>();
        for(int i=1 ; i < 100 ; i++) {
            humans.add(new HumanDTO("human-" + i, "Luke-" + i, "Tatooine"));
        }
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "humans/connection", new HumansPageFetcher(humans));
    }

    private void assertPageInfo(String json, Cursor startCursor, Cursor endCursor, Boolean hasPreviousPage, Boolean hasNextPage) {
        assertThat(json, hasJsonPath("$.data.paginatedHumans.pageInfo.startCursor", equalTo(startCursor == null ? null : startCursor.toString())));
        assertThat(json, hasJsonPath("$.data.paginatedHumans.pageInfo.endCursor", equalTo(endCursor == null ? null: endCursor.toString())));
        assertThat(json, hasJsonPath("$.data.paginatedHumans.pageInfo.hasPreviousPage", equalTo(hasPreviousPage)));
        assertThat(json, hasJsonPath("$.data.paginatedHumans.pageInfo.hasNextPage", equalTo(hasNextPage)));
    }

    private void assertEdges(String json, int startIndex, int endIndex) {
        int dataIndex = 0;
        for(int i=startIndex; i <= endIndex; i++) {
            final String id = "human-" + i;
            final Cursor c = new Cursor(id);
            final String name = "Luke-" + i;
            assertThat(json, hasJsonPath("$.data.paginatedHumans.edges[" + dataIndex + "].node.id", equalTo(id)));
            assertThat(json, hasJsonPath("$.data.paginatedHumans.edges[" + dataIndex + "].node.name", equalTo(name)));
            assertThat(json, hasJsonPath("$.data.paginatedHumans.edges[" + dataIndex + "].cursor", equalTo(c.toString())));
            dataIndex++;
        }
        final int count = endIndex - startIndex + 1;
        assertThat(json, hasJsonPath("$.data.paginatedHumans.edges.length()", equalTo(count)));
    }

    @Test
    public void noArguments() throws Exception {
        final String json = queryJSON("{ paginatedHumans {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertEdges(json, 1, 2);
        assertPageInfo(json, new Cursor("human-1"), new Cursor("human-2"), false, true );
    }

    @Test
    public void startCursorAndLimit() throws Exception {
        final Cursor start = new Cursor("human-5");
        final String json = queryJSON("{ paginatedHumans(after:\"" + start + "\", limit:6) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertEdges(json, 6, 11);
        assertPageInfo(json, new Cursor("human-6"), new Cursor("human-11"), true, true);
    }

    @Test
    public void startCursorNearEnd() throws Exception {
        final Cursor start = new Cursor("human-94");
        final String json = queryJSON("{ paginatedHumans(after:\"" + start + "\", limit:60) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertEdges(json, 95, 99);
        assertPageInfo(json, new Cursor("human-95"), new Cursor("human-99"), true, false);
    }

    @Test
    public void zeroLimit() throws Exception {
        final Cursor start = new Cursor("human-94");
        final String json = queryJSON("{ paginatedHumans(after:\"" + start + "\", limit:0) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertThat(json, hasJsonPath("$.data.paginatedHumans.edges.length()", equalTo(0)));
        assertPageInfo(json, null, null, false, true);
    }

    @Test
    public void afterCursorNotFound() throws Exception {
        final Cursor notInDataSet = new Cursor("This is not a key from our data set");
        final String json = queryJSON("{ paginatedHumans(after:\"" + notInDataSet + "\", limit:60) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertThat(json, hasJsonPath("errors"));
        assertTrue(json.contains("Start cursor not found"));
    }
}
