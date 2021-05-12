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

 package org.apache.sling.graphql.api.pagination.helpers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.apache.sling.graphql.api.pagination.Connection;
import org.apache.sling.graphql.api.pagination.Cursor;
import org.apache.sling.graphql.api.pagination.Edge;
import org.apache.sling.graphql.api.pagination.PageInfo;
import org.osgi.annotation.versioning.ConsumerType;

/** As per https://relay.dev/graphql/connections.htm a "connection"
 *  is a page of results for a paginated query.
*/
@ConsumerType
public class GenericConnection<T> implements Connection<T>, PageInfo {

    public static final int DEFAULT_LIMIT = 10;

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

    /** Build a Connection that will output the supplied data, optionally skipping items
     *  at the beginning and considering a set maximum of items.
     * 
     *  @param dataIterator the connection's data - must include the item that startAfter points to
     *  @param cursorStringProvider extracts a String from an object of type T to create a Cursor
     *  @param startAfter if not null, data up to and including the item which has this cursor is ignored
     *  @param maxItemsReturned at most this many items are considered
    */
    private GenericConnection(Iterator<T> dataIterator, Function<T, String> cursorStringProvider) {
        edges = new ArrayList<>();
        this.dataIterator = dataIterator;
        this.cursorStringProvider = cursorStringProvider;
    }

    private void initialize() {
        if(initialized) {
            throw new IllegalStateException("Already initialized");
        }
        initialized = true;

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

        if(!inRange && limit > 0) {
            throw new RuntimeException("Start cursor not found in supplied data:" + startAfter);
        }
        if(hasPreviousPage == null) {
            hasPreviousPage = false;
        }
        if(hasNextPage == null) {
            hasNextPage = dataIterator.hasNext();
        }
    }

    private Edge<T> newEdge(final T node, final Function<T, String> cursorStringProvider) {
        return new Edge<T>() {
            @Override
            public T getNode() {
                return node;
            }

            @Override
            public Cursor getCursor() {
                return new Cursor(cursorStringProvider.apply(node));
            }
        };
    }

    @Override
    public Iterable<Edge<T>> getEdges() {
        return edges::iterator;
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

    public static class Builder<T> {
        private final GenericConnection<T> connection;

        public Builder(Iterator<T> dataIterator, Function<T, String> cursorStringProvider) {
            connection = new GenericConnection<>(dataIterator, cursorStringProvider);
        }

        public Builder<T> withLimit(int limit) {
            connection.limit = limit;
            return this;
        }

        public Builder<T> withStartAfter(Cursor c) {
            connection.startAfter = c;
            return this;
        }

        /** Force the "has previous page" value, in case the supplied
         *  data doesn't expose that but a new query would find it
         */
        public Builder<T> withPreviousPage(boolean b) {
            connection.hasPreviousPage = b;
            return this;
        }

        /** Force the "has next page" value, in case the supplied
         *  data doesn't expose that but a new query would find it
         */
        public Builder<T> withNextPage(boolean b) {
            connection.hasNextPage = b;
            return this;
        }

        public Connection<T> build() {
            connection.initialize();
            return connection;
        }
    }
}
