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

package org.apache.sling.graphql.helpers.layzloading;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A {@link java.util.HashMap} that optionally uses Suppliers to provide its
 *  values. Each Supplier is called at most once, if the corresponding
 *  value is requested. Some "global" operations requires all values
 *  to be computed, and should be considered costly.
 * 
 *  Like HashMap, this class is NOT thread safe. If needed, 
 *  {@link java.util.Collections#synchronizedMap} can be used
 *  to sychronize it.
  */
public class LazyLoadingMap<K, T> extends HashMap<K, T> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<K, Supplier<T>> suppliers = new HashMap<>();

    public class Stats {
        int suppliersCallCount;

        public int getSuppliersCallCount() {
            return suppliersCallCount;
        }

        public int getUnusedSuppliersCount() {
            return suppliers.size();
        }
    }

    private final Stats stats = new Stats();

    /** Calls computeAll - should be avoided if possible */
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof LazyLoadingMap)) {
            return false;
        }
        final LazyLoadingMap<?,?> other = (LazyLoadingMap<?,?>)o;

        // Equality seems complicated to compute without this
        computeAll();
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + suppliers.hashCode();
    }

    /** Adds a Supplier that provides a lazy loaded value.
     *  Removes existing value with the same key if it exists.
     */
    public Supplier<T> put(K key, Supplier<T> supplier) {
        super.remove(key);
        return suppliers.put(key, supplier);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(Object key) {
        computeIfAbsent((K)key, k -> {
            final Supplier<T> s = suppliers.remove(k);
            if(s != null) {
                stats.suppliersCallCount++;
                return s.get();
            }
            return null;
        });
        return super.get(key);
    }

    /** Contrary to the usual Map contract, this always
     *  returns null, to avoid calling a supplier "for nothing".
     *  If the value is needed, call {@link #get} first.
     */
    @Override
    public T remove(Object key) {
        super.remove(key);
        suppliers.remove(key);
        return null;
    }

    @Override
    public void clear() {
        suppliers.clear();
        super.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return super.containsKey(key) || suppliers.containsKey(key);
    }

    @Override
    public int size() {
        return keySet().size();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && suppliers.isEmpty();
    }

    @Override
    public Set<K> keySet() {
        final Set<K> result = new HashSet<>();
        result.addAll(super.keySet());
        result.addAll(suppliers.keySet());
        return result;
    }

    /** Required for some methods that need all our values
     *  Calling those methods should be avoided if possible
     */
    private void computeAll() {
        if(!suppliers.isEmpty()) {
            log.debug("computeAll called, all remaining lazy values will be evaluated now");
            final Set<K> keys = new HashSet<>(suppliers.keySet());
            keys.forEach(this::get);
        }
    }

    /** Calls computeAll - should be avoided if possible */
    @Override
    public Collection<T> values() {
        computeAll();
        return super.values();
    }

    /** Calls computeAll - should be avoided if possible */
    @Override
    public Set<Entry<K, T>> entrySet() {
        computeAll();
        return super.entrySet();
    }

    /** Calls computeAll - should be avoided if possible */
    @Override
    public boolean containsValue(Object value) {
        computeAll();
        return super.containsValue(value);
    }

    /** Return statistics on our suppliers, for metrics etc. */
    public Stats getStats() {
        return stats;
    }
}
