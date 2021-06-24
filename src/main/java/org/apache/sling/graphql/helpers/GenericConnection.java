/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/

package org.apache.sling.graphql.helpers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.pagination.Connection;
import org.apache.sling.graphql.api.pagination.Cursor;
import org.apache.sling.graphql.api.pagination.Edge;
import org.apache.sling.graphql.api.pagination.PageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ConsumerType;

/** As per https://relay.dev/graphql/connections.htm a "connection"
 *  is a page of results for a paginated query.
 *
 *  Use the {@link Builder} class to build a Connection that outputs
 *  the supplied data, optionally sliced based on a "start after" cursor
 *  and a limit on the number of items output.
 */
@ConsumerType
public final class GenericConnection<T> implements Connection<T>, PageInfo {

    public static final int DEFAULT_LIMIT = 10;

    /** We might make this configurable but for now let's stay on the safe side */
    public static final int MAX_LIMIT = 100;

    private final List<Edge<T>> edges;
    private final Iterator<T> dataIterator;
    private final Function<T, String> cursorStringProvider;
    private boolean initialized;
    private Cursor startAfter = null;
    private Cursor startCursor = null;
    private Cursor endCursor = null;
    private Boolean hasPreviousPage;
    private Boolean hasNextPage;
    private int limit = DEFAULT_LIMIT;

    /** The Builder must be used to construct this */
    private GenericConnection(@NotNull Iterator<T> dataIterator, @NotNull Function<T, String> cursorStringProvider) {
        checkNotNull(dataIterator, "Data iterator");
        checkNotNull(cursorStringProvider, "Cursor string provider function");

        edges = new ArrayList<>();
        this.dataIterator = dataIterator;
        this.cursorStringProvider = cursorStringProvider;
    }

    private void initialize() {
        if(initialized) {
            throw new IllegalStateException("Already initialized");
        }
        initialized = true;
        final boolean anyData = dataIterator.hasNext();

        // Need to visit the stream first to setup the PageInfo, which graphql-java
        // apparently uses before visiting all the edges
        boolean inRange = false;
        int itemsToAdd = limit;
        while(itemsToAdd > 0 && dataIterator.hasNext()) {
            final T node = dataIterator.next();
            boolean addThisNode = false;
            if(!inRange) {
                if(startAfter == null) {
                    inRange = true;
                    addThisNode = true;
                    if(hasPreviousPage == null) {
                        hasPreviousPage = false;
                    }
                } else {
                    final String rawCursor = cursorStringProvider.apply(node);
                    inRange = startAfter.getRawValue().equals(rawCursor);
                    if(hasPreviousPage == null) {
                        hasPreviousPage = true;
                    }
                }
            } else {
                addThisNode = true;
            }

            if(addThisNode) {
                final Edge<T> toAdd = newEdge(node, cursorStringProvider);
                if(startCursor == null) {
                    startCursor = toAdd.getCursor();
                }
                endCursor = toAdd.getCursor();
                edges.add(toAdd);
                itemsToAdd--;
            }
        }

        if(anyData && !inRange && limit > 0) {
            throw new SlingGraphQLException("Start cursor not found in supplied data:" + startAfter);
        }
        if(hasPreviousPage == null) {
            hasPreviousPage = false;
        }
        if(hasNextPage == null) {
            hasNextPage = dataIterator.hasNext();
        }
    }

    private static void checkNotNull(Object o, String whatIsThat) {
        if(o == null) {
            throw new IllegalArgumentException(whatIsThat + " is null");
        }
    }

    private Edge<T> newEdge(final T node, final Function<T, String> cursorStringProvider) {
        return new Edge<T>() {
            @Override
            public @NotNull T getNode() {
                return node;
            }

            @Override
            public @NotNull Cursor getCursor() {
                return new Cursor(cursorStringProvider.apply(node));
            }
        };
    }

    @Override
    public @NotNull Iterable<Edge<T>> getEdges() {
        return edges;
    }

    @Override
    public @NotNull PageInfo getPageInfo() {
        return this;
    }

    @Override
    public @NotNull Cursor getStartCursor() {
        return startCursor;
    }

    @Override
    public @NotNull Cursor getEndCursor() {
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

    public static class Builder<T> {
        private final GenericConnection<T> connection;

        /** Builder for a Connection that will output the supplied data, optionally skipping items
         *  at the beginning and considering a set maximum of items.
         *
         *  @param dataIterator the connection's data - must include the item that startAfter points to if that
         *      Cursor is set, but can contain less items that set by the "limit" parameter.
         *  @param cursorStringProvider extracts a String from an object of type T to create a Cursor
         */
        public Builder(@NotNull Iterator<T> dataIterator, @NotNull Function<T, String> cursorStringProvider) {
            connection = new GenericConnection<>(dataIterator, cursorStringProvider);
        }

        /**
         * Set a limit on the number of items returned by the connection.
         *
         * @param limit must be &lt;= MAX_LIMIT
         * @return this builder
         */
        public Builder<T> withLimit(int limit) {
            if(limit < 0) {
                limit = 0;
            }
            if(limit > MAX_LIMIT) {
                throw new IllegalArgumentException("Invalid limit " + limit + ", the maximum value is " + MAX_LIMIT);
            }
            connection.limit = limit;
            return this;
        }

        /**
         * If set, the connection will skip to the first item after the {@code c} {@link Cursor}.
         *
         * @param c the cursor for {@code startAfter}
         * @return this builder
         */
        public Builder<T> withStartAfter(@Nullable Cursor c) {
            connection.startAfter = c;
            return this;
        }

        /**
         * Force the "has previous page" value, in case the supplied data doesn't expose that but a new query would find it.
         *
         * @param b a {@code boolean} that can force the {@code hasPreviousPage}
         * @return this builder
         */
        public Builder<T> withPreviousPage(boolean b) {
            connection.hasPreviousPage = b;
            return this;
        }

        /**
         * Force the "has next page" value, in case the supplied data doesn't expose that but a new query would find it
         *
         * @param b a {@code boolean} that can force the {@code hasNextPage}
         * @return this builder
         */
        public Builder<T> withNextPage(boolean b) {
            connection.hasNextPage = b;
            return this;
        }

        /**
         * Build the Connection - can only be called once.
         *
         * @return a {@link Connection}
         */
        public Connection<T> build() {
            connection.initialize();
            return connection;
        }
    }
}
