[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/)&#32;[![Test Status](https://img.shields.io/jenkins/tests.svg?jobUrl=https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-graphql-core/job/master/test/?width=800&height=600)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-graphql-core&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-graphql-core)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-graphql-core&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-graphql-core)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.graphql.core.svg)](https://www.javadoc.io/doc/org.apache.sling/org-apache-sling-graphql-core)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.graphql.core/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.graphql.core%22) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Apache Sling GraphQL Core
----

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
client sides.

> As I write this, work is ongoing at [SLING-9550](https://issues.apache.org/jira/browse/SLING-9550) to implement custom 
> GraphQL Scalars
 
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

## SlingDataFetcher selection with Schema Directives

The GraphQL schemas used by this module can be enhanced using
[schema directives](http://spec.graphql.org/June2018/#sec-Language.Directives)
(see also the [Apollo docs](https://www.apollographql.com/docs/graphql-tools/schema-directives/) for how those work)
that select specific `SlingDataFetcher` services to return the appropriate data.

A default data fetcher is used for types and fields which have no such directive.

Here's a simple example, the test code has more:

    # This directive maps fields to our Sling data fetchers
    directive @fetcher(
        name : String,
        options : String = "",
        source : String = ""
    ) on FIELD_DEFINITION

    type Query {
      withTestingSelector : TestData @fetcher(name:"test/pipe")
    }

    type TestData {
      farenheit: Int @fetcher(name:"test/pipe" options:"farenheit")
    }

The names of those `SlingDataFetcher` services are in the form

    <namespace>/<name>

The `sling/` namespace is reserved for `SlingDataFetcher` services
which hava Java package names that start with `org.apache.sling`.

The `<options>` and `<source>` arguments of the directive can be used by the
`SlingDataFetcher` services to influence their behavior.

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
    
