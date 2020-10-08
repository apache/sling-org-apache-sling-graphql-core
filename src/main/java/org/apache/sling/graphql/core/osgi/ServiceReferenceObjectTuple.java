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

import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.osgi.framework.ServiceReference;

public class ServiceReferenceObjectTuple<T> implements Comparable<ServiceReferenceObjectTuple<T>> {

    private final ServiceReference<T> serviceReference;
    private final T serviceObject;

    public ServiceReferenceObjectTuple(@NotNull ServiceReference<T> serviceReference, @NotNull T serviceObject) {
        this.serviceReference = serviceReference;
        this.serviceObject = serviceObject;
    }

    public ServiceReference<T> getServiceReference() {
        return serviceReference;
    }

    public T getServiceObject() {
        return serviceObject;
    }

    @Override
    public int compareTo(@NotNull ServiceReferenceObjectTuple<T> o) {
        return serviceReference.compareTo(o.serviceReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceReference, serviceObject);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ServiceReferenceObjectTuple) {
            ServiceReferenceObjectTuple<T> other = (ServiceReferenceObjectTuple<T>) obj;
            if (this == other) {
                return true;
            }
            return Objects.equals(serviceReference, other.serviceReference) && Objects.equals(serviceObject, other.serviceObject);
        }
        return false;
    }
}
