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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import org.apache.sling.graphql.api.pagination.Connection;
import org.apache.sling.graphql.api.pagination.Cursor;
import org.apache.sling.graphql.api.pagination.Edge;
import org.apache.sling.graphql.api.pagination.helpers.GenericConnection;
import org.junit.Test;

public class GenericConnectionTest {
    private static final List<Integer> data = Arrays.asList(1,2,3,4,5 );
    private static final Function<Integer, String> cursorize = (i) -> "cursor-" + i;

    private static void assertValues(Connection<Integer> data, int start, int end, boolean hasPreviousPage, boolean hasNextPage)  {
        // assert edge values
        final AtomicInteger current = new AtomicInteger(start);
        StreamSupport.stream(data.getEdges().spliterator(), false).map(edge -> edge.getNode()).forEach(actual -> {
            assertEquals(Integer.valueOf(current.get()), actual);
            if(actual > end) {
                fail("Got a value after expected end: " + actual);
            }
            current.incrementAndGet();
        });

        // cursors and previous/next page
        final Iterator<Edge<Integer>> it = data.getEdges().iterator();
        if(it.hasNext()) {
            final Cursor startCursor = new Cursor(cursorize.apply(it.next().getNode()));
            assertEquals("Expecting start cursor " + startCursor, startCursor, data.getPageInfo().getStartCursor());
            final Cursor endCursor = new Cursor(cursorize.apply(end));
            assertEquals("Expecting end cursor " + endCursor, endCursor, data.getPageInfo().getEndCursor());
        } else {
            // Empty data stream
            assertEquals(null, data.getPageInfo().getStartCursor());
            assertEquals(null, data.getPageInfo().getEndCursor());
        }
        assertEquals("Expecting hasNextPage=" + hasNextPage, hasNextPage, data.getPageInfo().isHasNextPage());
        assertEquals("Expecting hasPreviousPage=" + hasPreviousPage, hasPreviousPage, data.getPageInfo().isHasPreviousPage());
    }

    @Test
    public void minimalArguments() {
        final GenericConnection<Integer> c = new GenericConnection<>(data.iterator(), cursorize, null, 2);
        assertValues(c, 1, 2, false, true);
    }

    @Test
    public void zeroLimit() {
        final GenericConnection<Integer> c = new GenericConnection<>(data.iterator(), cursorize, null, 0);
        assertValues(c, -1, -1, false, true);
    }

    @Test
    public void largeLimit() {
        final GenericConnection<Integer> c = new GenericConnection<>(data.iterator(), cursorize, null, 999);
        assertValues(c, 1, 5, false, false);
    }

    @Test
    public void startAtThree() {
        final Cursor startAfter = new Cursor(cursorize.apply(2));
        final GenericConnection<Integer> c = new GenericConnection<>(data.iterator(), cursorize, startAfter, 2);
        assertValues(c, 3, 4, true, true);
    }

    @Test
    public void startCursorNotFound() {
        final Cursor startAfter = new Cursor(cursorize.apply(999));
        try {
            new GenericConnection<>(data.iterator(), cursorize, startAfter, 2);
            fail("Expecting a RuntimeException");
        } catch(RuntimeException rex) {
            assertTrue(rex.getMessage().contains("Start cursor not found"));
        }
    }
}