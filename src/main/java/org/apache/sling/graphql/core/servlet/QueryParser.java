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
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.johnzon.mapper.Mapper;
import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.sling.api.SlingHttpServletRequest;
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
    private static final String JSON_KEY_QUERY = "query";
    private static final String JSON_KEY_VARIABLES = "variables";
    private static final Mapper MAPPER = new MapperBuilder().build();

    private static boolean isJsonContentType(SlingHttpServletRequest request) {
        final String contentType = request.getContentType();
        if(MIME_TYPE_JSON.equals(contentType)) {
            return true;
        } else if(contentType != null) {
            final String [] parts = contentType.split(";");
            return MIME_TYPE_JSON.equals(parts[0].trim());
        } else {
            return false;
        }
    }

    @Nullable
    public static Result fromRequest(@NotNull SlingHttpServletRequest request) throws IOException {
        String query = null;
        Map<String, Object> variables = null;
        if (request.getMethod().equalsIgnoreCase("POST") && isJsonContentType(request)) {
            try (JsonReader reader = Json.createReader(request.getReader())) {
                JsonObject input = reader.readObject();
                query = input.getString(JSON_KEY_QUERY);
                query = query.replace("\\n", "\n");
                if (input.containsKey(JSON_KEY_VARIABLES)) {
                    variables = MAPPER.readObject(input.get(JSON_KEY_VARIABLES), Map.class);
                }
            }
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
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            JsonObject jsonInput = reader.readObject();
            String query = jsonInput.getString(JSON_KEY_QUERY);
            if (query != null) {
                Map<String, Object> variables = null;
                if (jsonInput.containsKey(JSON_KEY_VARIABLES)) {
                     variables= MAPPER.readObject(jsonInput.get(JSON_KEY_VARIABLES), Map.class);
                } else {
                    variables = Collections.emptyMap();
                }
                return new Result(query, variables);
            }
            throw new IOException("The provided JSON structure does not contain a query.");
        }
    }
}
