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
package org.apache.sling.graphql.api;

import org.osgi.annotation.versioning.ProviderType;

/**
 * The {@code SlingGraphQLException} defines the class of errors that can be thrown by the {@code org.apache.sling.graphql.core} bundle.
 */
@ProviderType
public class SlingGraphQLException extends RuntimeException {

    /**
     * Creates a {@code SlingGraphQLException} without a known cause.
     *
     * @param message the exception's message
     */
    public SlingGraphQLException(String message) {
        this(message, null);
    }

    /**
     * Creates a {@code SlingGraphQLException} with a known cause.
     *
     * @param message the exception's message
     * @param cause   the cause of this exception
     */
    public SlingGraphQLException(String message, Throwable cause) {
        super(message, cause);
    }

}
