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
package org.apache.sling.graphql.core.scalars;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;
import org.apache.sling.graphql.api.ScalarConversionException;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.SlingScalarConverter;
import org.apache.sling.graphql.core.mocks.TestUtil;
import org.apache.sling.graphql.core.mocks.URLScalarConverter;
import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SlingScalarsProviderTest {

    @Rule
    public final OsgiContext context = new OsgiContext();

    private SlingScalarsProvider provider;

    @Before
    public void setup() {
        context.registerInjectActivateService(new SlingScalarsProvider());
        provider = context.getService(SlingScalarsProvider.class);
    }

    @Test
    public void generationIncrementsOnBindAndUnbind() {
        assertEquals(0L, provider.getScalarGeneration());
        ServiceRegistration<?> first =
                TestUtil.registerSlingScalarConverter(context.bundleContext(), "URL", new URLScalarConverter());
        assertEquals(1L, provider.getScalarGeneration());
        ServiceRegistration<?> second =
                TestUtil.registerSlingScalarConverter(context.bundleContext(), "Other", new URLScalarConverter());
        assertEquals(2L, provider.getScalarGeneration());
        first.unregister();
        assertEquals(3L, provider.getScalarGeneration());
        second.unregister();
        assertEquals(4L, provider.getScalarGeneration());
    }

    @Test
    public void snapshotGenerationMatchesLiveGeneration() {
        TestUtil.registerSlingScalarConverter(context.bundleContext(), "URL", new URLScalarConverter());
        SlingScalarsProvider.CustomScalars snapshot = provider.getCustomScalars(schemaScalars("URL"));
        assertEquals(provider.getScalarGeneration(), snapshot.getGeneration());
        assertEquals(1, countScalars(snapshot));
    }

    @Test
    public void replacementUsesHighestRankedConverter() {
        TestUtil.registerSlingScalarConverter(context.bundleContext(), "URL", 0, new LabeledConverter("low"));
        TestUtil.registerSlingScalarConverter(context.bundleContext(), "URL", 50, new LabeledConverter("high"));
        SlingScalarsProvider.CustomScalars snapshot = provider.getCustomScalars(schemaScalars("URL"));
        GraphQLScalarType scalar = snapshot.getScalars().iterator().next();
        assertEquals("high", scalar.getDescription());
    }

    @Test
    public void removalDropsConverter() {
        ServiceRegistration<?> reg =
                TestUtil.registerSlingScalarConverter(context.bundleContext(), "URL", new URLScalarConverter());
        assertEquals(1, countScalars(provider.getCustomScalars(schemaScalars("URL"))));
        long generationBefore = provider.getScalarGeneration();
        reg.unregister();
        assertTrue(provider.getScalarGeneration() > generationBefore);
        Map<String, ScalarTypeDefinition> missing = schemaScalars("URL");
        try {
            provider.getCustomScalars(missing);
        } catch (SlingGraphQLException e) {
            assertTrue(e.getMessage().contains("not found"));
            return;
        }
        fail("Expected missing converter after unbind");
    }

    @Test
    public void graphqlSpecifiedScalarsAreSkipped() {
        Map<String, ScalarTypeDefinition> schemaScalars = schemaScalars("String", "Int");
        SlingScalarsProvider.CustomScalars snapshot = provider.getCustomScalars(schemaScalars);
        assertEquals(0, countScalars(snapshot));
        assertEquals(0L, snapshot.getGeneration());
    }

    @Test(timeout = 5000)
    public void getCustomScalarsDoesNotHoldLockDuringToString() {
        final AtomicBoolean registeredOther = new AtomicBoolean();
        SlingScalarConverter<String, String> converter = new SlingScalarConverter<String, String>() {
            @Override
            public @Nullable String parseValue(@Nullable String input) {
                return input;
            }

            @Override
            public @Nullable String serialize(@Nullable String value) {
                return value;
            }

            @Override
            public String toString() {
                TestUtil.registerSlingScalarConverter(context.bundleContext(), "Other", new URLScalarConverter());
                registeredOther.set(true);
                return "URL";
            }
        };
        TestUtil.registerSlingScalarConverter(context.bundleContext(), "URL", converter);
        provider.getCustomScalars(schemaScalars("URL"));
        assertTrue(registeredOther.get());
        assertEquals(2L, provider.getScalarGeneration());
    }

    private static Map<String, ScalarTypeDefinition> schemaScalars(String... names) {
        Map<String, ScalarTypeDefinition> map = new HashMap<>();
        for (String name : names) {
            map.put(
                    name,
                    ScalarTypeDefinition.newScalarTypeDefinition().name(name).build());
        }
        return map;
    }

    private static int countScalars(SlingScalarsProvider.CustomScalars snapshot) {
        int count = 0;
        for (GraphQLScalarType ignored : snapshot.getScalars()) {
            count++;
        }
        return count;
    }

    private static final class LabeledConverter implements SlingScalarConverter<String, String> {
        private final String label;

        private LabeledConverter(String label) {
            this.label = label;
        }

        @Override
        public @Nullable String parseValue(@Nullable String input) throws ScalarConversionException {
            return input;
        }

        @Override
        public @Nullable String serialize(@Nullable String value) throws ScalarConversionException {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
