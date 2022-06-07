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

package org.apache.sling.graphql.core.mocks;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.helpers.lazyloading.LazyLoadingField;

/** Used to verify that our lazy fetchers are used correctly by the GraphQL environment */
public class LazyDataFetcher implements SlingDataFetcher<LazyDataFetcher.ExpensiveObject> {

    private AtomicInteger cost = new AtomicInteger();

    /** Simulate a lazy expensive computation */
    public class ExpensiveObject {

        private final LazyLoadingField<String> lazyName;

        ExpensiveObject(String name) {
            lazyName = new LazyLoadingField<>(() -> {
                cost.incrementAndGet();
                return name.toUpperCase();
            });
        }

        public String getExpensiveName() {
            return lazyName.get();
        }

        public String getExpensiveNameClone() {
            return lazyName.get();
        }

        public int getCheapCount() {
            return 42;
        }
    }

    public int getCost() {
        return cost.get();
    }

    public void resetCost() {
        cost.set(0);
    }

    @Override
    public ExpensiveObject get(SlingDataFetcherEnvironment e) throws Exception {
        return new ExpensiveObject(getClass().getSimpleName());
    }
}
