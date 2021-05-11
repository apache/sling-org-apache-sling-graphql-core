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
import java.util.List;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.apache.sling.graphql.core.mocks.TestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.api.pagination.Cursor;
import org.apache.sling.graphql.api.pagination.Edge;
import org.apache.sling.graphql.api.pagination.PageInfo;
import org.apache.sling.graphql.api.pagination.ResultsPage;
import org.junit.Test;

public class PaginationTest extends ResourceQueryTestBase {

    static class HumansPageFetcher implements SlingDataFetcher<Object> {
        private List<HumanDTO> humans = new ArrayList<>();

        HumansPageFetcher(List<HumanDTO> humans) {
            this.humans = humans;
        }

        @Override
        public @Nullable Object get(@NotNull SlingDataFetcherEnvironment e) throws Exception {
            final String cursor = e.getArgument("after", "");
            final int limit = e.getArgument("limit", 2);
            return new HumansResultPage(humans, cursor, limit);
        }
    }

    // TODO this class should be generalized so that data can be provided as 
    // a data source (Stream?) with "skip after cursor" functionality, and the
    // rest of this class is then generic.
    static class HumansResultPage implements ResultsPage<HumanDTO>,PageInfo {

        private final Cursor startCursor;
        private Cursor endCursor;
        private boolean hasPreviousPage;
        private boolean hasNextPage;
        private final List<Edge<HumanDTO>> edges = new ArrayList<>();

        HumansResultPage(List<HumanDTO> humans, String cursor, int limit) {
            this.startCursor = new Cursor(Cursor.decode(cursor));

            boolean inRange = false;
            int remaining = limit;
            for(HumanDTO dto : humans) {
                if(remaining <= 0) {
                    hasNextPage = true;
                    break;
                } else if(inRange) {
                    if(remaining -- <= 0) {
                        inRange = false;
                    }
                    edges.add(new Edge<HumanDTO>() {
                        @Override
                        public HumanDTO getNode() {
                            return dto;
                        }

                        @Override
                        public Cursor getCursor() {
                            return new Cursor(dto.getId());
                        }

                    });
                    endCursor = new Cursor(dto.getId());
                } else if(startCursor.isEmpty()) {
                    inRange = true;
                    hasPreviousPage = false;
                } else if(startCursor.getRawValue().equals(dto.getId())) {
                    inRange = true;
                    hasPreviousPage = true;
                }
            }
        }

        @Override
        public List<Edge<HumanDTO>> getEdges() {
            return edges;
        }

        @Override
        public PageInfo getPageInfo() {
            return this;
        }

        @Override
        public Cursor getStartCursor() {
            return startCursor;
        }

        @Override
        public Cursor getEndCursor() {
            return endCursor;
        }

        @Override
        public boolean isHasPreviousPage() {
            return hasPreviousPage;
        }

        @Override
        public boolean isHasNextPage() {
            return hasNextPage;
        }
}

    @Override
    protected void setupAdditionalServices() {
        final List<HumanDTO> humans = new ArrayList<>();

        for(int i=0 ; i < 100 ; i++) {
            humans.add(new HumanDTO("human-" + i, "Luke-" + i, "Tatooine"));
        }
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "humans/paginated", new HumansPageFetcher(humans));
    }

    private void assertPageInfo(String json, Cursor startCursor, Cursor endCursor, Boolean hasPreviousPage, Boolean hasNextPage) {
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.startCursor", equalTo(startCursor.toString())));
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.endCursor", equalTo(endCursor.toString())));
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.hasPreviousPage", equalTo(hasPreviousPage)));
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.hasNextPage", equalTo(hasNextPage)));
    }

    private void assertEdges(String json, int startIndex, int endIndex) {
        int dataIndex = 0;
        for(int i=startIndex; i <= endIndex; i++) {
            final String id = "human-" + i;
            final Cursor c = new Cursor(id);
            final String name = "Luke-" + i;
            assertThat(json, hasJsonPath("$.data.paginatedQuery.edges[" + dataIndex + "].cursor", equalTo(c.toString())));
            assertThat(json, hasJsonPath("$.data.paginatedQuery.edges[" + dataIndex + "].node.id", equalTo(id)));
            assertThat(json, hasJsonPath("$.data.paginatedQuery.edges[" + dataIndex + "].node.name", equalTo(name)));
            dataIndex++;
        }
        final int count = endIndex - startIndex + 1;
        assertThat(json, hasJsonPath("$.data.paginatedQuery.edges.length()", equalTo(count)));
    }

    @Test
    public void noArguments() throws Exception {
        final String json = queryJSON("{ paginatedQuery {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertPageInfo(json, new Cursor(null), new Cursor("human-2"), false, true );
        assertEdges(json, 1, 2);
    }

    @Test
    public void startCursorAndLimit() throws Exception {
        final Cursor start = new Cursor("human-5");
        final String json = queryJSON("{ paginatedQuery(after:\"" + start + "\", limit:6) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertPageInfo(json, new Cursor("human-5"), new Cursor("human-11"), true, true);
        assertEdges(json, 6, 11);
    }

    @Test
    public void startCursorNearEnd() throws Exception {
        final Cursor start = new Cursor("human-94");
        final String json = queryJSON("{ paginatedQuery(after:\"" + start + "\", limit:60) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertPageInfo(json, new Cursor("human-94"), new Cursor("human-99"), true, false);
        assertEdges(json, 95, 99);
    }
}
