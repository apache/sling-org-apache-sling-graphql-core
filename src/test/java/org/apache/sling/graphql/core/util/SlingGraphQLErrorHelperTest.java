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
package org.apache.sling.graphql.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.InvalidParameterException;
import java.util.*;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class SlingGraphQLErrorHelperTest{
    @Test
    public void toSpecificatonTest() {
        final String customMessage = "Custom error message";
        try {
            throw new Exception(new InvalidParameterException());
        } catch(Exception e) {
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> errors = (List<Map<String, Object>>) SlingGraphQLErrorHelper.toSpecification(customMessage, e).get(SlingGraphQLErrorHelper.GRAPHQL_ERROR_ERRORS);
            assertEquals(e.getMessage(), errors.get(0).get(SlingGraphQLErrorHelper.GRAPHQL_ERROR_MESSAGE));

            @SuppressWarnings("unchecked")
            final Map<String, Object> extensions = (Map<String, Object>) errors.get(0).get(SlingGraphQLErrorHelper.GRAPHQL_ERROR_EXTENSIONS);
            assertEquals(customMessage, extensions.get(SlingGraphQLErrorHelper.GRAPHQL_ERROR_DETAIL));
            assertEquals(InvalidParameterException.class.getName(), extensions.get(SlingGraphQLErrorHelper.GRAPHQL_ERROR_CAUSE));
        }
    }
}
