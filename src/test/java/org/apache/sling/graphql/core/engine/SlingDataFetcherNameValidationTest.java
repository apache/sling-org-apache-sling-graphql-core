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
package org.apache.sling.graphql.core.engine;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SlingDataFetcherNameValidationTest {
    
    @Parameters(name="{0}")
    public static Collection<Object[]> data() {
        final List<Object[]> result = new ArrayList<>();
        result.add(new Object[] {"with/slash", true});
        result.add(new Object[] {"missingSlash", false});
        result.add(new Object[] {"", false});
        result.add(new Object[] {"one/two/three", true});
        result.add(new Object[] {"one/two/three/four_and/five_and_451_six_6", true});
        result.add(new Object[] {"uno/1/x42", true});
        result.add(new Object[] {"uno_due/tre", true});
        result.add(new Object[] {"the:colon/bad", false});
        return result;
    }

    private final String name;
    private final boolean expectValid;

    public SlingDataFetcherNameValidationTest(String name, Boolean expectValid) {
        this.name = name;
        this.expectValid = expectValid;
    }

    @Test
    public void testValidation() {
        boolean valid = true;
        try {
            GraphQLResourceQuery.validateFetcherName(name);
        } catch(IOException ioe) {
            valid = false;
        }
        final String msg = String.format("Expecting '%s' to be %s", name, expectValid ? "valid" : "invalid");
        assertEquals(msg, valid, expectValid);
    }

}