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
package org.apache.sling.graphql.core.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.example.fetchers.DoNothingFetcher;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;
import org.apache.sling.graphql.core.mocks.DigestDataFetcher;
import org.apache.sling.graphql.core.mocks.EchoDataFetcher;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class SlingDataFetcherSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingDataFetcherSelector selector;

    @Before
    public void setup() {
        context.registerInjectActivateService(new SlingDataFetcherSelector());
        selector = context.getService(SlingDataFetcherSelector.class);

        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/digest", new DigestDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/shouldFail", new DoNothingFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "example/ok", new DoNothingFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", 0, new DigestDataFetcher());
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", 10, new EchoDataFetcher(451));
        TestUtil.registerSlingDataFetcher(context.bundleContext(), "sling/duplicate", 5, new EchoDataFetcher(452));
    }

    @Test
    public void acceptableName() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("example/ok");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameOk() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("sling/digest");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameError() {
        assertNull(selector.getSlingFetcher("sling/shouldFail"));
    }

    @Test
    public void sameNameFetcher() throws Exception {
        final SlingDataFetcher<Object> sdf = selector.getSlingFetcher("sling/duplicate");
        assertNotNull(sdf);
        assertEquals(EchoDataFetcher.class, sdf.getClass());
        assertEquals(451, sdf.get(mock(SlingDataFetcherEnvironment.class)));
    }

    @Test
    public void concurrentBindUnbindAndRead() throws Exception {
        final String name = "example/concurrent";
        final int readers = 8;
        final int iterations = 200;
        final EchoDataFetcher low = new EchoDataFetcher(1);
        final EchoDataFetcher high = new EchoDataFetcher(2);
        final ServiceRegistration<?> lowReg = TestUtil.registerSlingDataFetcher(context.bundleContext(), name, 0, low);

        ExecutorService pool = Executors.newFixedThreadPool(readers + 1);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(readers + 1);
        final AtomicInteger failures = new AtomicInteger();
        final AtomicReference<ServiceRegistration<?>> highReg = new AtomicReference<>();
        try {
            for (int i = 0; i < readers; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < iterations; n++) {
                            SlingDataFetcher<Object> fetcher = selector.getSlingFetcher(name);
                            if (fetcher != null && fetcher != low && fetcher != high) {
                                failures.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            pool.submit(() -> {
                try {
                    start.await();
                    for (int n = 0; n < iterations; n++) {
                        ServiceRegistration<?> existing = highReg.getAndSet(null);
                        if (existing != null) {
                            existing.unregister();
                        } else {
                            highReg.set(TestUtil.registerSlingDataFetcher(context.bundleContext(), name, 100, high));
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
            assertEquals(0, failures.get());

            SlingDataFetcher<Object> winner = selector.getSlingFetcher(name);
            assertNotNull(winner);
            ServiceRegistration<?> remainingHigh = highReg.get();
            if (remainingHigh != null) {
                assertSame(high, winner);
                remainingHigh.unregister();
            }
            assertSame(low, selector.getSlingFetcher(name));
            lowReg.unregister();
            assertNull(selector.getSlingFetcher(name));
        } finally {
            pool.shutdownNow();
        }
    }
}
