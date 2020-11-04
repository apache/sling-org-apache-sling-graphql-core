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

import graphql.GraphQLError;

import java.util.*;
import java.util.stream.Collectors;

public class SlingGraphQLErrorHelper {
    
    public static final String GRAPHQL_ERROR_EXTENSIONS = "extensions";
    public static final String GRAPHQL_ERROR_MESSAGE = "message";
    public static final String GRAPHQL_ERROR_DETAIL = "detail";
    public static final String GRAPHQL_ERROR_EXCEPTION = "exception";
    public static final String GRAPHQL_ERROR_CAUSE = "cause";
    public static final String GRAPHQL_ERROR_CAUSE_STACKTRACE = "stacktrace";
    public static final String GRAPHQL_ERROR_ERRORS = "errors";

    /**
     * Structures the given error information into a {@code Map} following GraphQL error specification.
     * @param customMessage custom message to be included in the response. 
     * @param e exception.
     * @return a map containing error detail.
     */
    public static Map<String, Object> toSpecification(String customMessage, Exception e) {
        Map<String, Object> additionalExceptionInfo = new LinkedHashMap<>();
        additionalExceptionInfo.put(GRAPHQL_ERROR_MESSAGE, e.getMessage());

        Map<String, Object> extensionsMap = new LinkedHashMap<>();
        extensionsMap.put(GRAPHQL_ERROR_DETAIL, customMessage);
        extensionsMap.put(GRAPHQL_ERROR_EXCEPTION, e.getClass().getName());
        if (e.getCause() != null) {
            extensionsMap.put(GRAPHQL_ERROR_CAUSE, e.getCause().toString());
            List<String> stacktrace =  new ArrayList<>();
            
            //keep top 10 (max) stacktrace entries
            int i = 0;
            for (StackTraceElement stackTraceElement : e.getCause().getStackTrace()) {
                stacktrace.add(e.getCause().getStackTrace()[i].toString());
                if (++i > 9) {
                    break;
                }
            }
            extensionsMap.put(GRAPHQL_ERROR_CAUSE_STACKTRACE, stacktrace);
        }
        additionalExceptionInfo.put(GRAPHQL_ERROR_EXTENSIONS, extensionsMap);

        List<Map<String, Object>> errorList = new ArrayList<>();
        errorList.add(0, additionalExceptionInfo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(GRAPHQL_ERROR_ERRORS, errorList );

        return result;
    }
}
