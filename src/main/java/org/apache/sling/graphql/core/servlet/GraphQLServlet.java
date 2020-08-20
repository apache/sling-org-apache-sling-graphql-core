
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.graphql.api.cache.GraphQLCacheProvider;
import org.apache.sling.graphql.core.engine.GraphQLResourceQuery;
import org.apache.sling.graphql.core.engine.SlingDataFetcherSelector;
import org.apache.sling.graphql.core.json.JsonSerializer;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import graphql.ExecutionResult;

/** Servlet that can be activated to implement the standard
 *  GraphQL "protocol" as per https://graphql.org/learn/serving-over-http/
 * 
 *  This servlet is only active if the corresponding OSGi configurations
 *  are created. This allows is to be mounted either on a path to support
 *  the "traditional" GraphQL single-endpoint mode, or on specific resource
 *  types and selectors to turn specific Sling Resources into GraphQL 
 *  endpoints.
 */

@Component(
    service = Servlet.class,
    name = "org.apache.sling.graphql.core.GraphQLServlet",
    immediate = true,
    configurationPolicy=ConfigurationPolicy.REQUIRE,
    property = {
        "service.description=Sling GraphQL Servlet",
        "service.vendor=The Apache Software Foundation"
    })
@Designate(ocd = GraphQLServlet.Config.class, factory=true)
public class GraphQLServlet extends SlingAllMethodsServlet {
    private static final long serialVersionUID = 1L;

    public static final String P_QUERY = "query";

    private static final String SUFFIX_PERSISTED = "/persisted";
    private static final Pattern PATTERN_GET_PERSISTED_QUERY = Pattern.compile("^" + SUFFIX_PERSISTED + "/([a-f0-9]{64})$");

    @ObjectClassDefinition(
        name = "Apache Sling GraphQL Servlet",
        description = "Servlet that implements GraphQL endpoints")
    public @interface Config {
        @AttributeDefinition(
            name = "Selectors",
            description="Standard Sling servlet property")
        String[] sling_servlet_selectors() default "";

        @AttributeDefinition(
            name = "Resource Types",
            description="Standard Sling servlet property")
        String[] sling_servlet_resourceTypes() default "sling/servlet/default";

        @AttributeDefinition(
            name = "Methods",
            description="Standard Sling servlet property")
        String[] sling_servlet_methods() default "GET";

        @AttributeDefinition(
            name = "Extensions",
            description="Standard Sling servlet property")
        String[] sling_servlet_extensions() default "gql";
    }

    @Reference
    private RankedSchemaProviders schemaProviders;

    @Reference
    private SlingDataFetcherSelector dataFetcherSelector;

    @Reference
    private SlingScalarsProvider scalarsProvider;

    @Reference(
            cardinality = ReferenceCardinality.OPTIONAL,
            policy = ReferencePolicy.STATIC,
            policyOption = ReferencePolicyOption.GREEDY
    )
    private GraphQLCacheProvider cacheProvider;

    private final JsonSerializer jsonSerializer = new JsonSerializer();

    @Override
    public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String suffix = request.getRequestPathInfo().getSuffix();
        if (suffix != null && suffix.startsWith(SUFFIX_PERSISTED)) {
            if (cacheProvider == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "This servlet does not support persisted queries.");
                return;
            }
            Matcher matcher = PATTERN_GET_PERSISTED_QUERY.matcher(suffix);
            if (matcher.matches()) {
                String queryHash = matcher.group(1);
                if (StringUtils.isNotEmpty(queryHash)) {
                    String query = cacheProvider.getQuery(queryHash, request.getResource().getResourceType(),
                            request.getRequestPathInfo().getSelectorString());
                    if (query != null) {
                        execute(query, request, response);
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot find persisted query " + queryHash);
                    }
                }
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            execute(request.getResource(), request, response);
        }
    }

    @Override
    public void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String suffix = request.getRequestPathInfo().getSuffix();
        if (suffix != null && suffix.equals(SUFFIX_PERSISTED)) {
            if (cacheProvider == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "This servlet does not support persisted queries.");
                return;
            }
            String query = IOUtils.toString(request.getInputStream(), StandardCharsets.UTF_8);
            String hash = cacheProvider.cacheQuery(query, request.getResource().getResourceType(),
                    request.getRequestPathInfo().getSelectorString());
            response.addHeader("Location", getLocationHeaderValue(request, hash));
            response.setStatus(HttpServletResponse.SC_CREATED);
        } else {
            execute(request.getResource(), request, response);
        }
    }

    private void execute(Resource resource, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        final QueryParser.Result result = QueryParser.fromRequest(request);
        if (result == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        final String query = result.getQuery();
        if (query.trim().length() == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing request parameter:" + P_QUERY);
            return;
        }

        try {
            final GraphQLResourceQuery q = new GraphQLResourceQuery();
            final ExecutionResult executionResult = q.executeQuery(schemaProviders, dataFetcherSelector, scalarsProvider,
                resource, request.getRequestPathInfo().getSelectors(), query, result.getVariables());
            jsonSerializer.sendJSON(response.getWriter(), executionResult);
        } catch(Exception ex) {
            throw new IOException(ex);
        }
    }

    private void execute(@NotNull String persistedQuery, SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            final QueryParser.Result result = QueryParser.fromJSON(persistedQuery);
            final GraphQLResourceQuery q = new GraphQLResourceQuery();
            final ExecutionResult executionResult = q.executeQuery(schemaProviders, dataFetcherSelector, scalarsProvider,
                    request.getResource(), request.getRequestPathInfo().getSelectors(), result.getQuery(), result.getVariables());
            jsonSerializer.sendJSON(response.getWriter(), executionResult);
        } catch(Exception ex) {
            throw new IOException(ex);
        }
    }

    @NotNull
    private String getLocationHeaderValue(@NotNull SlingHttpServletRequest request, @NotNull String hash) {
        StringBuilder location = new StringBuilder();
        location.append(request.getScheme()).append("://");
        location.append(request.getServerName());
        int localPort = request.getLocalPort();
        if (localPort != 80 && localPort != 443) {
            location.append(":").append(localPort);
        }
        location.append(request.getContextPath()).append(request.getPathInfo()).append("/").append(hash);
        return location.toString();
    }


}
