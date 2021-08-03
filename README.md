[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-graphql-core&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-graphql-core)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-graphql-core&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-graphql-core)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.graphql.core.svg)](https://www.javadoc.io/doc/org.apache.sling/org-apache-sling-graphql-core)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.graphql.core/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.graphql.core%22)&#32;[![graphql](https://sling.apache.org/badges/group-graphql.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/graphql.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Apache Sling GraphQL Core
----

_This module is one of several which provide [GraphQL support for Apache Sling](https://github.com/search?q=topic%3Asling+topic%3Agraphql+org%3Aapache&type=Repositories)._

This module allows for running GraphQL queries in Sling, using dynamically built GraphQL schemas and
OSGi services for data fetchers (aka "resolvers") which provide the data.

To take advantage of Sling's flexibility, it allows for running GraphQL queries in three different modes,
using client or server-side queries and optionally being bound to the current Sling Resource.

Server-side queries are implemented as a Sling Script Engine.

The current version uses the [graphql-java](https://github.com/graphql-java/graphql-java) library but that's 
only used internally. The corresponding OSGi bundles must be active in your Sling instance but there's no
need to use their APIs directly.

The [GraphQL sample website](https://github.com/apache/sling-samples/tree/master/org.apache.sling.graphql.samples.website)
provides usage examples and demonstrates using GraphQL queries (and Handlebars templates) on both the server and
client sides. It's getting a bit old and as of June 2021 doesn't demonstrate the latest features.

As usual, **the truth is in the tests**. If something's missing from this page you can probably find the details in
this module's [extensive test suite](./src/test).
 
## Supported GraphQL endpoint styles

This module enables the following GraphQL "styles"

  * The **traditional GraphQL endpoint** style, where the clients supply requests to a single URL. It is easy to define
    multiple such endpoints with different settings, which can be useful to provide different "views" of your content.
  * A **Resource-based GraphQL endpoints** style where every Sling Resource can be a GraphQL endpoint (using specific 
    request selectors and extensions) where queries are executed in the context of that Resource. This is an experimental
    idea at this point but it's built into the design so doesn't require more efforts to support. That style supports both
    server-side "**prepared GraphQL queries**" and the more traditional client-supplied queries.
    
The GraphQL requests hit a Sling resource in all cases, there's no need for path-mounted servlets which are [not desirable](https://sling.apache.org/documentation/the-sling-engine/servlets.html#caveats-when-binding-servlets-by-path-1).

See also the _caching_ section later in this file.

## Configuring the GraphQL Servlet
Here's an excerpt from an OSGi feature model file which uses the GraphQL Servlet provided by this module
to serve `.json` requests for resources which have the `samples/graphql` resource type:

    "configurations":{
      "org.apache.sling.graphql.core.GraphQLServlet~default" : {
        "sling.servlet.resourceTypes" : "samples/graphql",
        "sling.servlet.extensions": "json",
        "sling.servlet.methods": [ "GET", "POST" ]
      },
      "org.apache.sling.servlets.get.DefaultGetServlet" : {
        "aliases" : [ "json:rawjson" ]
      },
      
The `rawjson` selector is configured to provide Sling's default JSON output.

See the `GraphQLServlet` class for more info.

## Resource-specific GraphQL schemas

Schemas are provided by `SchemaProvider` services:

```java
@ProviderType
public interface SchemaProvider {
  
    /** Get a GraphQL Schema definition for the given resource and optional selectors
     *
     *  @param r The Resource to which the schema applies
     *  @param selectors Optional set of Request Selectors that can influence the schema selection
     *  @return a GraphQL schema that can be annotated to define the data fetchers to use, see
     *      this module's documentation. Can return null if a schema cannot be provided, in which
     *      case a different provider should be used.
     *  @throws java.io.IOException if the schema cannot be retrieved
     */
    @Nullable
    String getSchema(@NotNull Resource r, @Nullable String [] selectors) throws IOException;
}
```

The default provider makes an internal Sling request with for the current Resource with a `.GQLschema` extension.

This allows the Sling script/servlet resolution mechanism and its script engines to be used to generate 
schemas dynamically, taking request selectors into account.

Unless you have specific needs not covered by this mechanism, there's no need to implement your
own `SchemaProvider` services.

## Built-in GraphQL Schema Directives

Since version 0.0.10 of this module, a number of GraphQL schema directives are built-in to support specific
features. As of that version, the `@fetcher`, `@resolver` and `@connection` directives described below
can be used directly, without having to declare them explicitly in the schema with the `directive`
statement that was required before.

Declaring these directives explicitly is still supported for backwards compatibility with existing
schemas, but not needed anymore.

### SlingDataFetcher selection using the `@fetcher` directive

The following built-in `@fetcher` directive is defined by this module:

```graphql
    # This directive maps fields to our Sling data fetchers
    directive @fetcher(
        name : String!,
        options : String = "",
        source : String = ""
    ) on FIELD_DEFINITION
```


It allows for selecting a specific `SlingDataFetcher` service to return the appropriate data, as in the
examples below.

Fileds which do not have such a directive will be retrieved using the default data fetcher.

Here are a few examples, the test code has more of them:

```graphql
    type Query {
      withTestingSelector : TestData @fetcher(name:"test/pipe")
    }

    type TestData {
      farenheit: Int @fetcher(name:"test/pipe" options:"farenheit")
    }
```

The names of those `SlingDataFetcher` services are in the form

    <namespace>/<name>

The `sling/` namespace is reserved for `SlingDataFetcher` services
which have Java package names that start with `org.apache.sling`.

The `<options>` and `<source>` arguments of the directive can be used by the
`SlingDataFetcher` services to influence their behavior.

### SlingTypeResolver selection using the `@resolver` directive

The following built-in `@resolver` directive is defined by this module:

```graphql
    # This directive maps the corresponding type resolver to a given Union
    directive @resolver(
        name: String!, 
        options: String = "", 
        source: String = ""
    ) on UNION | INTERFACE
```

A `Union` or `Interface` type can provide a `@resolver` directive, to select a specific `SlingTypeResolver` service to return the appropriate GraphQL object type.

Here's a simple example, the test code has more:

    union TestUnion @resolver(name : "test/resolver", source : "TestUnion") = Type_1 | Type_2 | Type_3 | Type_4

The names of those `SlingTypeResolver` services are in the form

    <namespace>/<name>

The `sling/` namespace is reserved for `SlingTypeResolver` services
which have Java package names that start with `org.apache.sling`.

The `<options>` and `<source>` arguments of the directive can be used by the
`SlingTypeResolver` services to influence their behavior.

## Result Set Pagination using the `@connection` and `@fetcher` directives

This module implements support for the [Relay Cursor Connections](https://relay.dev/graphql/connections.htm)
specification, via the built-in `@connection` directive, coupled with a `@fetcher` directive. The built-in `@connection`
directive has the following definition:

```graphql
    directive @connection(
      for: String!
    ) on FIELD_DEFINITION
```

To allow schemas to be ehanced with pagination support, like in this example:

```graphql
    type Query {
        paginatedHumans (after : String, limit : Int) : HumanConnection @connection(for: "Human") @fetcher(name:"humans/connection")
    }

    type Human {
        id: ID!
        name: String!
        address: String
    }
```

Using this directive as in the above example adds the following types to the schema to provide paginated
output that follows the Relay spec:

```graphql
    type PageInfo {
        startCursor : String
        endCursor : String
        hasPreviousPage : Boolean
        hasNextPage : Boolean
    }

    type HumanEdge {
        cursor: String
        node: Human
    }

    type HumanConnection {
        edges : [HumanEdge]
        pageInfo : PageInfo
    }
```

### How to implement a SlingDataFetcher that provides a paginated result set

The [GenericConnection](./src/main/java/org/apache/sling/graphql/core/helpers/pagination/GenericConnection.java) class,
together with the [`org.apache.sling.graphql.api.pagination`](./src/main/java/org/apache/sling/graphql/api/pagination) API
provide support for paginated results. With this utility class, you just need to supply an `Iterator` on your data, a
function to generate a string that represents the cursor for a given object, and optional parameters to control the
page start and length.

The [QueryDataFetcherComponent](./src/test/java/org/apache/sling/graphql/core/mocks/QueryDataFetcherComponent.java) provides a usage example: 

```java
    @Override
    public Object get(SlingDataFetcherEnvironment env) throws Exception {
      // fake test data simulating a query
      final List<Resource> data = new ArrayList<>();
      data.add(env.getCurrentResource());
      data.add(env.getCurrentResource().getParent());
      data.add(env.getCurrentResource().getParent().getParent());

      // Define how to build a unique cursor that points to one of our data objects
      final Function<Resource, String> cursorStringProvider = r -> r.getPath();

      // return a GenericConnection that the library will introspect and serialize
      return new GenericConnection.Builder<>(data.iterator(), cursorStringProvider)
        .withLimit(5)
        .build();
    }
```    

The above data fetcher code produces the following output, with the `GenericConnection` helper taking
care of the pagination logic and of generating the required data. This follows the
[Relay Connections](https://relay.dev/graphql/connections.htm) specification, which some GraphQL clients
should support out of the box.

```json
    {
      "data": {
        "oneSchemaQuery": {
          "pageInfo": {
            "startCursor": "L2NvbnRlbnQvZ3JhcGhxbC9vbmU=",
            "endCursor": "L2NvbnRlbnQ=",
            "hasPreviousPage": false,
            "hasNextPage": false
          },
          "edges": [
            {
              "cursor": "L2NvbnRlbnQvZ3JhcGhxbC9vbmU=",
              "node": {
                "path": "/content/graphql/one",
                "resourceType": "graphql/test/one"
              }
            },
            {
              "cursor": "L2NvbnRlbnQvZ3JhcGhxbA==",
              "node": {
                "path": "/content/graphql",
                "resourceType": "graphql/test/root"
              }
            },
            {
              "cursor": "L2NvbnRlbnQ=",
              "node": {
                "path": "/content",
                "resourceType": "sling:OrderedFolder"
              }
            }
          ]
        }
      }
    }
```

Usage of this `GenericConnection` helper is optional, although recommended for ease of use and consistency. As long
as the `SlingDataFetcher` provides a result that implements the [`org.apache.sling.graphql.api.pagination.Connection`](./src/main/java/org/apache/sling/graphql/api/pagination/Connection.java),
the output will be according to the Relay spec.

## Lazy Loading of field values

The [org.apache.sling.graphql.helpers.lazyloading](src/main/java/org/apache/sling/graphql/helpers/lazyloading) package provides helpers
for lazy loading field values.

Using this pattern, for example:

```java
    public class ExpensiveObject {
      private final LazyLoadingField<String> lazyName;

      ExpensiveObject(String name) {
        lazyName = new LazyLoadingField<>(() -> {
          // Not really expensive but that's the idea
          return name.toUpperCase();
        });
      }

      public String getExpensiveName() {
        return lazyName.get();
      }
    }
```

The `expensiveName` is only computed if its get method is called. This avoids executing expensive computations
for fields that are not used in the GraphQL result set.

A similar helper is provided for Maps with lazy loaded values.

## Caching: Persisted queries API

No matter how you decide to create your Sling GraphQL endpoints, you have the option to allow GraphQL clients to use persisted queries.

After preparing a query with a POST request, it can be executed with a GET request that can be cached by HTTP caches or a CDN.

This is required as POST queries are usually not cached, and if using GET with the query as a parameter there's a concrete risk of 
the parameter becoming too large for HTTP services and intermediates.

### How to use persisted queries?

1. An instance of the GraphQL servlet has to be configured; by default, the servlet will enable the persisted queries API on the
 `/persisted` request suffix; the value is configurable, via the `persistedQueries.suffix` parameter of the factory configuration.
2. A client prepares a persisted query in advance by `POST`ing the query text to the endpoint where the GraphQL servlet is bound, plus the
 `/persisted` suffix.
3. The servlet will respond with a `201 Created` status; the response's `Location` header will then instruct the client where it can then
 execute the persisted query, via a `GET` request.
4. The responses for a `GET` requests to a persisted query will contain appropriate HTTP Cache headers, allowing front-end HTTP caches
 (e.g. CDNs) to cache the JSON responses. 
5. There's no guarantee on how long a persisted query is stored. A client that gets a `404` on a persisted query must be prepared to
 re`POST` the query, in order to store the prepared query again.

#### Persisted Query Hash

The hash that's part of the `persisted` URL is computed on the POSTed GraphQL query by the
active `GraphQLCacheProvider` service. By default, this is the `SimpleGraphQLCacheProvider`
which computes it as follows:

```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(query.getBytes(StandardCharsets.UTF_8));
```
    
and encodes it in hex to build the persisted query's path.

This means that, if desired, an optimistic client can compute the hash itself and try a GET to
the `persisted/<hash>` URL without doing a POST first. If the query already exists in the cache
this saves the POST request, and if not the client gets a 404 status and has to POST the query
first.

#### Example HTTP interactions with persisted queries enabled

1. Storing a query
    ```bash
    curl -v 'http://localhost:8080/graphql.json/persisted' \
      -H 'Content-Type: application/json' \
      --data-binary '{"query":"{\n  navigation {\n    search\n    sections {\n      path\n      name\n    }\n  }\n  article(withText: \"virtual\") {\n    path\n    title\n    seeAlso {\n      path\n      title\n      tags\n    }\n  }\n}\n","variables":null}' \
      --compressed
    > POST /graphql.json/persisted HTTP/1.1
    > Host: localhost:8080
    > User-Agent: curl/7.64.1
    > Accept: */*
    > Accept-Encoding: deflate, gzip
    > Content-Type: application/json
    > Content-Length: 236
    >
    * upload completely sent off: 236 out of 236 bytes
    < HTTP/1.1 201 Created
    < Date: Mon, 31 Aug 2020 16:33:48 GMT
    < X-Content-Type-Options: nosniff
    < X-Frame-Options: SAMEORIGIN
    < Location: http://localhost:8080/graphql.json/persisted/e1ce2e205e1dfb3969627c6f417860cadab696e0e87b1c44de1438848661b62f.json
    < Content-Length: 0
    ```
2. Running a persisted query
```bash
curl -v http://localhost:8080/graphql.json/persisted/e1ce2e205e1dfb3969627c6f417860cadab696e0e87b1c44de1438848661b62f.json
> GET /graphql.json/persisted/e1ce2e205e1dfb3969627c6f417860cadab696e0e87b1c44de1438848661b62f.json HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/7.64.1
> Accept: */*
>
< HTTP/1.1 200 OK
< Date: Mon, 31 Aug 2020 16:35:18 GMT
< X-Content-Type-Options: nosniff
< X-Frame-Options: SAMEORIGIN
< Cache-Control: max-age=60
< Content-Type: application/json;charset=utf-8
< Transfer-Encoding: chunked
<

{
  "data": {
    "navigation": {
      "search": "/content/search",
      "sections": [
        {
          "path": "/content/articles/travel",
          "name": "Travel"
        },
        {
          "path": "/content/articles/music",
          "name": "Music"
        }
      ]
    }
    "article": [
      {
        "path": "/content/articles/travel/precious-kunze-on-the-bandwidth-of-virtual-nobis-id-aka-usb",
        "title": "Travel - Precious Kunze on the bandwidth of virtual 'nobis id' (aka USB)",
        "seeAlso": [
          {
            "path": "/content/articles/travel/solon-davis-on-the-card-of-primary-reiciendis-omnis-aka-sql",
            "title": "Travel - Solon Davis on the card of primary 'reiciendis omnis' (aka SQL)",
            "tags": [
              "bandwidth",
              "protocol"
            ]
          }
        ]
      }
    ]
  }
}
```
    
## Planned Extensions / Wishlist

### Selector-driven prepared queries (planned)

Described in [SLING-10540](https://issues.apache.org/jira/browse/SLING-10540): prepared GraphQL queries hidden behind
URL selectors, so that an HTTP GET request to `/content/mypage.A.full.json` executes the GraphQL query previously
stored under the `A.full` name.

### Schema Aggregator (planned)

An initial spec, without code so far, is available at
[sling-whiteboard:sling-org-apache-sling-graphql-schema](https://github.com/apache/sling-whiteboard/tree/master/sling-org-apache-sling-graphql-schema)
for a _schema aggregator_ that allows OSGi bundles to contribute partial GraphQL schemas to an overall schema.

This will allow bundles to contribute specific sets of types to a schema, along with the code that implements their retrieval and other
operations.

### Object Query Service (whishlist)

The Object Query service runs queries against the Sling Resource tree and returns POJOs in
a way that's optimized for the GraphQL Core to consume.

Probably something along those lines:

    new Query(
      """
      select Folder
      from /tmp, /conf
      where Folder.title contains 'sling'
      and Folder.lastModified < 1w
      """)
    .getIterator();

which returns an `Iterator` optimized for this module's pagination features.

The objects that this Iterator supplies can be built from Sling Models, using the lazy loading helpers provided
by this module. This would probably need an extension to Sling Models where the appropriate Model can be found
for a name like `Folder` in the above example. The Model class might be annotated in a way that allows it to
supply XPath query elements for expressions like `Folder.title`.

The Query might have additional options such as `withXpathGenerator`, `withObjectMapper` for edge cases
where the built-in logic is not sufficient.
