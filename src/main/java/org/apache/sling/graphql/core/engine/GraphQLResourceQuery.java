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

package org.apache.sling.graphql.core.engine;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.script.ScriptException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidate;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.StringValue;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Run a GraphQL query in the context of a Sling Resource
 */
public class GraphQLResourceQuery {

    public static final String FETCHER_DIRECTIVE = "fetcher";
    public static final String FETCHER_NAME = "name";
    public static final String FETCHER_OPTIONS = "options";
    public static final String FETCHER_SOURCE = "source";
    private static final Pattern FETCHER_NAME_PATTERN = Pattern.compile("\\w+(/\\w+)+");

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLResourceQuery.class);

    private GraphQLResourceQuery() {}

    public static ExecutionResult executeQuery(@NotNull SchemaProvider schemaProvider,
                                               @NotNull SlingDataFetcherSelector fetchersSelector,
                                               @NotNull SlingScalarsProvider scalarsProvider,
                                               @NotNull Resource r,
                                               @NotNull String[] requestSelectors,
                                               @NotNull String query, @NotNull Map<String, Object> variables) throws ScriptException {
        String schemaDef = null;
        try {
            schemaDef = prepareSchemaDefinition(schemaProvider, r, requestSelectors);
            LOGGER.debug("Resource {} maps to GQL schema {}", r.getPath(), schemaDef);
            final GraphQLSchema schema = buildSchema(schemaDef, fetchersSelector, scalarsProvider, r);
            final GraphQL graphQL = GraphQL.newGraphQL(schema).build();
            LOGGER.debug("Executing query\n[{}]\nat [{}] with variables [{}]", query, r.getPath(), variables);
            ExecutionInput ei = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();
            final ExecutionResult result = graphQL.execute(ei);
            LOGGER.debug("ExecutionResult.isDataPresent={}", result.isDataPresent());
            return result;
        } catch (ScriptException e) {
            throw e;
        } catch(Exception e) {
            final ScriptException up = new ScriptException(
                String.format("Query failed for Resource %s: schema=%s, query=%s", r.getPath(), schemaDef, query));
            up.initCause(e);
            LOGGER.info("GraphQL Query Exception", up);
            throw up;                
        }
    }

    public static boolean isQueryValid(@NotNull SchemaProvider schemaProvider,
                                       @NotNull SlingDataFetcherSelector fetchersSelector,
                                       @NotNull SlingScalarsProvider scalarsProvider,
                                       @NotNull Resource r,
                                       @NotNull String[] requestSelectors,
                                       @NotNull String query, Map<String, Object> variables) {

        try {
            String schemaDef = prepareSchemaDefinition(schemaProvider, r, requestSelectors);
            LOGGER.debug("Resource {} maps to GQL schema {}", r.getPath(), schemaDef);
            final GraphQLSchema schema =
                    buildSchema(schemaDef, fetchersSelector, scalarsProvider, r);
            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();
            return !ParseAndValidate.parseAndValidate(schema, executionInput).isFailure();
        } catch (Exception e) {
            LOGGER.error(String.format("Invalid query: %s.", query), e);
            return false;
        }
    }

    private static GraphQLSchema buildSchema(String sdl, SlingDataFetcherSelector fetchers, SlingScalarsProvider scalarsProvider,
                                             Resource currentResource) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        Iterable<GraphQLScalarType> scalars = scalarsProvider.getCustomScalars(typeRegistry.scalars());
        RuntimeWiring runtimeWiring = buildWiring(typeRegistry, fetchers, scalars, currentResource);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private static RuntimeWiring buildWiring(TypeDefinitionRegistry typeRegistry, SlingDataFetcherSelector fetchers,
                                       Iterable<GraphQLScalarType> scalars, Resource r) {
        List<ObjectTypeDefinition> types = typeRegistry.getTypes(ObjectTypeDefinition.class);
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        for (ObjectTypeDefinition type : types) {

            builder.type(type.getName(), typeWiring -> {
                for (FieldDefinition field : type.getFieldDefinitions()) {
                    try {
                        DataFetcher<Object> fetcher = getDataFetcher(field, fetchers, r);
                        if (fetcher != null) {
                            typeWiring.dataFetcher(field.getName(), fetcher);
                        }
                    } catch(IOException e) {
                        throw new RuntimeException("Exception while building wiring", e);
                    }
                }
                return typeWiring;
            });
        }
        scalars.forEach(builder::scalar);
        return builder.build();
    }

    private static String getDirectiveArgumentValue(Directive d, String name) {
        final Argument a = d.getArgument(name);
        if(a != null && a.getValue() instanceof StringValue) {
            return ((StringValue)a.getValue()).getValue();
        }
        return null;
    }

    static String validateFetcherName(String name) throws IOException {
        if(name == null) {
            throw new IOException(FETCHER_NAME + " cannot be null");
        }
        if(!FETCHER_NAME_PATTERN.matcher(name).matches()) {
            throw new IOException(String.format("Invalid fetcher name %s, does not match %s", 
                name, FETCHER_NAME_PATTERN));
        }
        return name;
    }

    private static DataFetcher<Object> getDataFetcher(FieldDefinition field,
        SlingDataFetcherSelector fetchers, Resource currentResource) throws IOException {
        DataFetcher<Object> result = null;
        final Directive d =field.getDirective(FETCHER_DIRECTIVE);
        if(d != null) {
            final String name = validateFetcherName(getDirectiveArgumentValue(d, FETCHER_NAME));
            final String options = getDirectiveArgumentValue(d, FETCHER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, FETCHER_SOURCE);
            SlingDataFetcher<Object> f = fetchers.getSlingFetcher(name);
            if(f != null) {
                result = new SlingDataFetcherWrapper<>(f, currentResource, options, source);
            }
        }
        return result;
    }

    private static @Nullable String prepareSchemaDefinition(@NotNull SchemaProvider schemaProvider,
                                                            @NotNull Resource resource,
                                                            @NotNull String[] selectors) throws ScriptException {
        try {
            return schemaProvider.getSchema(resource, selectors);
        } catch (Exception e) {
            final ScriptException up = new ScriptException("Schema provider failed");
            up.initCause(e);
            LOGGER.info("Schema provider Exception", up);
            throw up;
        }
    }
}
