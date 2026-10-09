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
 */

package org.wso2.am.integration.cucumbertests.runners.block;

import io.cucumber.testng.CucumberOptions;

/**
 * Re-runs the existing FederatedUserJWTTestCase coverage with OAuth token persistence enabled. The legacy tag
 * selects its password-grant profile-claim examples and external OIDC authorization-code examples from their
 * existing features, without pulling unrelated backend-JWT scenarios into this configuration block.
 */
@CucumberOptions(
        features = {
                "src/test/resources/features/key-manager/backend_jwt.feature",
                "src/test/resources/features/key-manager/federated_backend_jwt.feature"
        },
        tags = "@legacy:FederatedUserJWTTestCase",
        glue = {"org.wso2.am.integration.cucumbertests.stepdefinitions"},
        plugin = {"pretty", "html:target/cucumber-report/federated-user-jwt-token-persistence.html"}
)
public class FederatedUserJwtTokenPersistenceRunner extends BaseBlockRunner {
}
