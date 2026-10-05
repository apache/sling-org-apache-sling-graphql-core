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

import javax.script.ScriptException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLContext;
import graphql.GraphQLError;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.execution.values.InputInterceptor;
import graphql.execution.values.legacycoercing.LegacyCoercingInputInterceptor;
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
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.parser.ParserOptions;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.graphql.api.SchemaProvider;
import org.apache.sling.graphql.api.SlingGraphQLException;
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

@Component(service = QueryExecutor.class)
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
    private Map<String, BuiltExecutableSchema> hashToExecutableSchemaMap;
    private final ConcurrentHashMap<String, CompletableFuture<BuiltExecutableSchema>> executableSchemaInFlight =
            new ConcurrentHashMap<>();
    private final Object executableSchemaCacheLock = new Object();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock readLock = readWriteLock.readLock();
    private final Lock writeLock = readWriteLock.writeLock();
    private final SchemaGenerator schemaGenerator = new SchemaGenerator();

    private int maxQueryTokens;

    private int maxWhitespaceTokens;

    private volatile boolean executableSchemaCacheEnabled;

    /** Last scalar generation for which stale executable-schema entries were purged. */
    private volatile long lastPurgedScalarGeneration;

    @Reference
    private RankedSchemaProviders schemaProvider;

    @Reference
    private SlingDataFetcherSelector dataFetcherSelector;

    @Reference
    private SlingTypeResolverSelector typeResolverSelector;

    @Reference
    private SlingScalarsProvider scalarsProvider;

    @ObjectClassDefinition(name = "Apache Sling Default GraphQL Query Executor")
    @interface Config {
        @AttributeDefinition(
                name = "Schema Cache Size",
                description =
                        "The number of compiled GraphQL TypeDefinitionRegistry instances to cache. Since a schema normally doesn't"
                                + " change often, they can be cached and reused, rather than parsed by the engine all the time. Eviction is"
                                + " insertion-order (FIFO). Set to 0 to disable this cache.")
        int schemaCacheSize() default 128;

        @AttributeDefinition(
                name = "Executable Schema Cache Size",
                description =
                        "The number of fully built GraphQLSchema instances to cache when the executable schema cache is enabled."
                                + " Each entry is substantially larger than a TypeDefinitionRegistry, so this can be sized independently of"
                                + " Schema Cache Size. The cache is a LRU. Set to 0 to disable the executable cache even if it is enabled"
                                + " below.")
        int executableSchemaCacheSize() default 32;

        @AttributeDefinition(
                name = "Enable Executable Schema Cache",
                description =
                        "Enabled by default. Caches the executable GraphQLSchema (makeExecutableSchema result) keyed by SDL hash,"
                                + " with per-key single-flight so concurrent requests for the same schema share one build."
                                + " Set to false to rebuild the executable schema on every request.")
        boolean executableSchemaCacheEnabled() default true;

        @AttributeDefinition(
                name = "Max Query Tokens",
                description =
                        "The number of GraphQL query tokens to parse. This is a safety measure to avoid denial of service attacks."
                                + " Change ONLY if you know exactly what you are doing.")
        int maxQueryTokens() default 15000;

        @AttributeDefinition(
                name = "Max Whitespace Tokens",
                description =
                        "The number of GraphQL query whitespace tokens to parse. This is a safety measure to avoid denial of service attacks."
                                + " Change ONLY if you know exactly what you are doing.")
        int maxWhitespaceTokens() default 200000;

        @AttributeDefinition(
                name = "Maximum Field Count",
                description =
                        "The number of fields queried with an GraphQL request. This is a safety measure to avoid denial of service attacks."
                                + " Change ONLY if you know exactly what you are doing.")
        int maxFieldCount() default 100000;
    }

    private class ExecutionContext {
        final GraphQLSchema schema;
        final ExecutionInput input;

        ExecutionContext(
                @NotNull String query,
                @NotNull Map<String, Object> variables,
                @NotNull Resource queryResource,
                @NotNull String[] selectors)
                throws ScriptException {
            final String schemaSdl = prepareSchemaDefinition(schemaProvider, queryResource, selectors);
            if (schemaSdl == null) {
                throw new SlingGraphQLException(String.format(
                        "Cannot get a schema for resource %s and selectors %s.",
                        queryResource, Arrays.toString(selectors)));
            }
            LOGGER.debug("Resource {} maps to GQL schema {}", queryResource.getPath(), schemaSdl);
            final String schemaHash = SHA256Hasher.getHash(schemaSdl);
            final TypeDefinitionRegistry typeDefinitionRegistry =
                    getTypeDefinitionRegistry(schemaSdl, queryResource, selectors);
            schema = getExecutableSchema(schemaHash, typeDefinitionRegistry);
            input = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .graphQLContext(getGraphQLContextBuilder(queryResource))
                    .build();
        }

        private Consumer<GraphQLContext.Builder> getGraphQLContextBuilder(@NotNull Resource queryResource) {
            final ParserOptions parserOptions = ParserOptions.getDefaultParserOptions()
                    .transform(builder -> builder.maxTokens(maxQueryTokens)
                            .maxWhitespaceTokens(maxWhitespaceTokens)
                            .build());
            return builder -> builder.put(ParserOptions.class, parserOptions)
                    .put(InputInterceptor.class, LegacyCoercingInputInterceptor.migratesValues())
                    .put(Resource.class, queryResource);
        }
    }

    @Activate
    public void activate(Config config) {
        int schemaCacheSize = config.schemaCacheSize();
        if (schemaCacheSize < 0) {
            schemaCacheSize = 0;
        }
        int executableSchemaCacheSize = config.executableSchemaCacheSize();
        if (executableSchemaCacheSize < 0) {
            executableSchemaCacheSize = 0;
        }
        maxQueryTokens = config.maxQueryTokens();
        maxWhitespaceTokens = config.maxWhitespaceTokens();
        boolean wantExecutableCache = config.executableSchemaCacheEnabled();
        if (wantExecutableCache && executableSchemaCacheSize == 0) {
            LOGGER.info("Executable schema cache requested but executableSchemaCacheSize is 0; cache remains disabled");
        }

        writeLock.lock();
        try {
            resourceToHashMap = new BoundedCache<>(schemaCacheSize, false);
            hashToSchemaMap = new BoundedCache<>(schemaCacheSize, false);
        } finally {
            writeLock.unlock();
        }

        synchronized (executableSchemaCacheLock) {
            executableSchemaCacheEnabled = wantExecutableCache && executableSchemaCacheSize > 0;
            hashToExecutableSchemaMap = new BoundedCache<>(executableSchemaCacheSize, true);
            executableSchemaInFlight.clear();
            lastPurgedScalarGeneration = Long.MIN_VALUE;
        }
        ExecutableNormalizedOperationFactory.Options.setDefaultOptions(
                ExecutableNormalizedOperationFactory.Options.defaultOptions().maxFieldsCount(config.maxFieldCount()));
    }

    @Override
    public ValidationResult validate(
            @NotNull String query,
            @NotNull Map<String, Object> variables,
            @NotNull Resource queryResource,
            @NotNull String[] selectors) {
        try {
            final ExecutionContext ctx = new ExecutionContext(query, variables, queryResource, selectors);
            ParseAndValidateResult parseAndValidateResult = ParseAndValidate.parseAndValidate(ctx.schema, ctx.input);
            if (!parseAndValidateResult.isFailure()) {
                return DefaultValidationResult.Builder.newBuilder()
                        .withValidFlag(true)
                        .build();
            }
            DefaultValidationResult.Builder validationResultBuilder =
                    DefaultValidationResult.Builder.newBuilder().withValidFlag(false);
            for (GraphQLError error : parseAndValidateResult.getErrors()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Error: type=")
                        .append(error.getErrorType().toString())
                        .append("; ");
                sb.append("message=").append(error.getMessage()).append("; ");
                for (SourceLocation location : error.getLocations()) {
                    sb.append("location=")
                            .append(location.getLine())
                            .append(",")
                            .append(location.getColumn())
                            .append(";");
                }
                validationResultBuilder.withErrorMessage(sb.toString());
            }
            return validationResultBuilder.build();
        } catch (Exception e) {
            return DefaultValidationResult.Builder.newBuilder()
                    .withValidFlag(false)
                    .withErrorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public @NotNull Map<String, Object> execute(
            @NotNull String query,
            @NotNull Map<String, Object> variables,
            @NotNull Resource queryResource,
            @NotNull String[] selectors) {
        try {
            final ExecutionContext ctx = new ExecutionContext(query, variables, queryResource, selectors);
            final GraphQL graphQL = GraphQL.newGraphQL(ctx.schema).build();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "Executing query\n[{}]\nat [{}] with variables [{}]",
                        cleanLog.sanitize(query),
                        queryResource.getPath(),
                        cleanLog.sanitize(variables.toString()));
            }
            final ExecutionResult result = graphQL.execute(ctx.input);
            if (!result.getErrors().isEmpty()) {
                StringBuilder errors = new StringBuilder();
                for (GraphQLError error : result.getErrors()) {
                    errors.append("Error: type=")
                            .append(error.getErrorType().toString())
                            .append("; message=")
                            .append(error.getMessage())
                            .append(System.lineSeparator());
                    if (error.getLocations() != null) {
                        for (SourceLocation location : error.getLocations()) {
                            errors.append("location=")
                                    .append(location.getLine())
                                    .append(",")
                                    .append(location.getColumn())
                                    .append(";");
                        }
                    }
                }
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error(
                            "Query failed for Resource {}: query={} Errors:{}, selectors={}",
                            queryResource.getPath(),
                            cleanLog.sanitize(query),
                            errors,
                            Arrays.toString(selectors));
                }
            }
            LOGGER.debug("ExecutionResult.isDataPresent={}", result.isDataPresent());
            return result.toSpecification();
        } catch (Exception e) {
            final String message = String.format(
                    "Query failed for Resource %s: query=%s, selectors=%s",
                    queryResource.getPath(), cleanLog.sanitize(query), Arrays.toString(selectors));
            LOGGER.error(message, e);
            return SlingGraphQLErrorHelper.toSpecification(message, e);
        }
    }

    private RuntimeWiring buildWiring(TypeDefinitionRegistry typeRegistry, Iterable<GraphQLScalarType> scalars) {
        List<ObjectTypeDefinition> types = typeRegistry.getTypes(ObjectTypeDefinition.class);
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();
        for (ObjectTypeDefinition type : types) {
            builder.type(type.getName(), typeWiring -> {
                wireObjectTypeFields(typeWiring, type, typeRegistry);
                return typeWiring;
            });
        }
        scalars.forEach(builder::scalar);
        List<UnionTypeDefinition> unionTypes = typeRegistry.getTypes(UnionTypeDefinition.class);
        for (UnionTypeDefinition type : unionTypes) {
            wireTypeResolver(builder, type);
        }
        List<InterfaceTypeDefinition> interfaceTypes = typeRegistry.getTypes(InterfaceTypeDefinition.class);
        for (InterfaceTypeDefinition type : interfaceTypes) {
            wireTypeResolver(builder, type);
        }
        return builder.build();
    }

    private void wireObjectTypeFields(
            TypeRuntimeWiring.Builder typeWiring, ObjectTypeDefinition type, TypeDefinitionRegistry typeRegistry) {
        for (FieldDefinition field : type.getFieldDefinitions()) {
            try {
                DataFetcher<Object> fetcher = getDataFetcher(field);
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
    }

    private <T extends TypeDefinition<T>> void wireTypeResolver(RuntimeWiring.Builder builder, TypeDefinition<T> type) {
        try {
            TypeResolver resolver = getTypeResolver(type);
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
        throw new SlingGraphQLException(String.format(
                "Invalid fetcher name %s, does not match %s", name, SlingDataFetcherSelector.FETCHER_NAME_PATTERN));
    }

    private @NotNull String validateResolverName(String name) {
        if (SlingTypeResolverSelector.nameMatchesPattern(name)) {
            return name;
        }
        throw new SlingGraphQLException(String.format(
                "Invalid type resolver name %s, does not match %s",
                name, SlingTypeResolverSelector.RESOLVER_NAME_PATTERN));
    }

    private DataFetcher<Object> getDataFetcher(FieldDefinition field) {
        DataFetcher<Object> result = null;
        final Directive d = field.getDirectives().stream()
                .filter(i -> FETCHER_DIRECTIVE.equals(i.getName()))
                .findFirst()
                .orElse(null);
        if (d != null) {
            final String name = validateFetcherName(getDirectiveArgumentValue(d, FETCHER_NAME));
            final String options = getDirectiveArgumentValue(d, FETCHER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, FETCHER_SOURCE);
            // Always wire a wrapper so later OSGi registrations are visible to cached schemas.
            result = new SlingDataFetcherWrapper<>(dataFetcherSelector, name, options, source);
        }
        return result;
    }

    private <T extends TypeDefinition<T>> TypeResolver getTypeResolver(TypeDefinition<T> typeDefinition) {
        TypeResolver resolver = null;
        final Directive d = typeDefinition.getDirectives().stream()
                .filter(i -> RESOLVER_DIRECTIVE.equals(i.getName()))
                .findFirst()
                .orElse(null);
        if (d != null) {
            final String name = validateResolverName(getDirectiveArgumentValue(d, RESOLVER_NAME));
            final String options = getDirectiveArgumentValue(d, RESOLVER_OPTIONS);
            final String source = getDirectiveArgumentValue(d, RESOLVER_SOURCE);
            // Always wire a wrapper so later OSGi registrations are visible to cached schemas.
            resolver = new SlingTypeResolverWrapper(typeResolverSelector, name, options, source);
        }
        return resolver;
    }

    private @Nullable String prepareSchemaDefinition(
            @NotNull SchemaProvider schemaProvider,
            @NotNull org.apache.sling.api.resource.Resource resource,
            @NotNull String[] selectors)
            throws ScriptException {
        try {
            return schemaProvider.getSchema(resource, selectors);
        } catch (Exception e) {
            final ScriptException up = new ScriptException("Schema provider failed");
            up.initCause(e);
            LOGGER.info("Schema provider Exception", up);
            throw up;
        }
    }

    TypeDefinitionRegistry getTypeDefinitionRegistry(
            @NotNull String sdl, @NotNull Resource currentResource, @NotNull String[] selectors) {
        TypeDefinitionRegistry typeRegistry = null;
        readLock.lock();
        String newHash = SHA256Hasher.getHash(sdl);
        /*
        Since the SchemaProviders that generate the SDL can dynamically change, there's a two stage cache for parsed schemas:

        1. a mapping between the resource, selectors and the SDL's hash
        2. a mapping between the hash and the TypeDefinitionRegistry

        The request Resource is no longer baked into RuntimeWiring; it is supplied via GraphQLContext per execution.
        A third cache (hash → GraphQLSchema) is controlled by executableSchemaCacheEnabled (on by default).
         */
        String resourceToHashMapKey = getCacheKey(currentResource, selectors);
        String oldHash = resourceToHashMap.get(resourceToHashMapKey);
        if (!newHash.equals(oldHash) || hashToSchemaMap.get(newHash) == null) {
            readLock.unlock();
            writeLock.lock();
            try {
                oldHash = resourceToHashMap.get(resourceToHashMapKey);
                if (!newHash.equals(oldHash) || hashToSchemaMap.get(newHash) == null) {
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

    /**
     * Cap retries when scalar converters keep changing mid-build so we never spin forever under
     * continuous churn. After this many attempts the caller is served one uncached
     * {@link #buildSchema} result (the pre-cache behaviour) rather than failing the query.
     */
    private static final int MAX_EXECUTABLE_SCHEMA_BUILD_ATTEMPTS = 8;

    /**
     * Returns an executable schema for the given SDL hash. The executable schema cache is on by default.
     * When it is enabled,
     * concurrent callers for the same schema hash <em>and</em> scalar generation share a single in-flight build.
     * If converters change during a build or while waiting, the call retries until the result matches the
     * live generation or {@link #MAX_EXECUTABLE_SCHEMA_BUILD_ATTEMPTS} is exhausted. When the budget is
     * exhausted, an uncached schema is built and returned so the request still completes; that schema
     * is not published to the cache and may reflect a converter generation that has already moved on.
     */
    GraphQLSchema getExecutableSchema(@NotNull String schemaHash, @NotNull TypeDefinitionRegistry typeRegistry) {
        if (!executableSchemaCacheEnabled) {
            return buildSchema(typeRegistry).schema;
        }

        for (int attempt = 0; attempt < MAX_EXECUTABLE_SCHEMA_BUILD_ATTEMPTS; attempt++) {
            final long scalarGeneration = scalarsProvider.getScalarGeneration();
            purgeStaleExecutableSchemas(scalarGeneration);
            BuiltExecutableSchema cached = getCachedExecutableSchema(schemaHash, scalarGeneration);
            if (cached != null) {
                return cached.schema;
            }

            GraphQLSchema schema = joinOrBuildExecutableSchema(schemaHash, typeRegistry, scalarGeneration);
            if (schema != null) {
                return schema;
            }
            // Result was stale relative to live converters — retry.
        }

        LOGGER.warn(
                "Executable schema build did not stabilize after {} attempts (scalar converters changing); serving an uncached schema",
                MAX_EXECUTABLE_SCHEMA_BUILD_ATTEMPTS);
        return buildSchema(typeRegistry).schema;
    }

    /**
     * Drops executable-schema entries older than {@code liveGeneration} so unregistered
     * {@code SlingScalarConverter} instances (and their bundle classloaders) are not retained
     * until some other hash happens to be requested.
     */
    private void purgeStaleExecutableSchemas(long liveGeneration) {
        if (lastPurgedScalarGeneration >= liveGeneration) {
            return;
        }
        synchronized (executableSchemaCacheLock) {
            if (lastPurgedScalarGeneration >= liveGeneration) {
                return;
            }
            Iterator<Map.Entry<String, BuiltExecutableSchema>> it =
                    hashToExecutableSchemaMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, BuiltExecutableSchema> entry = it.next();
                if (entry.getValue().scalarGeneration < liveGeneration) {
                    it.remove();
                }
            }
            lastPurgedScalarGeneration = liveGeneration;
        }
    }

    /**
     * Joins an in-flight build or builds the schema for {@code scalarGeneration}.
     *
     * @return the schema when it matches the live scalar generation; {@code null} to signal a retry
     */
    private GraphQLSchema joinOrBuildExecutableSchema(
            @NotNull String schemaHash, @NotNull TypeDefinitionRegistry typeRegistry, long scalarGeneration) {
        final String flightKey = schemaHash + ':' + scalarGeneration;
        final CompletableFuture<BuiltExecutableSchema> created = new CompletableFuture<>();
        final CompletableFuture<BuiltExecutableSchema> existing =
                executableSchemaInFlight.putIfAbsent(flightKey, created);
        if (existing != null) {
            return schemaIfCurrentGeneration(awaitExecutableSchema(existing));
        }

        // Another builder may have finished between the cache miss and putIfAbsent winning.
        BuiltExecutableSchema cached = getCachedExecutableSchema(schemaHash, scalarsProvider.getScalarGeneration());
        if (cached != null) {
            created.complete(cached);
            executableSchemaInFlight.remove(flightKey, created);
            return cached.schema;
        }

        return buildPublishAndComplete(schemaHash, typeRegistry, flightKey, created);
    }

    /**
     * Builds, optionally publishes, and completes {@code created}. Returns the schema when generation is
     * still current, otherwise {@code null} so the caller can retry. Does not catch {@link Error}; if the
     * builder aborts with an Error, {@code finally} still completes the future so waiters do not hang.
     */
    private GraphQLSchema buildPublishAndComplete(
            @NotNull String schemaHash,
            @NotNull TypeDefinitionRegistry typeRegistry,
            @NotNull String flightKey,
            @NotNull CompletableFuture<BuiltExecutableSchema> created) {
        try {
            final BuiltExecutableSchema built = buildSchema(typeRegistry);
            publishExecutableSchema(schemaHash, built);
            created.complete(built);
            return schemaIfCurrentGeneration(built);
        } catch (Exception e) {
            created.completeExceptionally(e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new SlingGraphQLException("Executable schema build failed", e);
        } finally {
            if (!created.isDone()) {
                created.completeExceptionally(new IllegalStateException("Executable schema build aborted"));
            }
            executableSchemaInFlight.remove(flightKey, created);
        }
    }

    private GraphQLSchema schemaIfCurrentGeneration(@NotNull BuiltExecutableSchema built) {
        if (built.scalarGeneration == scalarsProvider.getScalarGeneration()) {
            return built.schema;
        }
        return null;
    }

    /**
     * Publishes {@code built} only when its generation still matches the live converter generation under
     * the cache lock, and never replaces a newer cached entry with an older one.
     */
    private void publishExecutableSchema(@NotNull String schemaHash, @NotNull BuiltExecutableSchema built) {
        synchronized (executableSchemaCacheLock) {
            if (built.scalarGeneration != scalarsProvider.getScalarGeneration()) {
                return;
            }
            BuiltExecutableSchema existing = hashToExecutableSchemaMap.get(schemaHash);
            if (existing == null || existing.scalarGeneration <= built.scalarGeneration) {
                hashToExecutableSchemaMap.put(schemaHash, built);
            }
        }
    }

    /**
     * Returns a cached schema only when its scalar generation matches {@code expectedGeneration}.
     * Stale (older) entries may be evicted; a newer entry is left intact so a delayed old-generation
     * caller cannot discard a valid fresher schema.
     */
    private BuiltExecutableSchema getCachedExecutableSchema(@NotNull String schemaHash, long expectedGeneration) {
        synchronized (executableSchemaCacheLock) {
            BuiltExecutableSchema entry = hashToExecutableSchemaMap.get(schemaHash);
            if (entry == null) {
                return null;
            }
            if (entry.scalarGeneration == expectedGeneration) {
                return entry;
            }
            if (entry.scalarGeneration < expectedGeneration) {
                hashToExecutableSchemaMap.remove(schemaHash, entry);
            }
            return null;
        }
    }

    private static BuiltExecutableSchema awaitExecutableSchema(CompletableFuture<BuiltExecutableSchema> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SlingGraphQLException("Interrupted while waiting for executable schema build", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new SlingGraphQLException("Executable schema build failed", cause);
        }
    }

    private BuiltExecutableSchema buildSchema(@NotNull TypeDefinitionRegistry typeRegistry) {
        SlingScalarsProvider.CustomScalars customScalars = scalarsProvider.getCustomScalars(typeRegistry.scalars());
        RuntimeWiring runtimeWiring = buildWiring(typeRegistry, customScalars.getScalars());
        GraphQLSchema schema = schemaGenerator.makeExecutableSchema(typeRegistry, runtimeWiring);
        return new BuiltExecutableSchema(schema, customScalars.getGeneration());
    }

    private static final class BuiltExecutableSchema {
        private final GraphQLSchema schema;
        private final long scalarGeneration;

        private BuiltExecutableSchema(GraphQLSchema schema, long scalarGeneration) {
            this.schema = schema;
            this.scalarGeneration = scalarGeneration;
        }
    }

    private String getCacheKey(@NotNull Resource resource, @NotNull String[] selectors) {
        return resource.getPath() + ":" + String.join(".", selectors);
    }

    private void handleConnectionTypes(ObjectTypeDefinition typeDefinition, TypeDefinitionRegistry typeRegistry) {
        for (FieldDefinition fieldDefinition : typeDefinition.getFieldDefinitions()) {
            Directive directive = fieldDefinition.getDirectives().stream()
                    .filter(i -> "connection".equals(i.getName()))
                    .findFirst()
                    .orElse(null);
            if (directive != null) {
                if (directive.getArgument(CONNECTION_FOR) != null) {
                    String forType =
                            ((StringValue) directive.getArgument(CONNECTION_FOR).getValue()).getValue();
                    Optional<TypeDefinition> forTypeDefinition = typeRegistry.getType(forType);
                    if (!forTypeDefinition.isPresent()) {
                        throw new SlingGraphQLException("Type '" + forType + "' has not been defined.");
                    }
                    TypeDefinition<?> forOTD = forTypeDefinition.get();
                    ObjectTypeDefinition edge = ObjectTypeDefinition.newObjectTypeDefinition()
                            .name(forOTD.getName() + "Edge")
                            .fieldDefinition(new FieldDefinition("cursor", new TypeName(TYPE_STRING)))
                            .fieldDefinition(new FieldDefinition("node", new TypeName(forOTD.getName())))
                            .build();
                    ObjectTypeDefinition connection = ObjectTypeDefinition.newObjectTypeDefinition()
                            .name(forOTD.getName() + "Connection")
                            .fieldDefinition(new FieldDefinition("edges", new ListType(new TypeName(forType + "Edge"))))
                            .fieldDefinition(new FieldDefinition("pageInfo", new TypeName(TYPE_PAGE_INFO)))
                            .build();
                    if (!typeRegistry.getType(TYPE_PAGE_INFO).isPresent()) {
                        ObjectTypeDefinition pageInfo = ObjectTypeDefinition.newObjectTypeDefinition()
                                .name(TYPE_PAGE_INFO)
                                .fieldDefinition(new FieldDefinition(
                                        "hasPreviousPage", new NonNullType(new TypeName(TYPE_BOOLEAN))))
                                .fieldDefinition(
                                        new FieldDefinition("hasNextPage", new NonNullType(new TypeName(TYPE_BOOLEAN))))
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

    /**
     * Bounded {@link LinkedHashMap}. When {@code accessOrder} is true this is a true LRU (safe only if all
     * access is externally synchronized). When false, eviction is insertion-order / FIFO.
     */
    private static class BoundedCache<T> extends LinkedHashMap<String, T> {

        private final int capacity;

        public BoundedCache(int capacity, boolean accessOrder) {
            super(16, 0.75f, accessOrder);
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
            if (o instanceof BoundedCache) {
                BoundedCache<T> other = (BoundedCache<T>) o;
                return super.equals(o) && capacity == other.capacity;
            }
            return false;
        }
    }
}
