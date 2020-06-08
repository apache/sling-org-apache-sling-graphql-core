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

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScript;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;

/** Mock Servlet that also implements SlingScript */
public class MockScriptServlet extends SlingSafeMethodsServlet implements SlingScript {
    private static final long serialVersionUID = 1L;

    @Override
    public Resource getScriptResource() {
        throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public Object eval(SlingBindings props) {
        final Resource r = (Resource)props.get("resource");
        final SlingDataFetcherEnvironment env = (SlingDataFetcherEnvironment)props.get("environment");

        final Map<String, Object> result = new HashMap<>();
        result.put("testingArgument", env == null ? null : env.getArgument("testing"));
        result.put("resourcePath", r == null ? null : r.getPath());
        result.put("boolValue", true);
        return result;
    }

    @Override
    public Object call(SlingBindings props, String method, Object... args) {
        throw new UnsupportedOperationException(getClass().getName());
    }
}
