/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.wso2.am.integration.cucumbertests.verification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.integration.cucumbertests.utils.ModulePathResolver;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.am.integration.cucumbertests.utils.clients.SimpleHTTPClient;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.am.testcontainers.DynamicApimContainer;
import org.wso2.am.testcontainers.JacocoCoverage;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * PoC Phase 1 (spike) for v2 integration coverage. Proves the full JaCoCo chain end-to-end on the
 * all-in-one lane: boot {@link DynamicApimContainer} with the agent attached ({@link DynamicApimContainer#withCoverage()}),
 * exercise the server a little (startup + health-check), dump the counters over the mapped tcpserver port,
 * and render a scoped report from the distribution class files — asserting non-zero {@code org.wso2.carbon.apimgt.*}
 * coverage. This is isolated (does not touch the production {@code BlockLifecycleListener}); it is the proof
 * that Phase 2 (listener wiring) builds on. See docs/devs/v2-coverage-architecture.md.
 *
 * <p>Run:
 * {@code mvn -pl tests-integration/cucumber-tests -am -Dsurefire.suite.xml=testng-fv-coverage.xml test}
 * (optionally {@code -Dapim.coverage.sources=<carbon-apimgt checkout>} once that sys-prop is forwarded).
 */
public class JacocoCoverageSpikeVerificationTest {

    private static final Log logger = LogFactory.getLog(JacocoCoverageSpikeVerificationTest.class);

    @Test
    public void verifyCoverageInstrumentDumpReport() throws Exception {

        String moduleDir = ModulePathResolver.getModuleDir(JacocoCoverageSpikeVerificationTest.class);
        String tomlContent = Utils.resolveDefaultToml(moduleDir);

        // Output layout mirrors the design doc (coverage/output/...), rooted under target for cleanliness.
        File covRoot = Paths.get(moduleDir, "target", "coverage").toFile();
        File execFile = new File(covRoot, "verify-cov.exec");
        File classfilesDir = new File(covRoot, "classfiles");
        File xmlOut = new File(covRoot, "output/jacoco-it.xml");
        File htmlDir = new File(covRoot, "output/html");

        DynamicApimContainer container = new DynamicApimContainer("verify-cov", tomlContent).withCoverage();
        container.withLabel("verify-step", "coverage");

        try {
            container.start();

            // Exercise the server: wait until the health-check returns 200 (also drives apimgt webapp code).
            String healthUrl = Utils.getGatewayHealthCheckURL(container.getServletHttpsUrl());
            Assert.assertTrue(pollUntil200(healthUrl), "server never became healthy at " + healthUrl);
            // A couple more hits to accumulate coverage.
            safeGet(container.getServletHttpsUrl() + "services/Version");
            safeGet(healthUrl);

            // Dump coverage from the live container BEFORE stopping it.
            JacocoCoverage.dump(container.getCoverageDumpHost(), container.getCoverageDumpPort(), execFile);
        } finally {
            container.stop();
        }

        Assert.assertTrue(execFile.exists() && execFile.length() > 0, "no coverage .exec was produced");

        // Class files = APIM plugin jars from the SAME distribution zip the image was built from.
        String serverName = System.getProperty("apim.server.name"); // e.g. wso2am-4.7.0-SNAPSHOT
        File distZip = Paths.get(moduleDir, "..", "..", "..", "distribution", "product", "target",
                serverName + ".zip").normalize().toFile();
        Assert.assertTrue(distZip.exists(), "distribution zip not found: " + distZip);
        List<File> classfileRoots = JacocoCoverage.extractApimgtClassfiles(distZip, classfilesDir);

        // Sources are best-effort and optional (line highlighting only). Forwarded via -Dapim.coverage.sources.
        List<File> sourceRoots = new ArrayList<>();
        String srcProp = System.getProperty("apim.coverage.sources");
        if (srcProp != null && !srcProp.isBlank()) {
            sourceRoots = JacocoCoverage.discoverSourceRoots(new File(srcProp));
        }

        double linePct = JacocoCoverage.report(List.of(execFile), classfileRoots, sourceRoots,
                xmlOut, htmlDir, "apim-integration");

        Assert.assertTrue(xmlOut.exists() && xmlOut.length() > 0, "no jacoco XML produced");
        Assert.assertTrue(new File(htmlDir, "index.html").exists(), "no HTML report produced");
        Assert.assertTrue(linePct > 0.0,
                "expected non-zero APIM line coverage from the instrumented run, got " + linePct + "%");

        logger.info("PoC Phase 1 PASS: APIM integration line coverage = " + String.format("%.2f", linePct)
                + "%  (xml=" + xmlOut + ", html=" + htmlDir + "/index.html)");
    }

    private boolean pollUntil200(String url) {
        long deadline = System.currentTimeMillis() + Constants.SERVER_STARTUP_WAIT_TIME;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse r = safeGet(url);
            if (r != null && r.getResponseCode() == 200) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private HttpResponse safeGet(String url) {
        try {
            return SimpleHTTPClient.getInstance().doGet(url, null);
        } catch (Exception e) {
            return null;
        }
    }
}
