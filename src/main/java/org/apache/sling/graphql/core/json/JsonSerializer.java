
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

package org.apache.sling.graphql.core.json;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import com.cedarsoftware.util.io.JsonWriter;

import org.apache.commons.io.output.WriterOutputStream;

import graphql.ExecutionResult;

public class JsonSerializer {

    public static final Map<String, Object> WRITER_OPTIONS = new HashMap<String, Object>() {
        private static final long serialVersionUID = 1L;
        {
            put(JsonWriter.TYPE, false);
        }
    };

    public void sendJSON(PrintWriter out, ExecutionResult result) throws IOException {
        final Object data = result.toSpecification();
        if (data == null) {
            throw new IOException("No data");
        }
        try(JsonWriter w = new JsonWriter(new WriterOutputStream(out), WRITER_OPTIONS)) {
            w.write(data);
        }
    }

    public String toJSON(Object data) {
        return JsonWriter.objectToJson(data, JsonSerializer.WRITER_OPTIONS);
    }
}
