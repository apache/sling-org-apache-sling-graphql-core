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
package org.apache.sling.graphql.core.mocks;

public class TypeTestDTO {
    private final boolean test;
    private final boolean boolValue;
    private final String resourcePath;
    private final String testingArgument;

    public TypeTestDTO(boolean test, boolean boolValue, String resourcePath, String testingArgument) {
        this.test = test;
        this.boolValue = boolValue;
        this.resourcePath = resourcePath;
        this.testingArgument = testingArgument;
    }

    public boolean isTest() {
        return test;
    }

    public boolean isBoolValue() {
        return boolValue;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public String getTestingArgument() {
        return testingArgument;
    }

}
