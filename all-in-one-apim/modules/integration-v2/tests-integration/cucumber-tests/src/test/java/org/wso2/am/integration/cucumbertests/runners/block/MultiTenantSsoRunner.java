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
 * Runner for the multi-tenant portal SSO block (testng-is7sso.xml). Boots a dedicated APIM + external IS 7.x
 * with the multi-tenant tenant_context / select-tenant configuration, then runs the {@code _setup_} fixture
 * pattern: {@code _setup_multitenant_sso} (listed first, provisions the nested-OIDC broker topology once) then
 * {@code multitenant_sso} (drives the headless Publisher login and asserts it succeeds). The fixture is torn
 * down once by {@link BaseBlockRunner}'s AfterClass sweep. Its own block/suite because the multi-tenant config
 * is behaviour-changing (tenant-qualified URLs, tenanted sessions, disabled default JIT handler, unauthenticated
 * select-tenant page) and must not affect the other lanes.
 */
@CucumberOptions(
        features = {
                "src/test/resources/features/admin/_setup_multitenant_sso.feature",
                "src/test/resources/features/admin/multitenant_sso.feature"
        },
        glue = {
                "org.wso2.am.integration.cucumbertests.stepdefinitions"
        },
        plugin = {"pretty", "html:target/cucumber-report/multitenant-sso.html"}
)
public class MultiTenantSsoRunner extends BaseBlockRunner {
}
