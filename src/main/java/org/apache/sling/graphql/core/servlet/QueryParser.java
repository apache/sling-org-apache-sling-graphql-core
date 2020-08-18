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

package org.apache.sling.graphql.core.servlet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.graphql.core.json.JsonSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QueryParser {

    private QueryParser() {}

    static class Result {

        private final String query;
        private final Map<String, Object> variables;

        Result(String query, Map<String, Object> variables) {
            this.query = query;
            this.variables = variables;
        }

        @NotNull
        public String getQuery() {
            return query;
        }

        @NotNull
        public Map<String, Object> getVariables() {
            return variables;
        }
    }

    private static final String MIME_TYPE_JSON = "application/json";
    private static final JsonSerializer jsonSerializer = new JsonSerializer();
    private static final String JSON_KEY_QUERY = "query";
    private static final String JSON_KEY_VARIABLES = "variables";

    @Nullable
    public static Result fromRequest(@NotNull SlingHttpServletRequest request) throws IOException {
        String query = null;
        Map<String, Object> variables = null;
        if (request.getMethod().equalsIgnoreCase("POST") && MIME_TYPE_JSON.equals(request.getContentType())) {
            Map<String, Object> requestJson = getInputJson(request);
            query = (String) requestJson.get(JSON_KEY_QUERY);
            if (query != null) {
                query = query.replace("\\n", "\n");
            }
            variables = (Map<String, Object>) requestJson.get(JSON_KEY_VARIABLES);
        }

        if (query == null) {
            query = request.getParameter(JSON_KEY_QUERY);
        }

        if (variables == null) {
            variables = Collections.emptyMap();
        }
        if (query != null) {
            return new Result(query, variables);
        }
        return null;
    }

    public static Result fromJSON(String json) throws IOException {
        Map<String, Object> jsonMap = jsonSerializer.JSONtoMaps(IOUtils.toInputStream(json, StandardCharsets.UTF_8));
        String query = (String) jsonMap.get(JSON_KEY_QUERY);
        if (query != null) {
            Map<String, Object> variables = (Map<String, Object>) jsonMap.get(JSON_KEY_VARIABLES);
            if (variables == null) {
                variables = Collections.emptyMap();
            }
            return new Result(query, variables);
        }
        throw new IOException("The provided JSON structure does not contain a query.");

    }

    private static Map<String, Object> getInputJson(SlingHttpServletRequest req) throws IOException {
        return jsonSerializer.JSONtoMaps(new ReaderInputStream(req.getReader()));
    }

}
