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

import org.apache.sling.graphql.api.SelectionSet;
import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingDataFetcherEnvironment;

public class EchoDataFetcher implements SlingDataFetcher<Object> {

    private final Object data;
    private SelectionSet selectionSet;

    public EchoDataFetcher(Object data) {
        this.data = data;
    }

    @Override
    public Object get(SlingDataFetcherEnvironment e) throws Exception {
        if("null".equals(e.getFetcherOptions())) {
            return null;
        } else if(data == null) {
            return e.getCurrentResource();
        }
        selectionSet = e.getSelectionSet();
        return data;
    }

    public SelectionSet getSelectionSet() {
        return selectionSet;
    }
}
