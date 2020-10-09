
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.osgi.Order;
import org.apache.sling.commons.osgi.RankedServices;
import org.apache.sling.graphql.api.SchemaProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * Uses multiple registered SchemaProvider services to get a schema, using the
 * first one that returns non-null.
 */
@Component(service = RankedSchemaProviders.class)
public class RankedSchemaProviders implements SchemaProvider {

    /*
        before SLING-9800 this was using an ASCENDING order, using the SchemaProvider with the lowest service ranking that returned a
        non-null schema; however, this was considered inconsistent with how service implementations are provided by OSGi containers,
        where the service with the highest ranking will be returned
     */
    final RankedServices<SchemaProvider> providers = new RankedServices<>(Order.DESCENDING);

    @Override
    public @Nullable String getSchema(@NotNull final Resource r, @Nullable final String[] selectors) throws IOException {
        String result = null;

        for(SchemaProvider p : providers) {
            result = p.getSchema(r, selectors);
            if(result != null) {
                break;
            }
        }

        if(result == null) {
            final String selectorsInfo = selectors == null ? null : " / " + Arrays.asList(selectors);
            throw new IOException(
                "No schema found for " + r.getPath() + selectorsInfo
                + ", SchemaProviders=" + providers);
        }

        return result;
    }

    @Reference(
        service = SchemaProvider.class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policy = ReferencePolicy.DYNAMIC,
        policyOption = ReferencePolicyOption.GREEDY)
    protected void bindSchemaProvider(SchemaProvider service, Map<String, Object> props) {
        providers.bind(service, props);
    }

    protected void unbindSchemaProvider(SchemaProvider service, Map<String, Object> props) {
        providers.unbind(service, props);
    }
}
