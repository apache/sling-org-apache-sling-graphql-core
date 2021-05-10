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

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.apache.sling.graphql.core.mocks.TestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.graphql.core.mocks.HumanDTO;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.junit.Test;

import graphql.relay.Connection;
import graphql.relay.ConnectionCursor;
import graphql.relay.Edge;
import graphql.relay.PageInfo;

public class PaginationTest extends ResourceQueryTestBase {

    static class ConnectionDataFetcher implements SlingDataFetcher<Connection<HumanDTO>> {
        private List<HumanDTO> humans = new ArrayList<>();

        ConnectionDataFetcher(List<HumanDTO> humans) {
            this.humans = humans;
        }

        @Override
        public @Nullable Connection<HumanDTO> get(@NotNull SlingDataFetcherEnvironment e) throws Exception {
            final String cursor = e.getArgument("after", null);
            final int limit = e.getArgument("limit", 2);
            return new DataConnection(humans, cursor, limit);
        }
    }

    static class Cursor implements ConnectionCursor {
        private final String value;
        Cursor(HumanDTO dto) {
            value = dto.getId();
        }
        Cursor(String c) {
            value = c;
        }
        @Override
        public String getValue() {
            return value == null ? "" : value;
        }
        @Override
        public String toString() {
            return getValue();
        }
        public boolean isEmpty() {
            return value == null || value.length() == 0;
        }
    }

    static class DataConnection implements Connection<HumanDTO> {

        private final Cursor startCursor;
        private Cursor endCursor;
        private boolean hasPreviousPage;
        private boolean hasNextPage;
        private final int limit; 
        private final List<Edge<HumanDTO>> edges = new ArrayList();
        private final PageInfo pageInfo;

        DataConnection(List<HumanDTO> humans, String cursor, int limit) {
            this.startCursor = new Cursor(cursor);
            this.limit = limit;

            // skip to cursor and add the following humanDTOs as edges
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
                        public ConnectionCursor getCursor() {
                            return new Cursor(dto);
                        }

                    });
                    endCursor = new Cursor(dto);
                } else if(startCursor.isEmpty()) {
                    inRange = true;
                    hasPreviousPage = false;
                } else if(startCursor.toString().equals(dto.getId())) {
                    inRange = true;
                    hasPreviousPage = true;
                }
            }

            // setup page info
            pageInfo = new PageInfo() {
                @Override
                public ConnectionCursor getStartCursor() {
                    return startCursor;
                }

                @Override
                public ConnectionCursor getEndCursor() {
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

            };
        }

        @Override
        public List<Edge<HumanDTO>> getEdges() {
            return edges;
        }

        @Override
        public PageInfo getPageInfo() {
            return pageInfo;
        }
    }

    @Override
    protected void setupAdditionalServices() {
        final List<HumanDTO> humans = new ArrayList<>();

        for(int i=0 ; i < 100 ; i++) {
            humans.add(new HumanDTO("human-" + i, "Luke-" + i, "Tatooine"));
        }
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "character/resolver", new CharacterTypeResolver());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "humans/paginated", new ConnectionDataFetcher(humans));
    }

    private void assertPageInfo(String json, String startCursor, String endCursor, Boolean hasPreviousPage, Boolean hasNextPage) {
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.startCursor", equalTo(startCursor)));
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.endCursor", equalTo(endCursor)));
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.hasPreviousPage", equalTo(hasPreviousPage)));
        assertThat(json, hasJsonPath("$.data.paginatedQuery.pageInfo.hasNextPage", equalTo(hasNextPage)));
    }

    private void assertEdges(String json, int startIndex, int endIndex) {
        int dataIndex = 0;
        for(int i=startIndex; i <= endIndex; i++) {
            final String id = "human-" + i;
            final String name = "Luke-" + i;
            assertThat(json, hasJsonPath("$.data.paginatedQuery.edges[" + dataIndex + "].cursor", equalTo(id)));
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
        assertPageInfo(json, "", "human-2", false, true );
        assertEdges(json, 1, 2);
    }

    @Test
    public void startCursorAndLimit() throws Exception {
        final String json = queryJSON("{ paginatedQuery(after:\"human-5\", limit:6) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertPageInfo(json, "human-5", "human-11", true, true);
        assertEdges(json, 6, 11);
    }

    @Test
    public void startCursorNearEnd() throws Exception {
        final String json = queryJSON("{ paginatedQuery(after:\"human-94\", limit:60) {"
            + " pageInfo { startCursor endCursor hasPreviousPage hasNextPage }"
            + " edges { cursor node { id name }}"
            +"}}");
        assertPageInfo(json, "human-94", "human-99", true, false);
        assertEdges(json, 95, 99);
    }
}
