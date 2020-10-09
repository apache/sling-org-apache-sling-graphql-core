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
package org.apache.sling.graphql.core.osgi;

import java.util.HashMap;
import java.util.TreeSet;

import javax.servlet.Servlet;

import org.apache.sling.testing.mock.osgi.junit.OsgiContext;
import org.junit.Rule;
import org.junit.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ServiceReferenceObjectTupleTest {

    @Rule
    public OsgiContext osgiContext = new OsgiContext();

    @Test
    public void testEqualsAndOrdering() throws InvalidSyntaxException {
        Servlet servlet1 = mock(Servlet.class);
        Servlet servlet2 = mock(Servlet.class);
        osgiContext.registerService(Servlet.class, servlet1, Constants.SERVICE_RANKING, 1);
        osgiContext.registerService(Servlet.class, servlet2, Constants.SERVICE_RANKING, 2);

        ServiceReference<Servlet> sr1 =
                osgiContext.bundleContext().getServiceReferences(Servlet.class, "(service.ranking=1)").stream().findFirst().orElse(null);

        ServiceReference<Servlet> sr2 =
                osgiContext.bundleContext().getServiceReferences(Servlet.class, "(service.ranking=2)").stream().findFirst().orElse(null);

        ServiceReference<Servlet> sr3 =
                osgiContext.bundleContext().getServiceReferences(Servlet.class, "(service.ranking=1)").stream().findFirst().orElse(null);

        ServiceReferenceObjectTuple<Servlet> t1 = new ServiceReferenceObjectTuple(sr1, servlet1);
        ServiceReferenceObjectTuple<Servlet> t3 = new ServiceReferenceObjectTuple(sr1, servlet1);
        ServiceReferenceObjectTuple<Servlet> t2 = new ServiceReferenceObjectTuple(sr2, servlet2);

        assertNotEquals(t1, t2);
        assertEquals(t1, t3);

        TreeSet<ServiceReferenceObjectTuple<Servlet>> treeSet = new TreeSet<>();
        assertTrue(treeSet.add(t1));
        assertTrue(treeSet.add(t2));
        assertFalse(treeSet.add(t3));

        HashMap<ServiceReferenceObjectTuple<Servlet>, Integer> hashMap = new HashMap<>();
        assertNull(hashMap.put(t1, 1));
        assertNull(hashMap.put(t2, 2));
        Integer previous = hashMap.put(t3, 3);
        assertTrue(previous != null && previous == 1);
    }

}
