
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
import java.util.Arrays;
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
import org.apache.sling.commons.metrics.Counter;
import org.apache.sling.commons.metrics.MetricsService;
import org.apache.sling.commons.metrics.Timer;
import org.apache.sling.graphql.api.cache.GraphQLCacheProvider;
import org.apache.sling.graphql.core.engine.GraphQLResourceQuery;
import org.apache.sling.graphql.core.engine.SlingDataFetcherSelector;
import org.apache.sling.graphql.core.json.JsonSerializer;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.AttributeType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
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

        @AttributeDefinition(
                name = "Persisted queries suffix",
                description = "The request suffix under which the HTTP API for persisted queries should be made available."
        )
        String persistedQueries_suffix() default "/persisted";

        @AttributeDefinition(
                name = "Persisted Queries Cache-Control max-age",
                description = "The maximum amount of time a persisted query resource is considered fresh (in seconds). A negative value " +
                        "will be interpreted as 0.",
                min = "0",
                type = AttributeType.INTEGER
        )
        int cache$_$control_max$_$age() default 60;
    }

    @Reference
    private RankedSchemaProviders schemaProviders;

    @Reference
    private SlingDataFetcherSelector dataFetcherSelector;

    @Reference
    private SlingScalarsProvider scalarsProvider;

    @Reference
    private GraphQLCacheProvider cacheProvider;

    @Reference
    private MetricsService metricsService;

    @Reference(target = "(name=sling)")
    private MetricRegistry metricRegistry;

    private String suffixPersisted;
    private Pattern patternGetPersistedQuery;
    private int cacheControlMaxAge;
    private final JsonSerializer jsonSerializer = new JsonSerializer();

    private Counter cacheHits;
    private Counter cacheMisses;
    private Counter requestsServed;
    private Timer requestTimer;

    private static final String METRIC_NS = GraphQLServlet.class.getName();
    private String servletRegistrationProperties;
    private String gaugeCacheHitRate;

    @Activate
    private void activate(Config config) {
        String[] extensions = config.sling_servlet_extensions();
        StringBuilder extensionsPattern = new StringBuilder();
        for (String extension : extensions) {
            if (extensionsPattern.length() > 0) {
                extensionsPattern.append("|");
            }
            extensionsPattern.append(extension);
        }
        if (extensionsPattern.length() > 0) {
            extensionsPattern.insert(0, "(");
            extensionsPattern.append(")");
        }
        cacheControlMaxAge = config.cache$_$control_max$_$age() >= 0 ? config.cache$_$control_max$_$age() : 0;
        String suffix = config.persistedQueries_suffix();
        if (StringUtils.isNotEmpty(suffix) && suffix.startsWith("/")) {
            suffixPersisted = suffix;
            patternGetPersistedQuery = Pattern.compile("^" + suffixPersisted + "/([a-f0-9]{64})" + (extensionsPattern.length() > 0 ?
                    "\\." + extensionsPattern.toString()  + "$" : "$"));
        } else {
            suffixPersisted = null;
            patternGetPersistedQuery = null;
        }
        StringBuilder sb = new StringBuilder();
        String[] resourceTypes = config.sling_servlet_resourceTypes();
        Arrays.sort(resourceTypes);
        sb.append("rt:").append(String.join("_", resourceTypes));
        if (config.sling_servlet_methods().length > 0) {
            String[] methods = config.sling_servlet_methods();
            Arrays.sort(methods);
            sb.append(".m:").append(String.join("_", methods));
        }
        if (config.sling_servlet_selectors().length > 0) {
            String[] selectors = config.sling_servlet_selectors();
            Arrays.sort(selectors);
            sb.append(".s:").append(String.join("_", selectors));
        }
        if (extensions.length > 0) {
            Arrays.sort(extensions);
            sb.append(".e:").append(String.join("_", extensions));
        }
        servletRegistrationProperties = sb.toString();
        cacheHits = metricsService.counter(METRIC_NS + "." + servletRegistrationProperties + ".cache_hits");
        cacheMisses = metricsService.counter(METRIC_NS + "." + servletRegistrationProperties + ".cache_misses");
        requestsServed = metricsService.counter(METRIC_NS + "." + servletRegistrationProperties + ".requests_total");
        gaugeCacheHitRate = METRIC_NS + "." + servletRegistrationProperties + ".cache_hit_rate";
        metricRegistry.register(gaugeCacheHitRate,
                (Gauge<Float>) () -> (float) (cacheHits.getCount() / (float) (cacheHits.getCount() + cacheMisses.getCount())));
        requestTimer = metricsService.timer(METRIC_NS + "." + servletRegistrationProperties + ".requests_timer");
    }

    @Deactivate
    private void deactivate() {
        if (StringUtils.isNotEmpty(gaugeCacheHitRate)) {
            metricRegistry.remove(gaugeCacheHitRate);
        }
    }

    @Override
    public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        requestsServed.increment();
        Timer.Context requestTimerContext = requestTimer.time();
        try {
            String suffix = request.getRequestPathInfo().getSuffix();
            if (suffix != null) {
                if (StringUtils.isNotEmpty(suffixPersisted) && suffix.startsWith(suffixPersisted)) {
                    Matcher matcher = patternGetPersistedQuery.matcher(suffix);
                    if (matcher.matches()) {
                        String queryHash = matcher.group(1);
                        String extension = matcher.group(2);
                        String requestExtension = request.getRequestPathInfo().getExtension();
                        if (requestExtension != null && requestExtension.equals(extension)) {
                            if (StringUtils.isNotEmpty(queryHash)) {
                                String query = cacheProvider.getQuery(queryHash, request.getResource().getResourceType(),
                                        request.getRequestPathInfo().getSelectorString());
                                if (query != null) {
                                    boolean isAuthenticated = request.getHeaders("Authorization").hasMoreElements();
                                    StringBuilder cacheControlValue = new StringBuilder("max-age=").append(cacheControlMaxAge);
                                    if (isAuthenticated) {
                                        cacheControlValue.append(",private");
                                    }
                                    response.addHeader("Cache-Control", cacheControlValue.toString());
                                    execute(query, request, response);
                                    cacheHits.increment();
                                } else {
                                    cacheMisses.increment();
                                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Cannot find persisted query " + queryHash);
                                }
                            }
                        } else {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The persisted query's extension does not match the " +
                                    "servlet extension.");
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unexpected hash.");
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Persisted queries are disabled.");
                }
            } else {
                execute(request.getResource(), request, response);
            }
        } finally {
            requestTimerContext.stop();
        }
    }

    @Override
    public void doPost(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response) throws IOException {
        requestsServed.increment();
        Timer.Context requestTimerContext = requestTimer.time();
        try {
            String suffix = request.getRequestPathInfo().getSuffix();
            if (suffix != null) {
                if (StringUtils.isNotEmpty(suffixPersisted) && suffix.equals(suffixPersisted)) {
                    doPostPersistedQuery(request, response);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                }
            } else {
                execute(request.getResource(), request, response);
            }
        } finally {
            requestTimerContext.stop();
        }
    }

    private void doPostPersistedQuery(@NotNull SlingHttpServletRequest request, @NotNull SlingHttpServletResponse response)
            throws IOException {
        String rawQuery = IOUtils.toString(request.getReader());
        QueryParser.Result query = QueryParser.fromJSON(rawQuery);
        if (GraphQLResourceQuery.isQueryValid(schemaProviders, dataFetcherSelector, scalarsProvider, request.getResource(),
                request.getRequestPathInfo().getSelectors(), query.getQuery(), query.getVariables())) {

            String hash = cacheProvider.cacheQuery(rawQuery, request.getResource().getResourceType(),
                    request.getRequestPathInfo().getSelectorString());
            if (hash != null) {
                response.addHeader("Location", getLocationHeaderValue(request, hash));
                response.setStatus(HttpServletResponse.SC_CREATED);
            } else {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Cannot store persisted query.");
            }
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid GraphQL query.");
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
            final ExecutionResult executionResult = GraphQLResourceQuery.executeQuery(schemaProviders, dataFetcherSelector, scalarsProvider,
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
            final ExecutionResult executionResult = GraphQLResourceQuery.executeQuery(schemaProviders, dataFetcherSelector, scalarsProvider,
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
        int localPort = request.getServerPort();
        if (localPort != 80 && localPort != 443) {
            location.append(":").append(localPort);
        }
        String extension = request.getRequestPathInfo().getExtension();
        location.append(request.getContextPath()).append(request.getPathInfo()).append("/").append(hash)
                .append(StringUtils.isNotEmpty(extension) ? "." + extension : "");
        return location.toString();
    }


}
