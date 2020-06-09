
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

package org.apache.sling.graphql.core.scripting;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.graphql.core.engine.GraphQLResourceQuery;
import org.apache.sling.graphql.core.json.JsonSerializer;

import graphql.ExecutionResult;

public class GraphQLScriptEngine extends AbstractScriptEngine {

    private final GraphQLScriptEngineFactory factory;
    private final JsonSerializer jsonSerializer = new JsonSerializer();
    public static final int JSON_INDENT_SPACES = 2;

    public GraphQLScriptEngine(GraphQLScriptEngineFactory factory) {
        this.factory = factory;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        return eval(new StringReader(script), context);
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        try {
            final GraphQLResourceQuery q = new GraphQLResourceQuery();

            final Resource resource = (Resource) context.getBindings(ScriptContext.ENGINE_SCOPE)
                    .get(SlingBindings.RESOURCE);
            final String [] selectors = getRequestSelectors(resource);
            final ExecutionResult result = q.executeQuery(factory.getSchemaProviders(), factory.getdataFetcherSelector(),
                    resource, selectors, IOUtils.toString(reader), null);
            final PrintWriter out = (PrintWriter) context.getBindings(ScriptContext.ENGINE_SCOPE).get(SlingBindings.OUT);
            jsonSerializer.sendJSON(out, result);
        } catch(Exception e) {
            throw new ScriptException(e);
        }
        return null;
    }

    @Override
    public Bindings createBindings() {
        return null;
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    /** We don't get selectors directly but we can get them
     *  by interpreting the Resource metadata: the resolution path
     *  info provides the part of the path that wasn't used to resolve
     *  the resource, which is the selectors + extension if it
     *  starts with a dot
     */
    private String [] getRequestSelectors(Resource r) {
        final List<String> result = new ArrayList<>();
        if(r != null) {
            final String pathInfo = r.getResourceMetadata().getResolutionPathInfo();
            if(pathInfo != null && pathInfo.startsWith(".")) {
                final String [] parts = pathInfo.split("\\.");
                Arrays.stream(parts).limit(parts.length - 1).forEach(it -> result.add(it));
            }
        }
        return result.toArray(new String[] {});
    }
}
