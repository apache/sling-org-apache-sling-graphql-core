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

package org.apache.sling.graphql.core.mocks;

import java.net.URL;

import org.apache.sling.graphql.api.ScalarConversionException;
import org.apache.sling.graphql.api.SlingScalarConverter;
import org.jetbrains.annotations.Nullable;

public class URLScalarConverter implements SlingScalarConverter<URL, String> {

    @Override
    public @Nullable URL parseValue(@Nullable String input) throws ScalarConversionException {
        try {
            return new URL(input);
        } catch(Exception e) {
            throw new ScalarConversionException(getClass().getSimpleName() + ":Invalid URL:" + input, e);
        }
    }

    @Override
    public @Nullable String serialize(@Nullable URL value) throws ScalarConversionException {
        final String testPrefix = getClass().getSimpleName() + " says:";
        return testPrefix + (value == null ? null : value.toExternalForm());
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }
}