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
 * Workshop runner 2 - the same happy path as {@link WorkshopRestInvocationRunner}, driven as a Scenario
 * Outline across the super tenant and tenant1.com. Identical to runner 1 apart from the feature it names:
 * running a flow in a second tenant is a feature-level change, not a runner or glue change.
 *
 * <p>Teaching-only; delete along with {@code features/workshop/} after the session.
 */
@CucumberOptions(
        features = "src/test/resources/features/workshop/rest_invocation_tenants.feature",
        glue = {
                "org.wso2.am.integration.cucumbertests.stepdefinitions"
        },
        plugin = {"pretty", "html:target/cucumber-report/workshop-rest-invocation-tenants.html"}
)
public class WorkshopRestInvocationTenantsRunner extends BaseBlockRunner {
}
