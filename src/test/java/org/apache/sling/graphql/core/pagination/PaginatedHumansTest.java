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

import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.api.SlingTypeResolverEnvironment;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.sling.graphql.core.engine.ResourceQueryTestBase;
import org.apache.sling.graphql.helpers.GenericConnection;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.api.pagination.Connection;
import org.apache.sling.graphql.api.pagination.Cursor;
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
            return new GenericConnection.Builder<>(humans.iterator(), HumanDTO::getId)
                .withStartAfter(afterCursor)
                .withLimit(limit)
                .build();
        }
    }

    static class BeingResolver implements SlingTypeResolver<Object> {
        @Override
        public @Nullable Object getType(@NotNull SlingTypeResolverEnvironment e) {
            if (e.getObject() instanceof HumanDTO) {
                return e.getObjectType("Human");
            }
            return null;
        }
    }

    @Override
    protected void setupAdditionalServices() {
        final List<HumanDTO> humans = new ArrayList<>();
        for(int i=1 ; i < 90 ; i++) {
            humans.add(new HumanDTO("human-" + i, "Luke-" + i, "Tatooine"));
        }
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "humans/connection", new HumansPageFetcher(humans));
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "being/resolver", new BeingResolver());
    }

    private void assertPageInfo(String json, String type, Cursor startCursor, Cursor endCursor, Boolean hasPreviousPage,
                                Boolean hasNextPage) {
        assertThat(json, hasJsonPath("$.data." + type + ".pageInfo.startCursor", equalTo(startCursor == null ? null : startCursor.toString())));
        assertThat(json, hasJsonPath("$.data." + type + ".pageInfo.endCursor", equalTo(endCursor == null ? null: endCursor.toString())));
        assertThat(json, hasJsonPath("$.data." + type + ".pageInfo.hasPreviousPage", equalTo(hasPreviousPage)));
        assertThat(json, hasJsonPath("$.data." + type + ".pageInfo.hasNextPage", equalTo(hasNextPage)));
    }

    private void assertEdges(String json, String type, int startIndex, int endIndex) {
        int dataIndex = 0;
        for(int i=startIndex; i <= endIndex; i++) {
            final String id = "human-" + i;
            final Cursor c = new Cursor(id);
            final String name = "Luke-" + i;
            assertThat(json, hasJsonPath("$.data." + type + ".edges[" + dataIndex + "].node.id", equalTo(id)));
            assertThat(json, hasJsonPath("$.data." + type + ".edges[" + dataIndex + "].node.name", equalTo(name)));
            assertThat(json, hasJsonPath("$.data." + type + ".edges[" + dataIndex + "].cursor", equalTo(c.toString())));
            dataIndex++;
        }
        final int count = endIndex - startIndex + 1;
        assertThat(json, hasJsonPath("$.data." + type + ".edges.length()", equalTo(count)));
    }

    @Test
    public void noArguments() throws Exception {
        final String paginatedHumansJson = queryJSON("{ paginatedHumans {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertEdges(paginatedHumansJson, "paginatedHumans", 1, 2);
        assertPageInfo(paginatedHumansJson, "paginatedHumans", new Cursor("human-1"), new Cursor("human-2"), false, true );

        final String paginatedBeingsJson = queryJSON("{ paginatedBeings {"
                + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
                + " edges { cursor node { id name }}"
                +"}}");
        assertEdges(paginatedBeingsJson, "paginatedBeings", 1, 2);
        assertPageInfo(paginatedBeingsJson, "paginatedBeings", new Cursor("human-1"), new Cursor("human-2"), false, true );
    }

    @Test
    public void startCursorAndLimit() throws Exception {
        final Cursor start = new Cursor("human-5");
        final String paginatedHumansJson = queryJSON("{ paginatedHumans(after:\"" + start + "\", limit:6) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertEdges(paginatedHumansJson, "paginatedHumans", 6, 11);
        assertPageInfo(paginatedHumansJson, "paginatedHumans", new Cursor("human-6"), new Cursor("human-11"), true, true);

        final String paginatedBeingsJson = queryJSON("{ paginatedBeings(after:\"" + start + "\", limit:6) {"
                + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
                + " edges { cursor node { id name }}"
                +"}}");
        assertEdges(paginatedBeingsJson, "paginatedBeings", 6, 11);
        assertPageInfo(paginatedBeingsJson, "paginatedBeings", new Cursor("human-6"), new Cursor("human-11"), true, true);
    }

    @Test
    public void startCursorNearEnd() throws Exception {
        final Cursor start = new Cursor("human-84");
        final String paginatedHumansJson = queryJSON("{ paginatedHumans(after:\"" + start + "\", limit:60) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertEdges(paginatedHumansJson, "paginatedHumans", 85, 89);
        assertPageInfo(paginatedHumansJson, "paginatedHumans", new Cursor("human-85"), new Cursor("human-89"), true, false);

        final String paginatedBeingsJson = queryJSON("{ paginatedBeings(after:\"" + start + "\", limit:60) {"
                + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
                + " edges { cursor node { id name }}"
                +"}}");
        assertEdges(paginatedBeingsJson, "paginatedBeings", 85, 89);
        assertPageInfo(paginatedBeingsJson, "paginatedBeings", new Cursor("human-85"), new Cursor("human-89"), true, false);
    }

    @Test
    public void zeroLimit() throws Exception {
        final Cursor start = new Cursor("human-94");
        final String paginatedHumansJson = queryJSON("{ paginatedHumans(after:\"" + start + "\", limit:0) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertThat(paginatedHumansJson, hasJsonPath("$.data.paginatedHumans.edges.length()", equalTo(0)));
        assertPageInfo(paginatedHumansJson, "paginatedHumans", null, null, false, true);

        final String paginatedBeingsJson = queryJSON("{ paginatedBeings(after:\"" + start + "\", limit:0) {"
                + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
                + " edges { cursor node { id name }}"
                +"}}");
        assertThat(paginatedBeingsJson, hasJsonPath("$.data.paginatedBeings.edges.length()", equalTo(0)));
        assertPageInfo(paginatedBeingsJson, "paginatedBeings",null, null, false, true);
    }

    @Test
    public void afterCursorNotFound() throws Exception {
        final Cursor notInDataSet = new Cursor("This is not a key from our data set");
        final String paginatedHumansJson = queryJSON("{ paginatedHumans(after:\"" + notInDataSet + "\", limit:60) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertThat(paginatedHumansJson, hasJsonPath("errors"));
        assertTrue(paginatedHumansJson.contains("Start cursor not found"));

        final String paginatedBeingsJson = queryJSON("{ paginatedBeings(after:\"" + notInDataSet + "\", limit:60) {"
                + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
                + " edges { cursor node { id name }}"
                +"}}");
        assertThat(paginatedBeingsJson, hasJsonPath("errors"));
        assertTrue(paginatedBeingsJson.contains("Start cursor not found"));


    }
}
