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

package org.wso2.am.integration.cucumbertests.runners.block;

import io.cucumber.testng.CucumberOptions;

/**
 * Workshop runner 4 - invocation with an access token minted by an external WSO2 Identity Server 7.x,
 * registered as a third-party key manager.
 *
 * <p>Note what is NOT here: no IS wiring, no truststore handling, no key-manager registration. Booting IS
 * and establishing cert trust are block infrastructure (suite parameters read by
 * {@code BlockLifecycleListener}), and registering the key manager is product behaviour performed by a step
 * in the feature. The runner stays the same three lines as every other one.
 *
 * <p>Teaching-only; delete along with {@code features/workshop/} after the session.
 */
@CucumberOptions(
        features = "src/test/resources/features/workshop/is7_external_idp_invocation.feature",
        glue = {
                "org.wso2.am.integration.cucumbertests.stepdefinitions"
        },
        plugin = {"pretty", "html:target/cucumber-report/workshop-is7-invocation.html"}
)
public class WorkshopIs7InvocationRunner extends BaseBlockRunner {
}
