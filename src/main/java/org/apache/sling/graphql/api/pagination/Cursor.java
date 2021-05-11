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

import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/** Cursor for our paginated results.
 *  Base64-encoded to express the fact that it's meant to
 *  be opaque.
 */
 @ProviderType
public class Cursor {
    private final String rawValue;
    private final String encoded;

    public Cursor(String rawValue) {
        this.rawValue = rawValue == null ? "" : rawValue;
        this.encoded = encode(this.rawValue);
    }

    public boolean isEmpty() {
        return rawValue == null || rawValue.length() == 0;
    }

    @NotNull
    public static String encode(String rawValue) {
        return Base64.getEncoder().encodeToString(rawValue.getBytes());
    }

    @NotNull
    public static String decode(String encodedValue) {
        return new String(Base64.getDecoder().decode(encodedValue));
    }

    @Override
    public String toString() {
        return encoded;
    }

    public String getRawValue() {
        return rawValue;
    }
}
