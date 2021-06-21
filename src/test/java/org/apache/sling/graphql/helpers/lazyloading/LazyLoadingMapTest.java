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
package org.apache.sling.graphql.helpers.lazyloading;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.sling.graphql.helpers.layzloading.LazyLoadingMap;
import org.junit.Before;
import org.junit.Test;

public class LazyLoadingMapTest {
    private static final String TEST_STRING = "Fritz Frisst etc. etc.";
    private int counter;
    private Supplier<String> lazyCounter = () -> "X" + String.valueOf(++counter);

    @Before
    public void setup() {
        counter = 0;
    }

    @Test
    public void basicTest() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        assertNull(map.get(42));

        map
            .withSupplier(21, () -> UUID.randomUUID().toString())
            .withSupplier(42, () -> TEST_STRING)
        ;
        assertEquals(0, map.getSuppliersCallCount());
        assertEquals(TEST_STRING, map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
        final String random = map.get(21);
        assertNotNull(random);
        assertEquals(random, map.get(21));
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void addAlsoWorks() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        map.put(42, TEST_STRING);
        assertEquals(TEST_STRING, map.get(42));
        map.withSupplier(42, () -> "should not change");
        assertEquals(TEST_STRING, map.get(42));
        assertEquals(0, map.getSuppliersCallCount());
    }

    @Test
    public void remove() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        map.withSupplier(21, lazyCounter);
        map.put(42, TEST_STRING);
        assertEquals(2, map.size());
        assertEquals(TEST_STRING, map.get(42));
        assertEquals(TEST_STRING, map.remove(42));
        assertNull(map.get(42));
        assertEquals(1, map.size());
        assertEquals(0, map.getSuppliersCallCount());

        // Remove before and after computing
        assertEquals(0, map.getSuppliersCallCount());
        map.withSupplier(112, lazyCounter);
        map.withSupplier(113, lazyCounter);
        assertEquals("X1", map.get(113));
        assertEquals("X1", map.remove(113));
        assertEquals(1, map.getSuppliersCallCount());
        assertEquals("X2", map.remove(112));
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void containsValueComputesEverything() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        assertFalse(map.containsKey(42));
        assertEquals(0, map.getSuppliersCallCount());

        assertFalse(map.containsValue("X1"));
        map.withSupplier(42, lazyCounter);
        assertTrue(map.containsValue("X1"));

        assertFalse(map.containsValue("X2"));
        map.withSupplier(21, lazyCounter);
        assertEquals(1, map.getSuppliersCallCount());
        assertTrue(map.containsValue("X1"));
        assertTrue(map.containsValue("X2"));
        assertEquals(2, map.getSuppliersCallCount());

        assertFalse(map.containsValue(TEST_STRING));
        map.put(71, TEST_STRING);
        map
            .withSupplier(92, lazyCounter)
            .withSupplier(93, lazyCounter)
        ;
        assertTrue(map.containsValue(TEST_STRING));
        assertTrue(map.containsValue("X1"));
        assertTrue(map.containsValue("X2"));
        assertTrue(map.containsValue("X3"));
        assertTrue(map.containsValue("X4"));
        assertFalse(map.containsValue("X5"));

