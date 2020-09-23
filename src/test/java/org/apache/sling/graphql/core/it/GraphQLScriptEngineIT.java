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
package org.apache.sling.graphql.core.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import javax.inject.Inject;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class GraphQLScriptEngineIT extends GraphQLCoreTestSupport {

    @Inject
    @Filter(value = "(names=graphql)")
    protected ScriptEngineFactory graphQLScriptEngine;

    @Inject
    @Filter(value = "(mimeTypes=application/graphql)")
    protected ScriptEngineFactory graphQLScriptEngineByMimeType;

    @Configuration
    public Option[] configuration() {
        return new Option[] { baseConfiguration() };
    }

    @Test
    public void testEnginePresent() throws ScriptException {
        assertNotNull("Expecting ScriptEngineFactory to be present", graphQLScriptEngine);
        final ScriptEngine engine = graphQLScriptEngine.getScriptEngine();
        assertNotNull("Expecting ScriptEngine to be provided", engine);
        assertEquals("Expecting our GraphQLScriptEngine", "GraphQLScriptEngine", engine.getClass().getSimpleName());
        assertEquals("Expected to get the GraphQLScriptEngine when filtering by mime type.", graphQLScriptEngine,
                graphQLScriptEngineByMimeType);
    }
}
