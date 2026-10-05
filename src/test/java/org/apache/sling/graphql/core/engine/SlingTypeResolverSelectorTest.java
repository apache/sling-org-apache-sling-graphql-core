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

import com.example.resolvers.DoNothingTypeResolver;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.api.SlingTypeResolverEnvironment;
import org.apache.sling.graphql.core.mocks.CharacterTypeResolver;
import org.apache.sling.graphql.core.mocks.DummyTypeResolver;
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

public class SlingTypeResolverSelectorTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingTypeResolverSelector selector;

    @Before
    public void setup() {
        context.registerInjectActivateService(new SlingTypeResolverSelector());
        selector = context.getService(SlingTypeResolverSelector.class);

        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/character", new CharacterTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/shouldFail", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "example/ok", new DoNothingTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/duplicate", 1, new DummyTypeResolver());
        TestUtil.registerSlingTypeResolver(context.bundleContext(), "sling/duplicate", 0, new CharacterTypeResolver());
    }

    @Test
    public void acceptableName() {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("example/ok");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameOk() {
        final SlingTypeResolver<Object> sdf = selector.getSlingTypeResolver("sling/character");
        assertThat(sdf, not(nullValue()));
    }

    @Test
    public void reservedNameError() {
        assertNull(selector.getSlingTypeResolver("sling/shouldFail"));
    }

    @Test
    public void sameNameTypeResolver() {
        final SlingTypeResolver<Object> str = selector.getSlingTypeResolver("sling/duplicate");
        assertNotNull(str);
        assertEquals(DummyTypeResolver.class, str.getClass());
        assertNull(str.getType(mock(SlingTypeResolverEnvironment.class)));
    }

    @Test
    public void concurrentBindUnbindAndRead() throws Exception {
        final String name = "example/concurrent";
        final int readers = 8;
        final int iterations = 200;
        final DummyTypeResolver low = new DummyTypeResolver();
        final CharacterTypeResolver high = new CharacterTypeResolver();
        final ServiceRegistration<?> lowReg = TestUtil.registerSlingTypeResolver(context.bundleContext(), name, 0, low);

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
                            SlingTypeResolver<Object> resolver = selector.getSlingTypeResolver(name);
                            if (resolver != null && resolver != low && resolver != high) {
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
                            highReg.set(TestUtil.registerSlingTypeResolver(context.bundleContext(), name, 100, high));
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

            SlingTypeResolver<Object> winner = selector.getSlingTypeResolver(name);
            assertNotNull(winner);
            ServiceRegistration<?> remainingHigh = highReg.get();
            if (remainingHigh != null) {
                assertSame(high, winner);
                remainingHigh.unregister();
            }
            assertSame(low, selector.getSlingTypeResolver(name));
            lowReg.unregister();
            assertNull(selector.getSlingTypeResolver(name));
        } finally {
            pool.shutdownNow();
        }
    }
}
