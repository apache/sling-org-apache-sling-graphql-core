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

package org.apache.sling.graphql.core.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

/** Sanitize strings for logging */
public class LogSanitizer {

    /** Character that replaces unwanted ones in input */
    public static final char REPLACEMENT_CHAR = '_';

    public String sanitize(String input) {
        if(input == null) {
            return null;
        }
        final StringBuilder result = new StringBuilder(input.length());
        final StringCharacterIterator it = new StringCharacterIterator(input);
        for(char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            result.append(accept(c) ? c : REPLACEMENT_CHAR);
        }
        return result.toString();
    }

    /**  Reject non US-ASCII characters as well as
     *  control chars which are not end-of-line chars.
     */
    private static boolean accept(char c) {
        if(c > 0x7f) {
            return false;
        } else if(c == '\n' || c == '\r')  {
            return true;
        } else if(Character.isISOControl(c)) {
            return false;
        }
        return true;
    }
}
