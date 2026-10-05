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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.core.hash.SHA256Hasher;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DefaultQueryExecutorCacheTest {

    @Mock
    private RankedSchemaProviders schemaProvider;

    @Mock
    private SlingDataFetcherSelector dataFetcherSelector;

    @Mock
    private SlingTypeResolverSelector typeResolverSelector;

    @Mock
    private SlingScalarsProvider scalarsProvider;

    @Mock
    private Resource resource;

    @InjectMocks
    private DefaultQueryExecutor executor;

    @Before
    public void setUp() {
        activate(10, false);
        when(resource.getPath()).thenReturn("/content/test");
        stubStableScalars();
    }

    private void stubStableScalars() {
        when(scalarsProvider.getCustomScalars(any()))
                .thenReturn(new SlingScalarsProvider.CustomScalars(0L, Collections.emptyList()));
        when(scalarsProvider.getScalarGeneration()).thenReturn(0L);
    }

    private void activate(int schemaCacheSize, boolean executableSchemaCacheEnabled) {
        activate(schemaCacheSize, schemaCacheSize, executableSchemaCacheEnabled);
    }

    private void activate(int schemaCacheSize, int executableSchemaCacheSize, boolean executableSchemaCacheEnabled) {
        DefaultQueryExecutor.Config config = mock(DefaultQueryExecutor.Config.class);
        when(config.schemaCacheSize()).thenReturn(schemaCacheSize);
        when(config.executableSchemaCacheSize()).thenReturn(executableSchemaCacheSize);
        when(config.executableSchemaCacheEnabled()).thenReturn(executableSchemaCacheEnabled);
        when(config.maxQueryTokens()).thenReturn(15000);
        when(config.maxWhitespaceTokens()).thenReturn(200000);
        when(config.maxFieldCount()).thenReturn(100000);
        executor.activate(config);
    }

    @Test
    public void testExecutableSchemaCache_AnnotationDefaultIsEnabled() throws Exception {
        Object defaultValue = DefaultQueryExecutor.Config.class
                .getMethod("executableSchemaCacheEnabled")
                .getDefaultValue();
        assertEquals(Boolean.TRUE, defaultValue);
    }

    @Test
    public void testGetTypeDefinitionRegistry_ValidSDL() {
        String validSDL = "type Query { hello: String }";
        String[] selectors = {"test"};

        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(validSDL, resource, selectors);

        assertNotNull(result);
        assertTrue(result.getType("Query").isPresent());
    }

    @Test
    public void testGetTypeDefinitionRegistry_InvalidSDL() {
        String invalidSDL = "invalid graphql syntax {";
        String[] selectors = {"test"};

        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(invalidSDL, resource, selectors);

        assertNull(result);
    }

    @Test
    public void testGetTypeDefinitionRegistry_CacheDisabled() {
        activate(0, false);

        String sdl = "type Query { hello: String }";
        String[] selectors = {"test"};

        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(sdl, resource, selectors);

        assertNotNull(result);
    }

    @Test
    public void testGetTypeDefinitionRegistry_Extended() {
        Resource resource2 = mock(Resource.class);
        when(resource2.getPath()).thenReturn("/content/test2");

        activate(2, false);

        String sdl = "type Query { hello: String }";
        String[] selectors = {"test"};

        executor.getTypeDefinitionRegistry(sdl, resource2, selectors);
        executor.getTypeDefinitionRegistry(sdl + " ", resource, selectors);
        executor.getTypeDefinitionRegistry(sdl + "  ", resource, selectors);
        TypeDefinitionRegistry result = executor.getTypeDefinitionRegistry(sdl, resource2, selectors);
        assertNotNull(result);
    }

    @Test
    public void testExecutableSchemaCache_ReusesSameInstance() {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        GraphQLSchema first = executor.getExecutableSchema(hash, registry);
        GraphQLSchema second = executor.getExecutableSchema(hash, registry);

        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    public void testExecutableSchemaCache_DisabledBuildsIndependently() {
        activate(10, false);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        GraphQLSchema first = executor.getExecutableSchema(hash, registry);
        GraphQLSchema second = executor.getExecutableSchema(hash, registry);

        assertNotNull(first);
        assertNotNull(second);
        // Without the cache each call creates a new GraphQLSchema instance
        assertNotSame(first, second);
    }

    @Test
    public void testExecutableSchemaCache_SingleFlightUnderContention() throws Exception {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        final AtomicInteger buildCalls = new AtomicInteger();
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch buildStarted = new CountDownLatch(1);
        final CountDownLatch releaseBuild = new CountDownLatch(1);
        final CyclicBarrier barrier = new CyclicBarrier(8, started::countDown);

        // Hold the winner in buildSchema until other threads have joined the in-flight Future
        when(scalarsProvider.getCustomScalars(any()))
                .thenAnswer((Answer<SlingScalarsProvider.CustomScalars>) invocation -> {
                    buildCalls.incrementAndGet();
                    buildStarted.countDown();
                    if (!releaseBuild.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release schema build");
                    }
                    return new SlingScalarsProvider.CustomScalars(0L, Collections.emptyList());
                });
        when(scalarsProvider.getScalarGeneration()).thenReturn(0L);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<GraphQLSchema>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return executor.getExecutableSchema(hash, registry);
                }));
            }
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(buildStarted.await(5, TimeUnit.SECONDS));
            releaseBuild.countDown();

            GraphQLSchema first = null;
            for (Future<GraphQLSchema> future : futures) {
                GraphQLSchema schema = future.get(10, TimeUnit.SECONDS);
                assertNotNull(schema);
                if (first == null) {
                    first = schema;
                } else {
                    assertSame(first, schema);
                }
            }
            assertEquals("Expected a single schema build under contention", 1, buildCalls.get());
        } finally {
            releaseBuild.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void testExecutableSchemaCache_ServesUncachedSchemaWhenScalarGenerationNeverStabilizes() {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        when(scalarsProvider.getCustomScalars(any()))
                .thenReturn(new SlingScalarsProvider.CustomScalars(1L, Collections.emptyList()));
        // Live generation always ahead of the snapshot used for wiring → never stable
        when(scalarsProvider.getScalarGeneration()).thenReturn(2L);

        GraphQLSchema first = executor.getExecutableSchema(hash, registry);
        GraphQLSchema second = executor.getExecutableSchema(hash, registry);
        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
    }

    @Test
    public void testExecutableSchemaCache_RetriesUntilScalarGenerationStabilizes() {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        final AtomicInteger liveGeneration = new AtomicInteger(1);
        when(scalarsProvider.getScalarGeneration()).thenAnswer(invocation -> (long) liveGeneration.get());
        when(scalarsProvider.getCustomScalars(any())).thenAnswer(invocation -> {
            long snapshot = liveGeneration.get();
            // First build sees generation bump before publish; subsequent builds are stable.
            if (snapshot == 1) {
                liveGeneration.set(2);
            }
            return new SlingScalarsProvider.CustomScalars(snapshot, Collections.emptyList());
        });

        GraphQLSchema schema = executor.getExecutableSchema(hash, registry);
        assertNotNull(schema);
        assertEquals(2, liveGeneration.get());
        assertSame(schema, executor.getExecutableSchema(hash, registry));
    }

    @Test
    public void testExecutableSchemaCache_OldGenerationCallerDoesNotClobberNewerEntry() throws Exception {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        when(scalarsProvider.getScalarGeneration()).thenReturn(1L);
        when(scalarsProvider.getCustomScalars(any()))
                .thenReturn(new SlingScalarsProvider.CustomScalars(1L, Collections.emptyList()));
        GraphQLSchema newer = executor.getExecutableSchema(hash, registry);
        assertNotNull(newer);
        assertEquals(1L, cachedGeneration(hash));

        // Delayed old-generation caller
        when(scalarsProvider.getScalarGeneration()).thenReturn(0L);
        when(scalarsProvider.getCustomScalars(any()))
                .thenReturn(new SlingScalarsProvider.CustomScalars(0L, Collections.emptyList()));
        GraphQLSchema older = executor.getExecutableSchema(hash, registry);
        assertNotNull(older);
        assertNotSame(newer, older);
        // Newer cache entry must remain
        assertEquals(1L, cachedGeneration(hash));

        when(scalarsProvider.getScalarGeneration()).thenReturn(1L);
        assertSame(newer, executor.getExecutableSchema(hash, registry));
    }

    @Test
    public void testExecutableSchemaCache_ErrorDuringBuildUnblocksWaiters() throws Exception {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        final CountDownLatch buildStarted = new CountDownLatch(1);
        final CountDownLatch releaseBuild = new CountDownLatch(1);
        when(scalarsProvider.getCustomScalars(any())).thenAnswer(invocation -> {
            buildStarted.countDown();
            if (!releaseBuild.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release schema build");
            }
            throw new AssertionError("simulated builder abort");
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> builder = pool.submit(() -> {
                try {
                    executor.getExecutableSchema(hash, registry);
                    fail("Expected AssertionError");
                } catch (AssertionError expected) {
                    assertEquals("simulated builder abort", expected.getMessage());
                }
            });
            assertTrue(buildStarted.await(5, TimeUnit.SECONDS));

            final AtomicReference<Thread> waiterThread = new AtomicReference<>();
            Future<?> waiter = pool.submit(() -> {
                waiterThread.set(Thread.currentThread());
                try {
                    executor.getExecutableSchema(hash, registry);
                    fail("Expected IllegalStateException from aborted build");
                } catch (IllegalStateException e) {
                    assertTrue(e.getMessage().contains("aborted"));
                }
            });

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                Thread t = waiterThread.get();
                if (t != null && (t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING)) {
                    break;
                }
                if (System.nanoTime() > deadline) {
                    fail("Waiter thread did not block on in-flight Future.get()");
                }
                Thread.yield();
            }

            releaseBuild.countDown();
            builder.get(10, TimeUnit.SECONDS);
            waiter.get(10, TimeUnit.SECONDS);
            assertTrue(inFlightMap().isEmpty());
        } finally {
            releaseBuild.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void testExecutableSchemaCache_BuildRuntimeExceptionPropagates() {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        when(scalarsProvider.getCustomScalars(any())).thenThrow(new IllegalStateException("boom"));

        try {
            executor.getExecutableSchema(hash, registry);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("boom", e.getMessage());
        }
    }

    @Test
    public void testExecutableSchemaCache_RejectsStaleCacheEntryOnHit() {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        GraphQLSchema first = executor.getExecutableSchema(hash, registry);
        assertNotNull(first);
        assertSame(first, executor.getExecutableSchema(hash, registry));

        // Converter set changed after the entry was cached
        when(scalarsProvider.getScalarGeneration()).thenReturn(1L);
        when(scalarsProvider.getCustomScalars(any()))
                .thenReturn(new SlingScalarsProvider.CustomScalars(1L, Collections.emptyList()));

        GraphQLSchema second = executor.getExecutableSchema(hash, registry);
        assertNotNull(second);
        assertNotSame(first, second);
        assertSame(second, executor.getExecutableSchema(hash, registry));
    }

    @Test
    public void testExecutableSchemaCache_WaiterSeesRuntimeFailure() throws Exception {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("build failed"));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CompletableFuture raw = failed;
        inFlightMap().put(hash + ":0", raw);

        try {
            executor.getExecutableSchema(hash, registry);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals("build failed", e.getMessage());
        }
    }

    @Test
    public void testExecutableSchemaCache_WaiterSeesNonRuntimeFailure() throws Exception {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        CompletableFuture<Object> failed = new CompletableFuture<>();
        failed.completeExceptionally(new Exception("checked failure"));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CompletableFuture raw = failed;
        inFlightMap().put(hash + ":0", raw);

        try {
            executor.getExecutableSchema(hash, registry);
            fail("Expected SlingGraphQLException");
        } catch (SlingGraphQLException e) {
            assertTrue(e.getMessage().contains("Executable schema build failed"));
            assertEquals("checked failure", e.getCause().getMessage());
        }
    }

    @Test
    public void testExecutableSchemaCache_WaiterInterrupted() throws Exception {
        activate(10, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        // Never-completing future so the waiter blocks in Future.get()
        inFlightMap().put(hash + ":0", new CompletableFuture<>());

        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicInteger failures = new AtomicInteger();
        Thread waiter = new Thread(() -> {
            try {
                entered.countDown();
                executor.getExecutableSchema(hash, registry);
            } catch (SlingGraphQLException e) {
                if (e.getMessage().contains("Interrupted while waiting")
                        && Thread.currentThread().isInterrupted()) {
                    failures.incrementAndGet();
                }
            } finally {
                done.countDown();
            }
        });
        waiter.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        // Wait until the thread is blocked inside Future.get()
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (waiter.getState() != Thread.State.WAITING && waiter.getState() != Thread.State.TIMED_WAITING) {
            if (System.nanoTime() > deadline) {
                fail("Waiter thread did not block on Future.get()");
            }
            Thread.yield();
        }
        waiter.interrupt();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(1, failures.get());
        inFlightMap().clear();
    }

    @Test
    public void testExecutableSchemaCache_EvictsLeastRecentlyUsed() throws Exception {
        activate(10, 2, true);
        TypeDefinitionRegistry registry1 =
                executor.getTypeDefinitionRegistry("type Query { hello: String }", resource, new String[] {"test"});
        TypeDefinitionRegistry registry2 =
                executor.getTypeDefinitionRegistry("type Query { world: String }", resource, new String[] {"test"});
        TypeDefinitionRegistry registry3 =
                executor.getTypeDefinitionRegistry("type Query { third: String }", resource, new String[] {"test"});
        String hash1 = SHA256Hasher.getHash("type Query { hello: String }");
        String hash2 = SHA256Hasher.getHash("type Query { world: String }");
        String hash3 = SHA256Hasher.getHash("type Query { third: String }");

        GraphQLSchema first = executor.getExecutableSchema(hash1, registry1);
        GraphQLSchema second = executor.getExecutableSchema(hash2, registry2);
        assertSame(first, executor.getExecutableSchema(hash1, registry1));

        GraphQLSchema third = executor.getExecutableSchema(hash3, registry3);
        assertNotNull(third);
        // Inspect without Map.get — that would count as an LRU access.
        assertNotNull(cachedEntry(hash1));
        assertNull(cachedEntry(hash2));
        assertNotNull(cachedEntry(hash3));
        assertSame(first, executor.getExecutableSchema(hash1, registry1));
        GraphQLSchema secondAgain = executor.getExecutableSchema(hash2, registry2);
        assertNotSame(second, secondAgain);
    }

    @Test
    public void testExecutableSchemaCache_PurgesOtherHashesWhenScalarGenerationChanges() throws Exception {
        activate(10, true);
        TypeDefinitionRegistry registryA =
                executor.getTypeDefinitionRegistry("type Query { hello: String }", resource, new String[] {"test"});
        TypeDefinitionRegistry registryB =
                executor.getTypeDefinitionRegistry("type Query { world: String }", resource, new String[] {"test"});
        String hashA = SHA256Hasher.getHash("type Query { hello: String }");
        String hashB = SHA256Hasher.getHash("type Query { world: String }");

        assertNotNull(executor.getExecutableSchema(hashA, registryA));
        assertNotNull(executor.getExecutableSchema(hashB, registryB));
        assertNotNull(cachedEntry(hashA));
        assertNotNull(cachedEntry(hashB));

        when(scalarsProvider.getScalarGeneration()).thenReturn(1L);
        when(scalarsProvider.getCustomScalars(any()))
                .thenReturn(new SlingScalarsProvider.CustomScalars(1L, Collections.emptyList()));

        GraphQLSchema rebuiltA = executor.getExecutableSchema(hashA, registryA);
        assertNotNull(rebuiltA);
        assertNull("Unrelated hash must be purged when scalar generation changes", cachedEntry(hashB));
        assertNotNull(cachedEntry(hashA));
        assertEquals(1L, cachedGeneration(hashA));
    }

    @Test
    public void testExecutableSchemaCache_EnabledIndependentlyOfTypeRegistryCache() {
        activate(0, 8, true);
        String sdl = "type Query { hello: String }";
        TypeDefinitionRegistry registry = executor.getTypeDefinitionRegistry(sdl, resource, new String[] {"test"});
        String hash = SHA256Hasher.getHash(sdl);

        GraphQLSchema first = executor.getExecutableSchema(hash, registry);
        GraphQLSchema second = executor.getExecutableSchema(hash, registry);
        assertNotNull(first);
        assertSame(first, second);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, CompletableFuture<?>> inFlightMap() throws Exception {
        Field field = DefaultQueryExecutor.class.getDeclaredField("executableSchemaInFlight");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, CompletableFuture<?>>) field.get(executor);
    }

    private long cachedGeneration(String schemaHash) throws Exception {
        Object entry = cachedEntry(schemaHash);
        assertNotNull(entry);
        Field genField = entry.getClass().getDeclaredField("scalarGeneration");
        genField.setAccessible(true);
        return genField.getLong(entry);
    }

    private Object cachedEntry(String schemaHash) throws Exception {
        Field field = DefaultQueryExecutor.class.getDeclaredField("hashToExecutableSchemaMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ?> map = (Map<String, ?>) field.get(executor);
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (schemaHash.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
