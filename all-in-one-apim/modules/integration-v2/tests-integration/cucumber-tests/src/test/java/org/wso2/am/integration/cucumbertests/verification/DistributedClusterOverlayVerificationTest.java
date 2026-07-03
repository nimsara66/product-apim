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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.integration.cucumbertests.utils.DistributedClusterConfig;
import org.wso2.am.integration.cucumbertests.utils.DistributedDbScripts;
import org.wso2.am.integration.cucumbertests.utils.ModulePathResolver;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.am.integration.cucumbertests.utils.clients.SimpleHTTPClient;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.am.testcontainers.DistributedApimCluster;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import java.net.URI;

/**
 * Phase 5 (T5.1) verification: proves the distributed component configs are produced by merging small role
 * overlays ONTO the component distribution TOMLs (not full-file copies), and that a cluster booted from the
 * merged result behaves identically to the Phase 4 (bundled-TOML) cluster.
 *
 * <p>Two checks: (1) merge correctness — each merged TOML contains BOTH the overlay's cluster wiring
 * (alias hostname, mysql datasource, event-hub on acp) AND keys inherited from the distribution that the
 * overlay never states (e.g. the TLS keystore); (2) parity — the cluster boots from the merged TOMLs and
 * both planes answer (ACP servlet Version + GW management health-check).
 */
public class DistributedClusterOverlayVerificationTest {

    private static final Log logger = LogFactory.getLog(DistributedClusterOverlayVerificationTest.class);

    @Test
    public void verifyOverlayMergeAndBoot() throws Exception {

        String moduleDir = ModulePathResolver.getModuleDir(DistributedClusterOverlayVerificationTest.class);
        DistributedClusterConfig.Tomls tomls = DistributedClusterConfig.resolve(moduleDir);

        // 1) merge correctness — re-parse the merged TOML (format-agnostic) and assert on node values:
        //    overlay wiring is applied AND distribution defaults the overlay never states are inherited.
        TomlMapper mapper = new TomlMapper();
        JsonNode acp = mapper.readTree(tomls.acp);
        Assert.assertEquals(acp.at("/server/hostname").asText(), "acp", "ACP overlay hostname not applied");
        Assert.assertTrue(acp.at("/database/apim_db/url").asText().contains("mysql:3306/WSO2AM_DB"),
                "ACP DB wiring missing");
        Assert.assertEquals(acp.at("/apim/event_hub/event_listening_endpoints/0").asText(), "tcp://acp:5672",
                "ACP event-hub wiring missing");
        // keystore.tls is a distribution default the overlay never states — proves inheritance (not full-copy).
        Assert.assertTrue(acp.at("/keystore/tls/file_name").asText().contains("wso2carbon.jks"),
                "ACP merged TOML did not inherit the distribution keystore — overlay not merged onto distribution?");

        JsonNode gw = mapper.readTree(tomls.gw);
        Assert.assertEquals(gw.at("/server/hostname").asText(), "gateway", "GW overlay hostname not applied");
        Assert.assertEquals(gw.at("/apim/throttling/url_group/0/traffic_manager_urls/0").asText(),
                "tcp://trafficmanager:9611", "GW throttling wiring missing");

        JsonNode tm = mapper.readTree(tomls.tm);
        Assert.assertEquals(tm.at("/server/hostname").asText(), "trafficmanager", "TM overlay hostname not applied");

        // 2) parity — boot a cluster from the MERGED tomls and confirm both planes come up.
        DistributedDbScripts.Ddl ddl = DistributedDbScripts.resolve(moduleDir);
        DistributedApimCluster cluster = new DistributedApimCluster(
                "verify-5", tomls.acp, tomls.tm, tomls.gw, ddl.shared, ddl.apim);
        try {
            cluster.start();

            // accessor contract: every URL well-formed, and servlet (ACP:9443) vs gateway-mgmt (GW:9443) map
            // to DISTINCT host ports — proving they are separate containers (the core distributed distinction).
            for (String url : new String[]{cluster.getServletHttpsUrl(), cluster.getServletHttpUrl(),
                    cluster.getGatewayHttpsUrl(), cluster.getGatewayHttpUrl(), cluster.getGatewayMgmtHttpsUrl()}) {
                URI parsed = URI.create(url);
                Assert.assertNotNull(parsed.getHost(), "URL has no host: " + url);
                Assert.assertTrue(parsed.getPort() > 0, "URL has no port: " + url);
            }
            Assert.assertNotEquals(URI.create(cluster.getServletHttpsUrl()).getPort(),
                    URI.create(cluster.getGatewayMgmtHttpsUrl()).getPort(),
                    "ACP servlet and GW management mapped to the same host port (not separate containers?)");

            Assert.assertTrue(pollUntil200(cluster.getServletHttpsUrl() + "services/Version"),
                    "ACP servlet not ready when booted from overlay-merged config");
            Assert.assertTrue(pollUntil200(Utils.getGatewayHealthCheckURL(cluster.getGatewayMgmtHttpsUrl())),
                    "GW health-check not ready when booted from overlay-merged config");
            logger.info("Phase 5 assertions passed: overlays merged onto distribution and cluster booted "
                    + "(servlet=" + cluster.getServletHttpsUrl() + " gatewayMgmt=" + cluster.getGatewayMgmtHttpsUrl() + ")");
        } finally {
            cluster.stop();
        }
    }

    private boolean pollUntil200(String url) {
        long deadline = System.currentTimeMillis() + Constants.SERVER_STARTUP_WAIT_TIME;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse response = SimpleHTTPClient.getInstance().doGet(url, null);
                if (response != null && response.getResponseCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {
                // not ready yet
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
