/*
 *  Copyright (c) 2026, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

package org.wso2.am.integration.cucumbertests.stepdefinitions;

import io.cucumber.java.en.Given;
import org.testng.Assert;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.am.testcontainers.ApimRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Readiness for the asynchronous throttle event path. Carbon's HTTP readiness page only proves that a component
 * is serving requests; it does not prove that the Traffic Manager event publisher or the Gateway JMS consumer has
 * finished binding to {@code throttleData}. This gate makes that prerequisite explicit before an enforcement
 * scenario starts sending requests.
 *
 * <p>The positive markers are emitted by the product itself: the Traffic Manager's event-publisher deployer marks
 * {@code jmsEventPublisher2} active, and the shared JMS listener reports that it has started listening to the
 * {@code throttleData} destination. A failed registry path or broker binding is retained as diagnostic evidence,
 * but is not treated as an immediate failure because the product may still be retrying its startup binding. If the
 * positive markers never arrive, the step fails with the relevant component log evidence.</p>
 */
public class ThrottlingReadinessSteps {

    private static final String SERVER_LOG = "wso2carbon.log";
    private static final Pattern TM_PUBLISHER_READY = Pattern.compile(
            "Event Publisher configuration successfully deployed and in active state\\s*:\\s*jmsEventPublisher2",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GATEWAY_CONSUMER_READY = Pattern.compile(
            "Started to listen on destination\\s*:\\s*throttleData\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern THROTTLE_FAILURE = Pattern.compile(
            "REG_PATH|permission denied.*(?:binding|throttledata)|(?:binding|throttledata).*permission denied",
            Pattern.CASE_INSENSITIVE);

    @Given("the Traffic Manager throttleData publisher and Gateway throttleData consumer are ready within {int} seconds")
    public void throttleDataPipelineIsReady(int timeoutSeconds) throws InterruptedException {
        ApimRuntime runtime = runtime();
        Readiness last = Utils.retryUntil(timeoutSeconds * 1000L,
                () -> inspect(runtime), Readiness::ready);

        Assert.assertNotNull(last, "No throttleData readiness sample was obtained");
        Assert.assertTrue(last.ready(), "The throttleData pipeline did not become ready. " + last.diagnostics());
    }

    private Readiness inspect(ApimRuntime runtime) {
        String tmLog = runtime.readTrafficManagerLogFile(SERVER_LOG);
        String gatewayLog = runtime.readGatewayLogFile(SERVER_LOG);
        boolean tmReady = TM_PUBLISHER_READY.matcher(tmLog).find();
        boolean gatewayReady = GATEWAY_CONSUMER_READY.matcher(gatewayLog).find();
        return new Readiness(tmReady, gatewayReady, diagnostics("Traffic Manager", tmLog)
                + diagnostics("Gateway", gatewayLog));
    }

    private static String diagnostics(String component, String log) {
        List<String> evidence = new ArrayList<>();
        for (String line : log.split("\\R")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("jmsEventPublisher2".toLowerCase(Locale.ROOT))
                    || lower.contains("throttledata")
                    || THROTTLE_FAILURE.matcher(line).find()
                    || lower.contains("event publisher")) {
                evidence.add(line.trim());
            }
        }
        int from = Math.max(0, evidence.size() - 8);
        StringBuilder result = new StringBuilder(" ").append(component).append(" marker=")
                .append(evidence.isEmpty() ? "not observed" : "observed");
        for (int i = from; i < evidence.size(); i++) {
            String line = evidence.get(i);
            result.append("\n  ").append(line, 0, Math.min(line.length(), 500));
        }
        return result.toString();
    }

    private static ApimRuntime runtime() {
        Object candidate = TestContext.get("blockApimContainer");
        if (!(candidate instanceof ApimRuntime)) {
            throw new IllegalStateException("Block APIM container is not available in the test context");
        }
        return (ApimRuntime) candidate;
    }

    private record Readiness(boolean trafficManagerReady, boolean gatewayReady, String diagnostics) {
        private boolean ready() {
            return trafficManagerReady && gatewayReady;
        }
    }
}
