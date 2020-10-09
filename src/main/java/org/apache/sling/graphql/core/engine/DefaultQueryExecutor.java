/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.graphql.core.engine;

import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.script.ScriptException;

import graphql.language.UnionTypeDefinition;
import graphql.schema.TypeResolver;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
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

@Component(
        service = QueryExecutor.class
)
public class DefaultQueryExecutor implements QueryExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultQueryExecutor.class);

    public static final String FETCHER_DIRECTIVE = "fetcher";
    public static final String FETCHER_NAME = "name";
    public static final String FETCHER_OPTIONS = "options";
    public static final String FETCHER_SOURCE = "source";

    public static final String RESOLVER_DIRECTIVE = "resolver";
    public static final String RESOLVER_NAME = "name";
    public static final String RESOLVER_OPTIONS = "options";
    public static final String RESOLVER_SOURCE = "source";

    @Reference
    private RankedSchemaProviders schemaProvider;

    @Reference
    private SlingDataFetcherSelector dataFetcherSelector;

    @Reference
    private SlingTypeResolverSelector typeResolverSelector;

    @Reference
    private SlingScalarsProvider scalarsProvider;

    @Override
    public boolean isValid(@NotNull String query, @NotNull Map<String, Object> variables, @NotNull Resource queryResource,
                           @NotNull String[] selectors) {
        try {
            String schemaDef = prepareSchemaDefinition(schemaProvider, queryResource, selectors);
            LOGGER.debug("Resource {} maps to GQL schema {}", queryResource.getPath(), schemaDef);
            final GraphQLSchema schema = buildSchema(schemaDef, queryResource);
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

    @Override
    public @NotNull JsonObject execute(@NotNull String query, @NotNull Map<String, Object> variables, @NotNull Resource queryResource,
                                       @NotNull String[] selectors) {
        String schemaDef = null;
        try {
            schemaDef = prepareSchemaDefinition(schemaProvider, queryResource, selectors);
            LOGGER.debug("Resource {} maps to GQL schema {}", queryResource.getPath(), schemaDef);
            final GraphQLSchema schema = buildSchema(schemaDef, queryResource);
            final GraphQL graphQL = GraphQL.newGraphQL(schema).build();
            LOGGER.debug("Executing query\n[{}]\nat [{}] with variables [{}]", query, queryResource.getPath(), variables);
            ExecutionInput ei = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();
            final ExecutionResult result = graphQL.execute(ei);
            if (!result.getErrors().isEmpty()) {
                StringBuilder errors = new StringBuilder();
                for (GraphQLError error : result.getErrors()) {
                    errors.append("Error type: ").append(error.getErrorType().toString()).append("; error message: ").append(error.getMessage()).append(System.lineSeparator());
                }
                throw new SlingGraphQLException(String.format("Query failed for Resource %s: schema=%s, query=%s%nErrors:%n%s",
                        queryResource.getPath(), schemaDef, query, errors.toString()));
            }
            LOGGER.debug("ExecutionResult.isDataPresent={}", result.isDataPresent());
            Map<String, Object> resultAsMap = result.toSpecification();
            return Json.createObjectBuilder(resultAsMap).build().asJsonObject();
        } catch (SlingGraphQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SlingGraphQLException(
                    String.format("Query failed for Resource %s: schema=%s, query=%s", queryResource.getPath(), schemaDef, query), e);
        }
    }

    private GraphQLSchema buildSchema(String sdl, Resource currentResource) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        Iterable<GraphQLScalarType> scalars = scalarsProvider.getCustomScalars(typeRegistry.scalars());
        RuntimeWiring runtimeWiring = buildWiring(typeRegistry, scalars, currentResource);
        SchemaGenerator schemaGenerator = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private RuntimeWiring buildWiring(TypeDefinitionRegistry typeRegistry, Iterable<GraphQLScalarType> scalars, Resource r) {
        List<ObjectTypeDefinition> types = typeRegistry.getTypes(ObjectTypeDefinition.class);
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        for (ObjectTypeDefinition type : types) {
            builder.type(type.getName(), typeWiring -> {
                for (FieldDefinition field : type.getFieldDefinitions()) {
                    try {
                        DataFetcher<Object> fetcher = getDataFetcher(field, r);
                        if (fetcher != null) {
                            typeWiring.dataFetcher(field.getName(), fetcher);
                        }
                    } catch (SlingGraphQLException e) {
                        throw  e;
                    } catch (Exception e) {
                        throw new SlingGraphQLException("Exception while building wiring.", e);
                    }
                }
                return typeWiring;
            });
        }
        scalars.forEach(builder::scalar);

        List<UnionTypeDefinition> unionTypes = typeRegistry.getTypes(UnionTypeDefinition.class);
        for (UnionTypeDefinition type : unionTypes) {
            try {
                TypeResolver resolver = getTypeResolver(type, r);
                if (resolver != null) {
                    builder.type(type.getName(), typeWriting -> typeWriting.typeResolver(resolver));
                }
            } catch (SlingGraphQLException e) {
                throw e;
            } catch(Exception e) {
                throw new SlingGraphQLException("Exception while building wiring.", e);
            }
        }
        return builder.build();
    }

    private String getDirectiveArgumentValue(Directive d, String name) {
        final Argument a = d.getArgument(name);
        if(a != null && a.getValue() instanceof StringValue) {
            return ((StringValue)a.getValue()).getValue();
        }
        return null;
    }

    private @NotNull String validateFetcherName(String name) {
        if (SlingDataFetcherSelector.nameMatchesPattern(name)) {
            return name;
        }
        throw new SlingGraphQLException(String.format("Invalid fetcher name %s, does not match %s",
                name, SlingDataFetcherSelector.FETCHER_NAME_PATTERN));
    }

    private @NotNull String validateResolverName(String name) {
        if (SlingTypeResolverSelector.nameMatchesPattern(name)) {
            return name;
        }
        throw new SlingGraphQLException(String.format("Invalid type resolver name %s, does not match %s",
                name, SlingTypeResolverSelector.RESOLVER_NAME_PATTERN));
    }

    private DataFetcher<Object> getDataFetcher(FieldDefinition field, Resource currentResource)
            {
        DataFetcher<Object> result = null;
        final Directive d =field.getDirective(FETCHER_DIRECTIVE);
        if(d != null) {
            final String name = validateFetcherName(getDirectiveArgumentValue(d, FETCHER_NAME));
            final String options = getDirectiveArgumentValue(d, FETCHER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, FETCHER_SOURCE);
            SlingDataFetcher<Object> f = dataFetcherSelector.getSlingFetcher(name);
            if(f != null) {
                result = new SlingDataFetcherWrapper<>(f, currentResource, options, source);
            }
        }
        return result;
    }

    private TypeResolver getTypeResolver(UnionTypeDefinition typeDefinition, Resource currentResource) {
        TypeResolver resolver = null;
        final Directive d = typeDefinition.getDirective(RESOLVER_DIRECTIVE);
        if(d != null) {
            final String name = validateResolverName(getDirectiveArgumentValue(d, RESOLVER_NAME));
            final String options = getDirectiveArgumentValue(d, RESOLVER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, RESOLVER_SOURCE);
            SlingTypeResolver<Object> r = typeResolverSelector.getSlingTypeResolver(name);
            if(r != null) {
                resolver = new SlingTypeResolverWrapper(r, currentResource, options, source);
            }
        }
        return resolver;
    }

    private @Nullable String prepareSchemaDefinition(@NotNull SchemaProvider schemaProvider,
                                                            @NotNull org.apache.sling.api.resource.Resource resource,
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
