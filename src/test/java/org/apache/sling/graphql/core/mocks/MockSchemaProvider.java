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
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;

public class MockSchemaProvider implements SchemaProvider {

    private static final String MOCK_SCHEMA_PATH = "/test-schema.txt";
    private static final String MOCK_SCHEMA;

    static {
        try (InputStream is = MockSchemaProvider.class.getResourceAsStream(MOCK_SCHEMA_PATH)) {
            final StringWriter w = new StringWriter();
            IOUtils.copy(is, w);
            MOCK_SCHEMA = w.toString();
        } catch(IOException ioe) {
            throw new RuntimeException("Error reading " + MOCK_SCHEMA_PATH, ioe);
        }
    }

    @Override
    public String getSchema(Resource r, String[] selectors) {
        return MOCK_SCHEMA;
    }

}
