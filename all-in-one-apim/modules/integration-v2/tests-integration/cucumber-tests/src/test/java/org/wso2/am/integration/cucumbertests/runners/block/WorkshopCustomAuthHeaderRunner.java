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
 * Workshop runner 3 - the same lifecycle arc as {@link WorkshopRestInvocationRunner}, but run against a
 * block whose container boots with a feature-specific TOML overlay
 * ({@code artifacts/configFiles/workshop/deployment.toml}, wired via the {@code tomlExtraOverlayPath}
 * block parameter in {@code testng-workshop-overlay.xml}).
 *
 * <p>The runner itself is unchanged from the other two: server configuration is a property of the BLOCK,
 * declared in the suite XML, not of the runner or the glue.
 *
 * <p>Teaching-only; delete along with {@code features/workshop/} after the session.
 */
@CucumberOptions(
        features = "src/test/resources/features/workshop/custom_auth_header.feature",
        glue = {
                "org.wso2.am.integration.cucumbertests.stepdefinitions"
        },
        plugin = {"pretty", "html:target/cucumber-report/workshop-custom-auth-header.html"}
)
public class WorkshopCustomAuthHeaderRunner extends BaseBlockRunner {
}
