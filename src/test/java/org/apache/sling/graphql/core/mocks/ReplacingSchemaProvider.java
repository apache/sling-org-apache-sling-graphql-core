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

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;

/**
 * SchemaProvider that replaces a regexp in the schema, or always returns a null
 * schema. provided by the DefaultSchemaProvider. Used for testing the
 * RankedSchemaProvider.
 */
public class ReplacingSchemaProvider implements SchemaProvider {

    private final String pattern;
    private final String replacement;
    private SchemaProvider defaultSchemaProvider;

    public ReplacingSchemaProvider(String pattern, String replacement) {
        this.pattern = pattern;
        this.replacement = replacement;
    }

    @Override
    public String getSchema(Resource r, String[] selectors) throws IOException {
        final String original = defaultSchemaProvider.getSchema(r, selectors);
        return original.replaceAll(pattern, replacement);
    }

    public ServiceRegistration<?> register(BundleContext ctx, SchemaProvider defaultSchemaProvider, int serviceRanking) throws InvalidSyntaxException {
        this.defaultSchemaProvider = defaultSchemaProvider;

        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(Constants.SERVICE_RANKING, serviceRanking);
        return ctx.registerService(SchemaProvider.class, this, props);
    }

}