        assertEquals(4, map.getSuppliersCallCount());
    }

    @Test
    public void containsKey() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        assertFalse(map.containsKey(42));
        assertEquals(0, map.getSuppliersCallCount());

        map.withSupplier(42, lazyCounter);
        map.put(21, "nothing");

        assertTrue(map.containsKey(42));
        assertTrue(map.containsKey(21));
        assertFalse(map.containsKey(22));

        assertEquals(0, map.getSuppliersCallCount());
    }

    @Test
    public void keySet() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        assertEquals(0, map.keySet().size());
        map.put(112, "nothing");
        assertEquals(1, map.keySet().size());
        map.withSupplier(21, lazyCounter);
        map.withSupplier(42, lazyCounter);
        map.withSupplier(110, lazyCounter);

        assertEquals(0, map.getSuppliersCallCount());
        map.get(42);
        assertEquals(1, map.getSuppliersCallCount());

        final Set<Integer> ks = map.keySet();
        assertEquals(4, ks.size());
        assertTrue(ks.contains(21));
        assertTrue(ks.contains(42));
        assertTrue(ks.contains(112));
        assertTrue(ks.contains(110));
        assertEquals(1, map.getSuppliersCallCount());
    }

    @Test
    public void entrySet() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        map.put(112, TEST_STRING);
        map.withSupplier(21, lazyCounter);
        map.withSupplier(42, lazyCounter);

        final Set<String> toFind = new HashSet<>();
        toFind.add(TEST_STRING);
        toFind.add("X1");
        toFind.add("X2");

        assertEquals(3, toFind.size());
        map.entrySet().forEach(e -> toFind.remove(e.getValue()));
        assertEquals(0, toFind.size());
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void values() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        map.put(112, TEST_STRING);
        map.withSupplier(21, lazyCounter);
        map.withSupplier(42, lazyCounter);

        final Set<String> toFind = new HashSet<>();
        toFind.add(TEST_STRING);
        toFind.add("X1");
        toFind.add("X2");

        assertEquals(3, toFind.size());
        assertEquals(0, map.getSuppliersCallCount());
        map.values().forEach(v -> toFind.remove(v));
        assertEquals(0, toFind.size());
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void isEmpty() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        assertTrue(map.isEmpty());

        map.put(112, TEST_STRING);
        assertFalse(map.isEmpty());
        map.withSupplier(42, lazyCounter);
        assertFalse(map.isEmpty());

        map.remove(112);
        assertFalse(map.isEmpty());
        map.remove(42);
        assertTrue(map.isEmpty());

        assertEquals(1, map.getSuppliersCallCount());
    }

    @Test
    public void nullSupplier() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        final Supplier<String> nullSup = () -> null;

        map.withSupplier(42, nullSup);
        assertEquals(0, map.getSuppliersCallCount());
        assertEquals(1, map.size());

        assertNull(map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
        assertEquals(1, map.size());

        assertNull(map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
        assertEquals(1, map.size());
    }

    @Test
    public void clear() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        map.withSupplier(21, lazyCounter);
        map.withSupplier(42, lazyCounter);
        assertEquals("X1", map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
        assertEquals(2, map.size());
        map.clear();
        assertEquals(0, map.size());
        assertNull(map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
    }

    @Test
    public void testHashCode() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        int hc = map.hashCode();
        map.put(21, TEST_STRING);
        assertNotEquals(hc, hashCode());
        hc = map.hashCode();
        map.withSupplier(42, lazyCounter);
        assertNotEquals(hc, hashCode());
    }

    @Test
    public void testEquals() {
        final Supplier<String> constant = () -> "Some String";
        final LazyLoadingMap<Integer, String> A = new LazyLoadingMap<>();
        final LazyLoadingMap<Integer, String> B = new LazyLoadingMap<>();
        assertEquals(A, B);

        A.withSupplier(42, constant);
        A.put(21, TEST_STRING);
        assertNotEquals(A, B);
        assertEquals(1, A.getSuppliersCallCount());
        assertEquals(0, B.getSuppliersCallCount());

        B.withSupplier(42, constant);
        assertNotEquals(A, B);
        B.put(21, TEST_STRING);
        assertEquals(B, A);
        assertEquals(1, A.getSuppliersCallCount());
        assertEquals(1, B.getSuppliersCallCount());
    }

    @Test
    public void nullKey() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        assertNull(map.get(null));
    }

    @Test
    public void applesAndOranges() {
        final LazyLoadingMap<Integer, String> map = new LazyLoadingMap<>();
        final boolean isEqual = map.equals((Object)"A string");
        assertFalse(isEqual);
    }
}
