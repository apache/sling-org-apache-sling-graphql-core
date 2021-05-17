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

 package org.apache.sling.graphql.api.pagination;

import java.util.Base64;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.graphql.api.SlingGraphQLException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;

/**
 * This class provides a Base64-encoded cursor which is used for paginated results, according to the specification from
 * <a href="https://relay.dev/graphql/connections.htm#sec-Cursor">https://relay.dev/graphql/connections.htm#sec-Cursor</a>.
 */
@ProviderType
public class Cursor {

    private final String rawValue;
    private final String encoded;

    /**
     * Creates a cursor from a {@link String}. The passed {@code rawValue} should not be {@code null}, nor an empty {@link String}.
     *
     * @param rawValue the raw value from which to generate a cursor
     */
    public Cursor(@NotNull String rawValue) {
        if (StringUtils.isEmpty(rawValue)) {
            throw new SlingGraphQLException("Cannot create a cursor from an empty string.");
        }
        this.rawValue = rawValue;
        this.encoded = encode(this.rawValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if(!(obj instanceof Cursor)) {
            return false;
        }
        final Cursor other = (Cursor)obj;
        return Objects.equals(rawValue, other.rawValue) && Objects.equals(encoded, other.encoded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawValue, encoded);
    }

    /**
     * Generates a new cursor based on an {@code encoded} value obtained via the same encoding mechanism {@code this} class uses.
     *
     * @param encoded the encoded value from which to generate a new cursor
     * @return a new cursor, if one can be generated; {@code null} otherwise
     */
    public static Cursor fromEncodedString(@Nullable String encoded) {
        if(encoded == null) {
            return null;
        }
        encoded = encoded.trim();
        if(encoded.length() == 0) {
            return null;
        }
        return new Cursor(decode(encoded));
    }

    @NotNull
    static String encode(String rawValue) {
        return Base64.getEncoder().encodeToString(rawValue.getBytes());
    }

    @NotNull
    static String decode(String encodedValue) {
        return new String(Base64.getDecoder().decode(encodedValue));
    }

    @Override
    public String toString() {
        return encoded;
    }

    @NotNull
    public String getRawValue() {
        return rawValue;
    }

    @NotNull
    public String getEncoded() {
        return encoded;
    }
}
