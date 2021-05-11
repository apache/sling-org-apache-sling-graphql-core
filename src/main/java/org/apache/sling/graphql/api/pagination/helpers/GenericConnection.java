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
public class GenericConnection<T> implements Connection<T> {

    private final List<Edge<T>> edges;

    static class LocalPageInfo implements PageInfo {
        Cursor startCursor = null;
        Cursor endCursor = null;
        boolean hasPreviousPage;
        boolean hasNextPage;

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
    };

    private final LocalPageInfo pageInfo = new LocalPageInfo();

    private Edge<T> newEdge(final T node, final Function<T, String> cursorStringFunction) {
        return new Edge<T>() {
            @Override
            public T getNode() {
                return node;
            }

            @Override
            public Cursor getCursor() {
                return new Cursor(cursorStringFunction.apply(node));
            }
        };
    }

    /** Build a Connection that will output the supplied data, optionally skipping items
     *  at the beginning and considering a set maximum of items.
     * 
     *  @param dataIterator the connection's data - must include the item that startAfter points to
     *  @param cursorStringFunction extracts a String from an object of type T to create a Cursor
     *  @param startAfter if not null, data up to and including the item which has this cursor is ignored
     *  @param maxItemsReturned at most this many items are considered
    */
    public GenericConnection(Iterator<T> dataIterator, Function<T, String> cursorStringFunction, final Cursor startAfter, int maxItemsReturned) {

        // Need to visit the stream first to setup the PageInfo, which graphql-java
        // apparently uses before visiting all the edges
        edges = new ArrayList<>();
        boolean inRange = false;
        int itemsToAdd = maxItemsReturned;
        while(itemsToAdd > 0 && dataIterator.hasNext()) {
            final T node = dataIterator.next();
            boolean addThisNode = false;
            if(!inRange) {
                if(startAfter == null) {
                    inRange = true;
                    addThisNode = true;
                    pageInfo.hasPreviousPage = false;
                } else {
                    final String rawCursor = cursorStringFunction.apply(node);
                    inRange = startAfter.getRawValue().equals(rawCursor);
                    pageInfo.hasPreviousPage = true;
                }
            } else {
                addThisNode = true;
            }

            if(addThisNode) {
                final Edge<T> toAdd = newEdge(node, cursorStringFunction);
                if(pageInfo.startCursor == null) {
                    pageInfo.startCursor = toAdd.getCursor();
                }
                pageInfo.endCursor = toAdd.getCursor();
                edges.add(toAdd);
                itemsToAdd--;
            }
        }

        if(!inRange) {
            throw new RuntimeException("Start cursor not found in supplied data:" + startAfter);
        }
        pageInfo.hasNextPage = dataIterator.hasNext();
    }

    public Iterable<Edge<T>> getEdges() {
        return edges::iterator;
    }

    public PageInfo getPageInfo() {
        return pageInfo;
    }
}
