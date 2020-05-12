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

package org.apache.sling.graphql.core.schema;

import graphql.schema.DataFetcher;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.graphqljava.DataFetcherProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

public class DataFetcherSelector {

    private BundleContext bundleContext;

    public DataFetcherSelector(BundleContext ctx) {
        bundleContext = ctx;
    }

    @Nullable
    public DataFetcher<Object> getDataFetcherForType(@NotNull DataFetcherDefinition def, @NotNull Resource r) throws IOException {
        // Query the DataFetcherProvider services for the
        // request namespace, first one that returns a
        // non-null value wins.
        final String filter = "(namespace=" + def.getFetcherNamespace() + ")";
        ServiceReference<?>[] refs= null;
        try {
            refs = bundleContext.getServiceReferences(DataFetcherProvider.class.getName(), filter);
        } catch(InvalidSyntaxException ise) {
            throw new IOException("Invalid OSGi filter syntax", ise);
        }

        if(refs == null) {
            return null;
        }

        try {
            final Optional<DataFetcher<Object>> result =
                Arrays.stream(refs)
                .sorted()
                .map(ref -> (DataFetcherProvider)bundleContext.getService(ref))
                .map(provider -> createProvider(def, r, provider))
                .filter(fetcher -> fetcher != null)
                .findFirst();
            return result.isPresent() ? result.get() : null;
        } finally {
            Arrays.stream(refs).forEach(ref -> bundleContext.ungetService(ref));
        }
    }

    static DataFetcher<Object> createProvider(DataFetcherDefinition def, Resource r, DataFetcherProvider p) throws RuntimeException {
        try {
            return p.createDataFetcher(r, def.getFetcherName(), def.getFetcherOptions(), def.getFetcherSourceExpression());
        } catch(IOException ioe) {
            throw new RuntimeException("IOException in providerMatches", ioe);
        }
    }
}
