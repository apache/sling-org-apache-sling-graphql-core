Apache Sling GraphQL Core - Metrics
----

This page documents the metrics the Apache Sling GraphQL Core bundle exposes to the Metrics Registry.

## `org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider`

<dl>

<dt>org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider.evictions</dt>
<dd>
    the number of cache evictions for <a href="../README.md#caching-persisted-queries-api">persisted queries</a>
</dd>

<dt>org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider.cacheSize</dt>
<dd>
    the maximum number of entries the cache can store
</dd>

<dt>org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider.elements<dt>
<dd>
    the current number of elements the cache stores
</dd>

<dt>org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider.maxMemory</dt>
<dd>
    the maximum amount of memory the stored queries can consume (in bytes)
</dd>

<dt>org.apache.sling.graphql.core.cache.SimpleGraphQLCacheProvider.currentMemory</dt>
<dd>
    the current amount of memory used to store the persisted queries (in bytes); this is calculated through an approximation of the amount of bytes each entry requires
</dd>

</dl>

## `org.apache.sling.graphql.core.servlet.GraphQLServlet`

For each service instance of the `org.apache.sling.graphql.core.servlet.GraphQLServlet` servlet, the following additional
metrics are available, where the `<qualifier>` is a string using the
`.rt:list of servlet resource types.m:list of servlet methods.s:list of servlet selectors.e:list of servlet extensions` pattern (each
 list uses `_` as an element separator):

<dl>

<dt>org.apache.sling.graphql.core.servlet.GraphQLServlet.&lt;qualifier&gt;.cache_hits</dt>
<dd>the number of times a persisted query was retrieved from the cache</dd>

<dt>org.apache.sling.graphql.core.servlet.GraphQLServlet.&lt;qualifier&gt;.cache_misses</dt>
<dd>the number of times a persisted query was not found in the cache</dd>

<dt>org.apache.sling.graphql.core.servlet.GraphQLServlet.&lt;qualifier&gt;.cache_hit_rate</dt>
<dd>the cache hit rate: a float number between 0 and 1.0; multiply the number by 100 to get the percentage</dd>

<dt>org.apache.sling.graphql.core.servlet.GraphQLServlet.&lt;qualifier&gt;.total_requests</dt>
<dd>total number of requests served by this servlet</dd>

<dt>org.apache.sling.graphql.core.servlet.GraphQLServlet.&lt;qualifier&gt;.requests_timer</dt>
<dd>request timing metrics for this servlet</dd>

</dl>
