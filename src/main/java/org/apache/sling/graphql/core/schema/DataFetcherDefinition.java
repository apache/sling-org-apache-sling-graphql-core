
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

package org.apache.sling.graphql.core.schema;

// TODO Since moving to schema directives, we don't really need this class anymore...
public class DataFetcherDefinition {
    public final String fetcherNamespace;
    public final String fetcherName;
    public final String fetcherOptions;
    public final String fetcherSourceExpression;

    public DataFetcherDefinition(String nameSpaceAndName, String options, String source) throws IllegalArgumentException {
        final String [] parts = nameSpaceAndName.split("/");
        if(parts.length != 2) {
            throw new IllegalArgumentException("Expected a namespace/name String, got " + nameSpaceAndName);
        }
        fetcherNamespace = parts[0];
        fetcherName = parts[1];
        fetcherOptions = options;
        fetcherSourceExpression = source;
    }

    public String getFetcherNamespace() {
        return fetcherNamespace;
    }

    public String getFetcherName() {
        return fetcherName;
    }

    public String getFetcherOptions() {
        return fetcherOptions;
    }

    public String getFetcherSourceExpression() {
        return fetcherSourceExpression;
    }

    @Override
    public String toString() {
        return String.format(
            "%s#%s#%s#%s#%s",
            getClass().getSimpleName(),
            fetcherNamespace, fetcherName, 
            fetcherOptions, fetcherSourceExpression);
    }
}