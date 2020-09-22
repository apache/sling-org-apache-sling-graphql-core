
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

import org.apache.sling.graphql.api.ScalarConversionException;
import org.apache.sling.graphql.api.SlingScalarConverter;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

/** Wraps {@link SlingScalarConverter} into a GraphQL-java Coercing */
class SlingCoercingWrapper implements Coercing<Object, Object> {
    private final SlingScalarConverter<Object, Object> converter;

    SlingCoercingWrapper(SlingScalarConverter<Object, Object> c) {
        converter = c;
    }

    @Override
    public Object serialize(Object dataFetcherResult) {
        try {
            return converter.serialize(dataFetcherResult);
        } catch(ScalarConversionException sce) {
            throw new CoercingSerializeException(sce);
        }
    }

    @Override
    public Object parseValue(Object input) {
        try {
            return converter.parseValue(input);
        } catch(ScalarConversionException sce) {
            throw new CoercingSerializeException(sce);
        }
    }

    @Override
    public Object parseLiteral(Object input) {
        // This is called when parsing objects from the GraphQL Abstract Syntax Tree
        // So far we handle StringValue only and unfortunately there's no common
        // interface for the getValue() method.
        try {
            if(input instanceof StringValue) {
                return converter.parseValue(((StringValue)input).getValue());
            } else {
                return converter.parseValue(input);
            }
        } catch(ScalarConversionException sce) {
            throw new CoercingSerializeException(sce);
        }
    }

 }
