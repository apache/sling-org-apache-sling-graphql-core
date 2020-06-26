
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

package org.apache.sling.graphql.core.scalars;

import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;

import graphql.language.ScalarTypeDefinition;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

/**
 * Provides GraphQL Scalars (leaf data types) for query execution
 */
@Component(service = SlingScalarsProvider.class, property = {
    Constants.SERVICE_DESCRIPTION + "=Apache Sling Scripting GraphQL Scalars Provider",
    Constants.SERVICE_VENDOR + "=The Apache Software Foundation" })
public class SlingScalarsProvider {

    static class URLCoercing implements Coercing<URL, String> {
        static final String NAME = "URL";

        @Override
        public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
            if(dataFetcherResult instanceof URL) {
                return getClass().getSimpleName() + " says:" + ((URL)dataFetcherResult).toExternalForm(); 
            }
            throw new CoercingSerializeException("Unexpected serialize input " + dataFetcherResult);
        }

        @Override
        public URL parseValue(Object input) throws CoercingParseValueException {
            try {
                return new URL(String.valueOf(input));
            } catch(Exception e) {
                throw new CoercingParseValueException("URL parsing failed ", e);
            }
        }

        @Override
        public URL parseLiteral(Object input) throws CoercingParseLiteralException {
            if(input instanceof StringValue) {
                return parseValue(((StringValue)input).getValue());

            }
            throw new CoercingSerializeException("Unexpected parse input " + input);
        }

    }

    static class UppercaseStringCoercing implements Coercing<String, String> {
        static final String NAME = "UppercaseString";

        @Override
        public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return String.valueOf(dataFetcherResult).toUpperCase();
        }

        @Override
        public String parseValue(Object input) throws CoercingParseValueException {
            throw new CoercingParseValueException("Parsing not implemented");
        }

        @Override
        public String parseLiteral(Object input) throws CoercingParseLiteralException {
            throw new CoercingParseValueException("Parsing not implemented");
        }

    }

    private GraphQLScalarType getScalar(String name) {
        if(URLCoercing.NAME.equals(name)) {
            return GraphQLScalarType.newScalar()
                .name(URLCoercing.NAME)
                .description("TODO should be an OSGi service - hardcoded for initial tests")
                .coercing(new URLCoercing())
                .build()
            ;
        }
        if(UppercaseStringCoercing.NAME.equals(name)) {
            return GraphQLScalarType.newScalar()
                .name(UppercaseStringCoercing.NAME)
                .description("TODO should be an OSGi service - hardcoded for initial tests")
                .coercing(new UppercaseStringCoercing())
                .build()
            ;
        }
        return null;
    }

    public Iterable<GraphQLScalarType> getScalars(Map<String,ScalarTypeDefinition> schemaScalars) {
        // Using just the names for now, not sure why we'd need the ScalarTypeDefinitions
        return schemaScalars.keySet().stream()
            .map(this::getScalar)
            .filter(it -> it != null)
            .collect(Collectors.toList());
    }

}
