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

import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.johnzon.mapper.Mapper;
import org.apache.johnzon.mapper.MapperBuilder;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.engine.SlingRequestProcessor;
import org.apache.sling.graphql.core.mocks.QueryDataFetcherComponent;
import org.apache.sling.graphql.core.mocks.TestDataFetcherComponent;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.apache.sling.servlethelpers.internalrequests.SlingInternalRequest;
import org.apache.sling.testing.paxexam.TestSupport;
import org.junit.Before;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.options.ModifiableCompositeOption;
import org.ops4j.pax.exam.options.extra.VMOption;
import org.ops4j.pax.tinybundles.core.TinyBundle;
import org.osgi.framework.Constants;

import static org.apache.sling.testing.paxexam.SlingOptions.slingCommonsMetrics;
import static org.apache.sling.testing.paxexam.SlingOptions.slingQuickstartOakTar;
import static org.apache.sling.testing.paxexam.SlingOptions.slingResourcePresence;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScripting;
import static org.apache.sling.testing.paxexam.SlingOptions.slingScriptingJsp;
import static org.junit.Assert.fail;
import static org.ops4j.pax.exam.CoreOptions.composite;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.cm.ConfigurationAdminOptions.newConfiguration;

public abstract class GraphQLCoreTestSupport extends TestSupport {

    private final static int STARTUP_WAIT_SECONDS = 30;

    @Inject
    protected ResourceResolverFactory resourceResolverFactory;

    @Inject
    protected SlingRequestProcessor requestProcessor;

    public ModifiableCompositeOption baseConfiguration() {
        final String vmOpt = System.getProperty("pax.vm.options");
        VMOption vmOption = null;
        if (StringUtils.isNotEmpty(vmOpt)) {
            vmOption = new VMOption(vmOpt);
        }

        final String jacocoOpt = System.getProperty("jacoco.command");
        VMOption jacocoCommand = null;
        if (StringUtils.isNotEmpty(jacocoOpt)) {
            jacocoCommand = new VMOption(jacocoOpt);
        }

        return composite(
            when(vmOption != null).useOptions(vmOption),
            when(jacocoCommand != null).useOptions(jacocoCommand),
            super.baseConfiguration(),
            slingQuickstart(),
            graphQLJava(),
            testBundle("bundle.filename"),
            newConfiguration("org.apache.sling.jcr.base.internal.LoginAdminWhitelist")
                .put("whitelist.bundles.regexp", "^PAXEXAM.*$")
                .asOption(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.servlet-helpers").versionAsInProject(),
            mavenBundle().groupId("org.apache.sling").artifactId("org.apache.sling.commons.johnzon").versionAsInProject(),
            mavenBundle().groupId("org.apache.johnzon").artifactId("johnzon-mapper").versionAsInProject(),
            slingResourcePresence(),
            slingCommonsMetrics(),
            jsonPath(),
            junitBundles()
        );
    }

    @ProbeBuilder
    public TestProbeBuilder probeConfiguration(final TestProbeBuilder testProbeBuilder) {
        testProbeBuilder.setHeader("Sling-Initial-Content", "initial-content");
        return testProbeBuilder;
    }

    protected Option testDataFetchers() {
        return buildBundleWithBnd(TestDataFetcherComponent.class, QueryDataFetcherComponent.class);
    }

    protected Option slingQuickstart() {
        final int httpPort = findFreePort();
        final String workingDirectory = workingDirectory();
        return composite(
            slingQuickstartOakTar(workingDirectory, httpPort),
            slingScripting(),
            slingScriptingJsp()
        );
    }

    protected Option jsonPath() {
        return composite(
            mavenBundle().groupId("com.jayway.jsonpath").artifactId("json-path").versionAsInProject(),
            mavenBundle().groupId("net.minidev").artifactId("json-smart").versionAsInProject(),
            mavenBundle().groupId("net.minidev").artifactId("accessors-smart").versionAsInProject(),
            mavenBundle().groupId("org.ow2.asm").artifactId("asm").versionAsInProject(),
            mavenBundle().groupId("com.jayway.jsonpath").artifactId("json-path-assert").versionAsInProject(),
            mavenBundle().groupId("org.apache.servicemix.bundles").artifactId("org.apache.servicemix.bundles.hamcrest").versionAsInProject()
        );
    }

    protected Option graphQLJava() {
        return composite(
            mavenBundle().groupId("com.graphql-java").artifactId("graphql-java").versionAsInProject(),
            mavenBundle().groupId("org.antlr").artifactId("antlr4-runtime").versionAsInProject(),
            mavenBundle().groupId("com.graphql-java").artifactId("java-dataloader").versionAsInProject(),
            mavenBundle().groupId("org.reactivestreams").artifactId("reactive-streams").versionAsInProject()
        );
    }

    /**
     * Injecting the appropriate services to wait for would be more elegant but this is very reliable..
     */
    @Before
    public void waitForSling() throws Exception {
        final int expectedStatus = 200;
        final List<Integer> statuses = new ArrayList<>();
        final String path = "/.json";
        final Instant endTime = Instant.now().plus(Duration.ofSeconds(STARTUP_WAIT_SECONDS));

        while(Instant.now().isBefore(endTime)) {
            final int status = executeRequest("GET", path, null, null, null, -1).getStatus();
            statuses.add(status);
            if (status == expectedStatus) {
                return;
            }
            Thread.sleep(250);
        }

        fail("Did not get a " + expectedStatus + " status at " + path + " got " + statuses);
    }

    protected MockSlingHttpServletResponse executeRequest(final String method, 
        final String path, Map<String, Object> params, String contentType, 
        Reader body, final int expectedStatus) throws Exception {

        // Admin resolver is fine for testing    
        @SuppressWarnings("deprecation")            
        final ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);

        final int [] statusParam = expectedStatus == -1 ? null : new int[] { expectedStatus };

        return (MockSlingHttpServletResponse)
            new SlingInternalRequest(resourceResolver, requestProcessor, path)
            .withRequestMethod(method)
            .withParameters(params)
            .withContentType(contentType)
            .withBody(body)
            .execute()
            .checkStatus(statusParam)
            .getResponse()
            ;
    }

