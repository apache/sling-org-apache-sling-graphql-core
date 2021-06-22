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
    private Supplier<String> counterSupplier = () -> "X" + String.valueOf(++counter);
    private final Supplier<String> constantSupplier = () -> TEST_STRING;
    private LazyLoadingMap<Integer, String> map;

    @Before
    public void setup() {
        counter = 0;
        map = new LazyLoadingMap<>();
    }

    @Test
    public void basicTest() {
        assertNull(map.get(42));

        map.put(21, () -> UUID.randomUUID().toString());
        map.put(42, () -> TEST_STRING);
        assertEquals(0, map.getSuppliersCallCount());
        assertEquals(TEST_STRING, map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
        final String random = map.get(21);
        assertNotNull(random);
        assertEquals(random, map.get(21));
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void suppliersAndDirectValues() {
        map.put(42, counterSupplier);
        assertEquals("X1", map.get(42));
        map.get(42);
        map.put(42, TEST_STRING);
        assertEquals(TEST_STRING, map.get(42));
        map.put(42, counterSupplier);
        assertEquals("X2", map.get(42));
        map.get(42);
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void remove() {
        map.put(21, counterSupplier);
        map.put(42, TEST_STRING);
        assertEquals(2, map.size());
        assertEquals(TEST_STRING, map.get(42));
        assertEquals(TEST_STRING, map.remove(42));
        assertNull(map.get(42));
        assertEquals(1, map.size());
        assertEquals(0, map.getSuppliersCallCount());

        // Remove before and after computing
        assertEquals(0, map.getSuppliersCallCount());
        map.put(112, counterSupplier);
        map.put(113, counterSupplier);
        assertEquals("X1", map.get(113));
        assertEquals("X1", map.remove(113));
        assertEquals(1, map.getSuppliersCallCount());
        assertEquals("X2", map.remove(112));
        assertEquals(2, map.getSuppliersCallCount());
    }

    @Test
    public void containsValueComputesEverything() {
        assertFalse(map.containsKey(42));
        assertEquals(0, map.getSuppliersCallCount());

        assertFalse(map.containsValue("X1"));
        map.put(42, counterSupplier);
        assertTrue(map.containsValue("X1"));

        assertFalse(map.containsValue("X2"));
        map.put(21, counterSupplier);
        assertEquals(1, map.getSuppliersCallCount());
        assertTrue(map.containsValue("X1"));
        assertTrue(map.containsValue("X2"));
        assertEquals(2, map.getSuppliersCallCount());

        assertFalse(map.containsValue(TEST_STRING));
        map.put(71, TEST_STRING);
        map.put(92, counterSupplier);
        map.put(93, counterSupplier);
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
        assertFalse(map.containsKey(42));
        assertEquals(0, map.getSuppliersCallCount());

        map.put(42, counterSupplier);
        map.put(21, "nothing");

        assertTrue(map.containsKey(42));
        assertTrue(map.containsKey(21));
        assertFalse(map.containsKey(22));

        assertEquals(0, map.getSuppliersCallCount());
    }

    @Test
    public void keySet() {
        assertEquals(0, map.keySet().size());
        map.put(112, "nothing");
        assertEquals(1, map.keySet().size());
        map.put(21, counterSupplier);
        map.put(42, counterSupplier);
        map.put(110, counterSupplier);

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
        map.put(112, TEST_STRING);
        map.put(21, counterSupplier);
        map.put(42, counterSupplier);

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
        map.put(112, TEST_STRING);
        map.put(21, counterSupplier);
        map.put(42, counterSupplier);

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
        assertTrue(map.isEmpty());

        map.put(112, TEST_STRING);
        assertFalse(map.isEmpty());
        map.put(42, counterSupplier);
        assertFalse(map.isEmpty());

        map.remove(112);
        assertFalse(map.isEmpty());
        map.remove(42);
        assertTrue(map.isEmpty());

        assertEquals(1, map.getSuppliersCallCount());
    }

    @Test
    public void nullSupplier() {
        final Supplier<String> nullSup = () -> null;

        map.put(42, nullSup);
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
        map.put(21, counterSupplier);
        map.put(42, counterSupplier);
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
        int hc = map.hashCode();
        map.put(21, TEST_STRING);
        assertNotEquals(hc, hashCode());
        hc = map.hashCode();
        map.put(42, counterSupplier);
        assertNotEquals(hc, hashCode());
    }

    @Test
    public void testEquals() {
        final LazyLoadingMap<Integer, String> A = new LazyLoadingMap<>();
        final LazyLoadingMap<Integer, String> B = new LazyLoadingMap<>();
        assertEquals(A, B);

        A.put(42, constantSupplier);
        A.put(21, TEST_STRING);
        assertNotEquals(A, B);
        assertEquals(1, A.getSuppliersCallCount());
        assertEquals(0, B.getSuppliersCallCount());

        B.put(42, constantSupplier);
        assertNotEquals(A, B);
        B.put(21, TEST_STRING);
        assertEquals(B, A);
        assertEquals(1, A.getSuppliersCallCount());
        assertEquals(1, B.getSuppliersCallCount());
    }

    @Test
    public void replaceSupplierBeforeUsingIt() {
        map.put(42, counterSupplier);
        map.put(42, constantSupplier);
        assertEquals(TEST_STRING, map.get(42));
        assertEquals(1, map.getSuppliersCallCount());
    }

    @Test
    public void nullKey() {
        assertNull(map.get(null));
    }

    @Test
    public void applesAndOranges() {
        final boolean isEqual = map.equals((Object)"A string");
        assertFalse(isEqual);
    }
}
