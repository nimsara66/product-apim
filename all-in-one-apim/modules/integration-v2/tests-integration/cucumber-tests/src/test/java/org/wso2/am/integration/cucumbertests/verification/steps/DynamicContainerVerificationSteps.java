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

package org.wso2.am.integration.cucumbertests.verification.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.integration.cucumbertests.utils.ModulePathResolver;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.am.testcontainers.DynamicApimContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Phase 1.3 verification step definitions. Boots a {@link DynamicApimContainer} (dynamic/ephemeral
 * host ports) and wires its mapped servlet-https / gateway-https URLs into the shared test context
 * so the existing publisher/devportal/invocation steps drive a full create -> deploy -> publish ->
 * subscribe -> token -> invoke lifecycle through dynamic ports. Kept in an isolated glue package so
 * the legacy lanes are untouched.
 */
public class DynamicContainerVerificationSteps {

    private static final Logger logger = LoggerFactory.getLogger(DynamicContainerVerificationSteps.class);
    private static final String CONTAINER_KEY = "dynamicApimContainer";
    private static final String VERIFY_LABEL_KEY = "verify-step";
    private static final String VERIFY_LABEL_VALUE = "1.3";

    private final String callerModuleDir = ModulePathResolver.getModuleDir(DynamicContainerVerificationSteps.class);

    @Given("I have initialized a dynamic API Manager container with label {string}")
    public void initializeDynamicAPIMContainer(String label) throws IOException {

        String baseTomlPath = Paths.get(callerModuleDir, Constants.DEFAULT_TOML_PATH).toString();
        String tomlContent = Files.readString(Path.of(baseTomlPath));

        DynamicApimContainer container = new DynamicApimContainer(label, tomlContent);
        container.withLabel(VERIFY_LABEL_KEY, VERIFY_LABEL_VALUE);
        container.start();

        TestContext.setShared(CONTAINER_KEY, container);
        TestContext.setShared("baseUrl", container.getServletHttpsUrl());
        TestContext.setShared("baseGatewayUrl", container.getGatewayHttpsUrl());
        TestContext.setShared("label", label);

        logger.info("Dynamic APIM container '{}' started: baseUrl={} baseGatewayUrl={}",
                label, container.getServletHttpsUrl(), container.getGatewayHttpsUrl());
    }

    @Then("I stop the dynamic API Manager container")
    public void stopDynamicAPIMContainer() throws IOException {

        DynamicApimContainer container = (DynamicApimContainer) TestContext.get(CONTAINER_KEY);
        if (container == null) {
            return;
        }
        // Record the servlet-https host:port before stopping so verify-1.3.sh can assert release.
        String hostPort = container.getHost() + ":" + container.getMappedPort(Constants.HTTPS_PORT);
        Files.writeString(Paths.get(callerModuleDir, "target", "verify-1.3-servlet-https-port.txt"), hostPort);
        container.stop();
        logger.info("Dynamic APIM container stopped (servlet-https was {})", hostPort);
    }
}
