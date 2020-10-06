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

import static org.junit.Assert.fail;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.graphql.api.SlingDataFetcher;
import org.apache.sling.graphql.api.SlingScalarConverter;
import org.apache.sling.graphql.api.SlingTypeResolver;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class TestUtil {
    public static ServiceRegistration<?> registerSlingDataFetcher(BundleContext bc, String name, SlingDataFetcher<?> f) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(SlingDataFetcher.NAME_SERVICE_PROPERTY, name);
        return bc.registerService(SlingDataFetcher.class, f, props);
    }

    public static ServiceRegistration<?> registerSlingTypeResolver(BundleContext bc, String name, SlingTypeResolver<?> f) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(SlingTypeResolver.NAME_SERVICE_PROPERTY, name);
        return bc.registerService(SlingTypeResolver.class, f, props);
    }

    public static ServiceRegistration<?> registerSlingScalarConverter(BundleContext bc, String name, SlingScalarConverter<?,?> c) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(SlingScalarConverter.NAME_SERVICE_PROPERTY, name);
        return bc.registerService(SlingScalarConverter.class, c, props);
    }

    public static void assertNestedException(Throwable t, Class<?> clazz, String messageContains) {
        boolean found = false;
        while(t != null) {
            if(t.getClass().equals(clazz) && t.getMessage().contains(messageContains)) {
                found = true;
                break;
            }
            t = t.getCause();
        }
        if(!found) {
            fail(String.format("Did not get %s exception with message containing '%s'", 
                clazz.getName(), messageContains));
        }
    }
}