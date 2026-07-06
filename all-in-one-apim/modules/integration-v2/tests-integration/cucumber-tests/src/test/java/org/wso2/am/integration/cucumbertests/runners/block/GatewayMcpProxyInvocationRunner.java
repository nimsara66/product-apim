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
 * Runner for gateway MCP-server proxy invocation — ports the proxy-subtype path of MCPServerTestCase against a
 * REAL session-stateful MCP server (official SDK), verifying the gateway proxies the MCP JSON-RPC handshake +
 * session state end-to-end. Runs in a gateway-invoking block (needs the node mcp-server backend).
 */
@CucumberOptions(
        features = {
                "src/test/resources/features/gateway/mcp_proxy_invocation.feature"
        },
        glue = {
                "org.wso2.am.integration.cucumbertests.stepdefinitions"
        },
        plugin = {"pretty", "html:target/cucumber-report/gateway-mcp-proxy-invocation.html"}
)
public class GatewayMcpProxyInvocationRunner extends BaseBlockRunner {
}
