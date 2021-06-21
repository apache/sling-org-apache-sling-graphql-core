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

public class LazyLoadingMap<K, T> extends HashMap<K, T> {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Map<K, Supplier<T>> suppliers = new HashMap<>();
    private int suppliersCallCount;

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

    /** Adds a Supplier that provides a lazy loaded value 
     *  @return this object, to be able to chain calls
    */
    public LazyLoadingMap<K, T> withSupplier(K key, Supplier<T> supplier) {
        suppliers.put(key, supplier);
        return this;
    }

    @Override
    public T get(Object key) {
        return lazyCompute(key);
    }

    @SuppressWarnings("unchecked")
    private T lazyCompute(Object key) {
        if(key == null) {
            return null;
        }
        T value = super.get(key);
        if(value == null) {
            synchronized(this) {
                if(value == null) {
                    final Supplier<T> s = suppliers.remove(key);
                    if(s != null) {
                        suppliersCallCount++;
                        value = s.get();
                        super.put((K)key, value);
                    }
                }
            }
        }
        return value;
    }


    /** This indicates how many Supplier calls have been made.
     *  Can be useful in case of doubt, as several methods need
     *  to call computeAll().
     */
    public int getSuppliersCallCount() {
        return suppliersCallCount;
    }

    @Override
    public T remove(Object key) {
        synchronized(this) {
            lazyCompute(key);
            return super.remove(key);
        }        
    }

    @Override
    public void clear() {
        synchronized(this) {
            suppliers.clear();
            super.clear();
        }
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
        log.info("computeAll called, all remaining lazy values will be evaluated now");
        suppliers.entrySet().forEach(e -> {
            if(!super.containsKey(e.getKey())) {
                suppliersCallCount++;
                put(e.getKey(), e.getValue().get());
            }
        });
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
}