    protected String getContent(String path) throws Exception {
        return executeRequest("GET", path, null, null, null, 200).getOutputAsString();
    }

    protected String getContent(String path, String ... params) throws Exception {
        return executeRequest("GET", path, toMap(params), null, null, 200).getOutputAsString();
    }

    protected String getContentWithPost(String path, String query, Map<String, Object> variables) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (query != null) {
            String queryEncoded = query.replace("\n", "\\n");
            body.put("query", queryEncoded);
        }
        if (variables != null) {
            body.put("variables", variables);
        }

        return executeRequest("POST", path, null, "application/json", new StringReader(toJSON(body)), 200).getOutputAsString();
    }

    protected MockSlingHttpServletResponse persistQuery(String path, String query, Map<String, Object> variables) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (query != null) {
            String queryEncoded = query.replace("\n", "\\n");
            body.put("query", queryEncoded);
        }
        if (variables != null) {
            body.put("variables", variables);
        }

        return executeRequest("POST", path + "/persisted", null, "application/json", new StringReader(toJSON(body)), -1);
    }

    protected String toJSON(Object source) {
        Mapper mapper = new MapperBuilder().build();
        return mapper.toStructure(source).toString();
    }

    protected Map<String, Object> toMap(String ...keyValuePairs) {
        final Map<String, Object> result = new HashMap<>();
        for(int i=0 ; i < keyValuePairs.length; i+=2) {
            result.put(keyValuePairs[i], keyValuePairs[i+1]);
        }
        return result;
    }

    protected Option buildBundleWithExportedPackages(final Class<?>... classes) {
        final TinyBundle bundle = org.ops4j.pax.tinybundles.core.TinyBundles.bundle();
        bundle.set(Constants.BUNDLE_SYMBOLICNAME, getClass().getSimpleName() + UUID.randomUUID());

        final StringBuilder exports = new StringBuilder();
        
        for (final Class<?> clazz : classes) {
            bundle.add(clazz);
            if(exports.length() != 0) {
                exports.append(",");
            }
            exports.append(clazz.getPackage().getName());
        }

        bundle.set(Constants.EXPORT_PACKAGE, exports.toString());
        return streamBundle(bundle.build()).start();
    }
}
