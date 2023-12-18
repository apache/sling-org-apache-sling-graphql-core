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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import javax.script.ScriptException;

import graphql.GraphQLContext;
import graphql.parser.ParserOptions;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.apache.sling.graphql.api.engine.QueryExecutor;
import org.apache.sling.graphql.api.engine.ValidationResult;
import org.apache.sling.graphql.core.directives.Directives;
import org.apache.sling.graphql.core.hash.SHA256Hasher;
import org.apache.sling.graphql.core.scalars.SlingScalarsProvider;
import org.apache.sling.graphql.core.schema.RankedSchemaProviders;
import org.apache.sling.graphql.core.util.LogSanitizer;
import org.apache.sling.graphql.core.util.SlingGraphQLErrorHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SourceLocation;
import graphql.language.StringValue;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

@Component(
        service = QueryExecutor.class
)
@Designate(ocd = DefaultQueryExecutor.Config.class)
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

    public static final String CONNECTION_FOR = "for";
    public static final String CONNECTION_FETCHER = "fetcher";
    public static final String TYPE_STRING = "String";
    public static final String TYPE_BOOLEAN = "Boolean";
    public static final String TYPE_PAGE_INFO = "PageInfo";

    private static final LogSanitizer cleanLog = new LogSanitizer();

    private Map<String, String> resourceToHashMap;
    private Map<String, TypeDefinitionRegistry> hashToSchemaMap;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    private final SchemaGenerator schemaGenerator = new SchemaGenerator();

    private int queryMaxTokens;

    private int queryMaxWhitespaceTokens;

    @Reference
    private RankedSchemaProviders schemaProvider;

    @Reference
    private SlingDataFetcherSelector dataFetcherSelector;

    @Reference
    private SlingTypeResolverSelector typeResolverSelector;

    @Reference
    private SlingScalarsProvider scalarsProvider;

    @ObjectClassDefinition(
            name = "Apache Sling Default GraphQL Query Executor"
    )
    @interface Config {
        @AttributeDefinition(
                name = "Schema Cache Size",
                description = "The number of compiled GraphQL schemas to cache. Since a schema normally doesn't change often, they can be" +
                        " cached and reused, rather than parsed by the engine all the time. The cache is a LRU and will store up to this number of schemas."
        )
        int schemaCacheSize() default 128;

        @AttributeDefinition(
                name = "Query Max Tokens",
                description = "The number of GraphQL query tokens to parse. This is a safety measure to avoid denial of service attacks."
        )
        int queryMaxTokens() default 15000;

        @AttributeDefinition(
                name = "Query Max Whitespace Tokens",
                description = "The number of GraphQL query whitespace tokens to parse. This is a safety measure to avoid denial of service attacks."
        )
        int queryMaxWhitespaceTokens() default 200000;
    }

    private class ExecutionContext {
        final GraphQLSchema schema;
        final ExecutionInput input;

        ExecutionContext(@NotNull String query, @NotNull Map<String, Object> variables, @NotNull Resource queryResource, @NotNull String[] selectors) 
        throws ScriptException {
            final String schemaSdl = prepareSchemaDefinition(schemaProvider, queryResource, selectors);
            if (schemaSdl == null) {
                throw new SlingGraphQLException(String.format("Cannot get a schema for resource %s and selectors %s.", queryResource,
                        Arrays.toString(selectors)));
            }
            LOGGER.debug("Resource {} maps to GQL schema {}", queryResource.getPath(), schemaSdl);
            final TypeDefinitionRegistry typeDefinitionRegistry = getTypeDefinitionRegistry(schemaSdl, queryResource, selectors);
            schema = buildSchema(typeDefinitionRegistry, queryResource);
            input = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .graphQLContext(getGraphQLContextBuilder())
                    .build();

        }
    }

    @Activate
    public void activate(Config config) {
        int schemaCacheSize = config.schemaCacheSize();
        if (schemaCacheSize < 0) {
            schemaCacheSize = 0;
        }
        queryMaxTokens = config.queryMaxTokens();
        queryMaxWhitespaceTokens = config.queryMaxWhitespaceTokens();

        resourceToHashMap = new LRUCache<>(schemaCacheSize);
        hashToSchemaMap = new LRUCache<>(schemaCacheSize);
    }

    @Override
    public ValidationResult validate(@NotNull String query, @NotNull Map<String, Object> variables, @NotNull Resource queryResource,
                                     @NotNull String[] selectors) {
        try {
            final ExecutionContext ctx = new ExecutionContext(query, variables, queryResource, selectors);
            ParseAndValidateResult parseAndValidateResult = ParseAndValidate.parseAndValidate(ctx.schema, ctx.input);
            if (!parseAndValidateResult.isFailure()) {
                return DefaultValidationResult.Builder.newBuilder().withValidFlag(true).build();
            }
            DefaultValidationResult.Builder validationResultBuilder = DefaultValidationResult.Builder.newBuilder().withValidFlag(false);
            for (GraphQLError error : parseAndValidateResult.getErrors()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Error: type=").append(error.getErrorType().toString()).append("; ");
                sb.append("message=").append(error.getMessage()).append("; ");
                for (SourceLocation location : error.getLocations()) {
                    sb.append("location=").append(location.getLine()).append(",").append(location.getColumn()).append(";");
                }
                validationResultBuilder.withErrorMessage(sb.toString());
            }
            return validationResultBuilder.build();
        } catch (Exception e) {
            return DefaultValidationResult.Builder.newBuilder().withValidFlag(false).withErrorMessage(e.getMessage()).build();
        }
    }

    @Override
    public @NotNull Map<String, Object> execute(@NotNull String query, @NotNull Map<String, Object> variables,
                                                @NotNull Resource queryResource, @NotNull String[] selectors) {
        try {
            final ExecutionContext ctx = new ExecutionContext(query, variables, queryResource, selectors);
            final GraphQL graphQL = GraphQL.newGraphQL(ctx.schema).build();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executing query\n[{}]\nat [{}] with variables [{}]",
                        cleanLog.sanitize(query), queryResource.getPath(), cleanLog.sanitize(variables.toString()));
            }
            final ExecutionResult result = graphQL.execute(ctx.input);
            if (!result.getErrors().isEmpty()) {
                StringBuilder errors = new StringBuilder();
                for (GraphQLError error : result.getErrors()) {
                    errors.append("Error: type=").append(error.getErrorType().toString()).append("; message=").append(error.getMessage())
                            .append(System.lineSeparator());
                    if (error.getLocations() != null) {
                        for (SourceLocation location : error.getLocations()) {
                            errors.append("location=").append(location.getLine()).append(",").append(location.getColumn()).append(";");
                        }
                    }
                }
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("Query failed for Resource {}: query={} Errors:{}, selectors={}",
                        queryResource.getPath(), cleanLog.sanitize(query), errors, Arrays.toString(selectors));
                }
            }
            LOGGER.debug("ExecutionResult.isDataPresent={}", result.isDataPresent());
            return result.toSpecification();
        } catch (Exception e) {
            final String message = String.format("Query failed for Resource %s: query=%s, selectors=%s",
                queryResource.getPath(), cleanLog.sanitize(query), Arrays.toString(selectors));
            LOGGER.error(message, e);
            return SlingGraphQLErrorHelper.toSpecification(message, e);
        }
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
                        throw e;
                    } catch (Exception e) {
                        throw new SlingGraphQLException("Exception while building wiring.", e);
                    }
                }
                handleConnectionTypes(type, typeRegistry);
                return typeWiring;
            });
        }
        scalars.forEach(builder::scalar);
        List<UnionTypeDefinition> unionTypes = typeRegistry.getTypes(UnionTypeDefinition.class);
        for (UnionTypeDefinition type : unionTypes) {
            wireTypeResolver(builder, type, r);
        }
        List<InterfaceTypeDefinition> interfaceTypes = typeRegistry.getTypes(InterfaceTypeDefinition.class);
        for (InterfaceTypeDefinition type : interfaceTypes) {
            wireTypeResolver(builder, type, r);
        }
        return builder.build();
    }

    private <T extends TypeDefinition<T>> void wireTypeResolver(RuntimeWiring.Builder builder, TypeDefinition<T> type, Resource r) {
        try {
            TypeResolver resolver = getTypeResolver(type, r);
            if (resolver != null) {
                builder.type(type.getName(), typeWriting -> typeWriting.typeResolver(resolver));
            }
        } catch (SlingGraphQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SlingGraphQLException("Exception while building wiring.", e);
        }
    }

    private String getDirectiveArgumentValue(Directive d, String name) {
        final Argument a = d.getArgument(name);
        if (a != null && a.getValue() instanceof StringValue) {
            return ((StringValue) a.getValue()).getValue();
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

    private DataFetcher<Object> getDataFetcher(FieldDefinition field, Resource currentResource) {
        DataFetcher<Object> result = null;
        final Directive d = field.getDirectives().stream().filter( i -> FETCHER_DIRECTIVE.equals(i.getName())).findFirst().orElse(null);
        if (d != null) {
            final String name = validateFetcherName(getDirectiveArgumentValue(d, FETCHER_NAME));
            final String options = getDirectiveArgumentValue(d, FETCHER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, FETCHER_SOURCE);
            SlingDataFetcher<Object> f = dataFetcherSelector.getSlingFetcher(name);
            if (f != null) {
                result = new SlingDataFetcherWrapper<>(f, currentResource, options, source);
            }
        }
        return result;
    }

    private <T extends TypeDefinition<T>> TypeResolver getTypeResolver(TypeDefinition<T> typeDefinition, Resource currentResource) {
        TypeResolver resolver = null;
        final Directive d = typeDefinition.getDirectives().stream().filter( i -> RESOLVER_DIRECTIVE.equals(i.getName())).findFirst().orElse(null);
        if (d != null) {
            final String name = validateResolverName(getDirectiveArgumentValue(d, RESOLVER_NAME));
            final String options = getDirectiveArgumentValue(d, RESOLVER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, RESOLVER_SOURCE);
            SlingTypeResolver<Object> r = typeResolverSelector.getSlingTypeResolver(name);
            if (r != null) {
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

    TypeDefinitionRegistry getTypeDefinitionRegistry(@NotNull String sdl, @NotNull Resource currentResource, @NotNull String[] selectors) {
        TypeDefinitionRegistry typeRegistry = null;
        readLock.lock();
        String newHash = SHA256Hasher.getHash(sdl);
        /*
        Since the SchemaProviders that generate the SDL can dynamically change, but also since the resource is passed to the RuntimeWiring,
        there's a two stage cache:

        1. a mapping between the resource, selectors and the SDL's hash
        2. a mapping between the hash and the compiled GraphQL schema
         */
        String resourceToHashMapKey = getCacheKey(currentResource, selectors);
        String oldHash = resourceToHashMap.get(resourceToHashMapKey);
        if (!newHash.equals(oldHash)) {
            readLock.unlock();
            writeLock.lock();
            try {
                oldHash = resourceToHashMap.get(resourceToHashMapKey);
                if (!newHash.equals(oldHash)) {
                    typeRegistry = new SchemaParser().parse(sdl);
                    typeRegistry.add(Directives.CONNECTION);
                    typeRegistry.add(Directives.FETCHER);
                    typeRegistry.add(Directives.RESOLVER);
                    for (ObjectTypeDefinition typeDefinition : typeRegistry.getTypes(ObjectTypeDefinition.class)) {
                        handleConnectionTypes(typeDefinition, typeRegistry);
                    }
                    resourceToHashMap.put(resourceToHashMapKey, newHash);
                    hashToSchemaMap.put(newHash, typeRegistry);
                }
            } catch (Exception e) {
                LOGGER.error("Unable to generate a TypeRegistry.", e);
            } finally {
                readLock.lock();
                writeLock.unlock();
            }
        }
        try {
            /*
             * when the cache is disabled we need to return the registry directly, since it will be created for each request
             */
            if (typeRegistry != null) {
                return typeRegistry;
            }
            return hashToSchemaMap.get(newHash);
        } finally {
            readLock.unlock();
        }
    }

    private Consumer<GraphQLContext.Builder> getGraphQLContextBuilder() {
        ParserOptions defaultParserOptions = ParserOptions.getDefaultParserOptions();
        Consumer<GraphQLContext.Builder> graphQLContextBuilder = builder -> {
            builder.put(ParserOptions.class, ParserOptions.newParserOptions()
                    .captureIgnoredChars(defaultParserOptions.isCaptureIgnoredChars())
                    .captureSourceLocation(defaultParserOptions.isCaptureSourceLocation())
                    .captureLineComments(defaultParserOptions.isCaptureLineComments())
                    .readerTrackData(defaultParserOptions.isReaderTrackData())
                    .maxTokens(queryMaxTokens)
                    .maxWhitespaceTokens(queryMaxWhitespaceTokens)
                    .maxRuleDepth(defaultParserOptions.getMaxRuleDepth())
                    .build()
            );
        };

        return graphQLContextBuilder;
    }

    private GraphQLSchema buildSchema(@NotNull TypeDefinitionRegistry typeRegistry, @NotNull Resource currentResource) {
        Iterable<GraphQLScalarType> scalars = scalarsProvider.getCustomScalars(typeRegistry.scalars());
        RuntimeWiring runtimeWiring = buildWiring(typeRegistry, scalars, currentResource);
        return schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private String getCacheKey(@NotNull Resource resource, @NotNull String[] selectors) {
        return resource.getPath() + ":" + String.join(".", selectors);
    }

    private void handleConnectionTypes(ObjectTypeDefinition typeDefinition, TypeDefinitionRegistry typeRegistry) {
        for (FieldDefinition fieldDefinition : typeDefinition.getFieldDefinitions()) {
            Directive directive = fieldDefinition.getDirectives().stream().filter( i -> "connection".equals(i.getName())).findFirst().orElse(null);
            if (directive != null) {
                if (directive.getArgument(CONNECTION_FOR) != null) {
                    String forType = ((StringValue) directive.getArgument(CONNECTION_FOR).getValue()).getValue();
                    Optional<TypeDefinition> forTypeDefinition = typeRegistry.getType(forType);
                    if (!forTypeDefinition.isPresent()) {
                        throw new SlingGraphQLException("Type '" + forType + "' has not been defined.");
                    }
                    TypeDefinition<?> forOTD = forTypeDefinition.get();
                    ObjectTypeDefinition edge = ObjectTypeDefinition.newObjectTypeDefinition().name(forOTD.getName() + "Edge")
                            .fieldDefinition(new FieldDefinition("cursor", new TypeName(TYPE_STRING)))
                            .fieldDefinition(new FieldDefinition("node", new TypeName(forOTD.getName())))
                            .build();
                    ObjectTypeDefinition connection = ObjectTypeDefinition.newObjectTypeDefinition().name(forOTD.getName() +
                            "Connection")
                            .fieldDefinition(new FieldDefinition("edges", new ListType(new TypeName(forType + "Edge"))))
                            .fieldDefinition(new FieldDefinition("pageInfo", new TypeName(TYPE_PAGE_INFO)))
                            .build();
                    if (!typeRegistry.getType(TYPE_PAGE_INFO).isPresent()) {
                        ObjectTypeDefinition pageInfo = ObjectTypeDefinition.newObjectTypeDefinition().name(TYPE_PAGE_INFO)
                                .fieldDefinition(new FieldDefinition("hasPreviousPage", new NonNullType(new TypeName(TYPE_BOOLEAN))))
                                .fieldDefinition(new FieldDefinition("hasNextPage", new NonNullType(new TypeName(TYPE_BOOLEAN))))
                                .fieldDefinition(new FieldDefinition("startCursor", new TypeName(TYPE_STRING)))
                                .fieldDefinition(new FieldDefinition("endCursor", new TypeName(TYPE_STRING)))
                                .build();
                        typeRegistry.add(pageInfo);
                    }
                    typeRegistry.add(edge);
                    typeRegistry.add(connection);
                } else {
                    throw new SlingGraphQLException("The connection directive requires a 'for' argument.");
                }
            }
        }
    }

    private static class LRUCache<T> extends LinkedHashMap<String, T> {

        private final int capacity;

        public LRUCache(int capacity) {
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, T> eldest) {
            return size() > capacity;
        }

        @Override
        public int hashCode() {
            return super.hashCode() + Objects.hashCode(capacity);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof LRUCache) {
                LRUCache<T> other = (LRUCache<T>) o;
                return super.equals(o) && capacity == other.capacity;
            }
            return false;
        }
    }

}
