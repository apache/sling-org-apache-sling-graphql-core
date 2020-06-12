
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

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.servlethelpers.internalrequests.InternalRequest;
import org.apache.sling.servlethelpers.internalrequests.SlingInternalRequest;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides a Resource-specific GraphQL Schema, as text */
@Component(service = SchemaProvider.class, immediate = true, property = {
        Constants.SERVICE_RANKING + ":Integer=" + DefaultSchemaProvider.SERVICE_RANKING,
        Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting GraphQL SchemaProvider",
        Constants.SERVICE_VENDOR + "=The Apache Software Foundation" })
public class DefaultSchemaProvider implements SchemaProvider {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final int SERVICE_RANKING = Integer.MAX_VALUE - 100;
    public static final String SCHEMA_EXTENSION = "GQLschema";
    public static final String DEFAULT_SCHEMA = "";

    @Reference
    private SlingRequestProcessor requestProcessor;

    @Override
    public String getSchema(Resource r, String [] selectors) throws IOException {
        // TODO using a servletRequest should be more efficient - not tested yet
        final InternalRequest req =
            new SlingInternalRequest(r.getResourceResolver(), requestProcessor, r.getPath())
            .withResourceType(r.getResourceType())
            .withResourceSuperType(r.getResourceSuperType())
            .withSelectors(selectors)
            .withExtension(SCHEMA_EXTENSION)
        ;

        log.debug("Getting GraphQL Schema for {}: {}", r.getPath(), req);

        if(req.execute().getStatus() == HttpServletResponse.SC_OK) {
            return req.getResponseAsString();
        } else {
            return DEFAULT_SCHEMA;
        }
    }
}
