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
package org.apache.sling.graphql.core.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.sling.graphql.api.engine.ValidationResult;
import org.jetbrains.annotations.NotNull;

public class DefaultValidationResult implements ValidationResult {

    private final boolean isValid;
    private final List<String> errors;

    private DefaultValidationResult(boolean isValid, List<String> errors) {
        this.isValid = isValid;
        this.errors = errors;
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    @Override
    public @NotNull List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    static class Builder {

        private boolean isValid;
        private List<String> errors = new ArrayList<>();

        private Builder() {};

        Builder withValidFlag(boolean isValid) {
            this.isValid = isValid;
            return this;
        }

        Builder withErrorMessage(@NotNull String message) {
            errors.add(message);
            return this;
        }

        ValidationResult build() {
            return new DefaultValidationResult(isValid, errors);
        }

        static Builder newBuilder() {
            return new Builder();
        }
    }
}
