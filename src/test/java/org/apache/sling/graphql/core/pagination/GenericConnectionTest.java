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
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.apache.sling.graphql.api.pagination.Connection;
import org.apache.sling.graphql.api.pagination.Cursor;
import org.apache.sling.graphql.api.pagination.Edge;
import org.apache.sling.graphql.core.helpers.pagination.GenericConnection;
import org.junit.Test;

public class GenericConnectionTest {
    private static final int HIGH_LIMIT = 99;
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
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(2)
            .build();
        assertValues(c, 1, 2, false, true);
    }

    @Test
    public void zeroLimit() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(0)
            .build();
        assertValues(c, -1, -1, false, true);
    }

    @Test
    public void largeLimit() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(HIGH_LIMIT)
            .build();
        assertValues(c, 1, 5, false, false);
    }

    @Test
    public void forcePreviousPage() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(HIGH_LIMIT)
            .withPreviousPage(true)
            .build();
        assertValues(c, 1, 5, true, false);
    }

    @Test
    public void forceNextPage() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(HIGH_LIMIT)
            .withNextPage(true)
            .build();
        assertValues(c, 1, 5, false, true);
    }

    @Test
    public void startAtThree() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(2)
            .withStartAfter(new Cursor(cursorize.apply(2)))
            .build();
        assertValues(c, 3, 4, true, true);
    }

    @Test
    public void startAtFourLargeLimit() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(HIGH_LIMIT)
            .withStartAfter(new Cursor(cursorize.apply(3)))
            .build();
        assertValues(c, 4, 5, true, false);
    }

    @Test
    public void justTwo() {
        final Connection<Integer> c = new GenericConnection.Builder<>(data.iterator(), cursorize)
            .withLimit(1)
            .withStartAfter(new Cursor(cursorize.apply(1)))
            .build();
        assertValues(c, 2, 2, true, true);
    }

    @Test
    public void startCursorNotFound() {
        try {
            new GenericConnection.Builder<>(data.iterator(), cursorize)
                .withLimit(2)
                .withStartAfter(new Cursor(cursorize.apply(HIGH_LIMIT)))
                .build();
            fail("Expecting a RuntimeException");
        } catch(RuntimeException rex) {
            assertTrue(rex.getMessage().contains("Start cursor not found"));
        }
    }

    private static void assertSupplierException(Supplier<?> s) {
        try {
            s.get();
            fail("Expected an Exception");
        } catch(IllegalArgumentException iarg) {
            // as expcted
        }
    }

    @Test
    public void testNullValues() {
        assertSupplierException(() -> new GenericConnection.Builder<Integer>(data.iterator(), null));
        assertSupplierException(() -> new GenericConnection.Builder<Integer>(null, cursorize));
    }

    @Test
    public void testMaxLimit() {
        final GenericConnection.Builder<Integer> b = new GenericConnection.Builder<Integer>(data.iterator(), cursorize);
        b.withLimit(-100);
        b.withLimit(0);
        b.withLimit(42);
        b.withLimit(100);
        try {
            b.withLimit(101);
            fail("Expecting an exception, over limit");
        } catch(IllegalArgumentException iex) {
            assertTrue(iex.getMessage().contains("aximum"));
        }
    }
}