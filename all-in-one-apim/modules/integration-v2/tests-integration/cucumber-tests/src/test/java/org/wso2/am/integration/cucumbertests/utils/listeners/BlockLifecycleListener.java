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

package org.wso2.am.integration.cucumbertests.utils.listeners;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.xml.XmlTest;
import org.wso2.am.integration.cucumbertests.utils.CoverageSupport;
import org.wso2.am.integration.cucumbertests.utils.DistributedClusterConfig;
import org.wso2.am.integration.cucumbertests.utils.DistributedDbScripts;
import org.wso2.am.integration.cucumbertests.utils.ModulePathResolver;
import org.wso2.am.integration.cucumbertests.utils.ServerReadiness;
import org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.am.integration.cucumbertests.utils.clients.SimpleHTTPClient;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.am.testcontainers.DistributedApimCluster;
import org.wso2.am.testcontainers.DynamicApimContainer;
import org.wso2.am.testcontainers.JacocoCoverage;
import org.wso2.am.testcontainers.NodeAppServer;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Per-block lifecycle for the parallel-on-shared-container lane. Fires once per TestNG {@code <test>}
 * block: {@code onStart} boots a single {@link DynamicApimContainer} for the block, gates on readiness,
 * and publishes the container plus its base/gateway URLs into the block's shared scope so every class in
 * the block sees one ready server; {@code onFinish} stops that container and clears the scope.
 *
 * <p>If boot or readiness fails it records the cause as the {@code bootError} attribute (consumed by
 * {@code BaseBlockRunner}'s guard) instead of throwing, so the block's classes are reported FAILED with a
 * single root cause rather than failing with an NPE cascade from the absent container. The build stays red
 * — a boot failure must never be reported as a skip, which would leave the run green.
 *
 * <p>Registered only in the new-lane verification suite; the legacy testng.xml is untouched.
 */
public class BlockLifecycleListener implements ITestListener {

    private static final Log logger = LogFactory.getLog(BlockLifecycleListener.class);

    /** Must match {@code BaseBlockRunner.BOOT_ERROR_ATTRIBUTE}. */
    static final String BOOT_ERROR_ATTRIBUTE = "bootError";

    static final String CONTAINER_KEY = "blockApimContainer";
    static final String BASE_URL_KEY = "baseUrl";
    static final String BASE_GATEWAY_URL_KEY = "baseGatewayUrl";
    /**
     * Base URL of the node serving the gateway management webapp ({@code api/am/gateway/v2/*}). All-in-one:
     * equals {@code baseUrl}. Distributed: the Gateway node's mapped 9443. Consumed by {@code BaseSteps
     * .getGatewayMgmtUrl()} and this listener's readiness gate.
     */
    static final String GATEWAY_MGMT_URL_KEY = "gatewayMgmtUrl";

    /**
     * Topology selector (system property {@code apim.topology}): {@code allinone} (default) boots a single
     * {@link DynamicApimContainer}; {@code distributed} boots a {@link DistributedApimCluster} (ACP + GW + TM
     * + shared MySQL). The published TestContext keys are identical either way, so step definitions are
     * topology-agnostic.
     */
    static final String TOPOLOGY_PROPERTY = "apim.topology";
    static final String TOPOLOGY_DISTRIBUTED = "distributed";

    /** Optional {@code <parameter>} names read from the block's {@code <test>}. */
    static final String PARAM_BLOCK_LABEL = "blockLabel";
    static final String PARAM_TOML_OVERLAY = "tomlOverlayPath";
    /**
     * Optional path to a small feature-specific TOML overlay merged on top of the default {@code basic}
     * overlay (which is itself merged onto the product distribution config). Use this — not the full-file
     * {@code tomlOverlayPath} — when a block only needs a few extra keys (e.g. a custom auth header or
     * application sharing) so it still inherits the distribution + basic defaults.
     */
    static final String PARAM_TOML_EXTRA_OVERLAY = "tomlExtraOverlayPath";
    /** When {@code true}, onStart provisions tenants/users into the block's own container after readiness. */
    static final String PARAM_INIT_TENANT_USERS = "initTenantUsers";
    /** Selects which tenant/user set to provision: {@code default} (the else branch) or {@code adpsample}. */
    static final String PARAM_TENANT_SET = "tenantSet";
    static final String TENANT_SET_ADPSAMPLE = "adpsample";
    /**
     * When {@code true}, onStart ensures the shared NodeAppServer backend (network alias {@code nodebackend})
     * is running before APIM boots, so gateway-invocation tests have a reachable backend for deployed APIs.
     */
    static final String PARAM_INIT_BACKEND = "initBackend";

    @Override
    public void onStart(ITestContext context) {

        // Opt-in gate: a block joins the parallel-on-shared lane only by declaring a blockLabel param.
        // Without it (e.g. a legacy fixed-port <test> driving its own SystemInitializationRunner), the
        // listener no-ops so it never boots a stray container or disturbs that block's own lifecycle.
        String label = param(context, PARAM_BLOCK_LABEL);
        if (label == null || label.isBlank()) {
            return;
        }

        String sharedScopeId = TestContext.sharedScopeId(context);
        TestContext.setScope(sharedScopeId, sharedScopeId);

        try {
            // Start the shared backend first (idempotent singleton on the shared network) when the block opts
            // in, so APIs deployed by gateway-invocation tests have a reachable "nodebackend" upstream.
            if (Boolean.parseBoolean(param(context, PARAM_INIT_BACKEND))) {
                NodeAppServer.getInstance();
                logger.info("Block '" + label + "' ensured NodeAppServer backend is running");
            }

            String baseUrl;
            String gatewayUrl;
            String gatewayMgmtUrl;
            Object stopHandle;

            if (TOPOLOGY_DISTRIBUTED.equalsIgnoreCase(System.getProperty(TOPOLOGY_PROPERTY))) {
                // Distributed lane: boot a 4-container cluster (ACP + GW + TM + shared MySQL) from role
                // overlays merged onto the component distribution configs.
                String moduleDir = ModulePathResolver.getModuleDir(BlockLifecycleListener.class);
                // A block's feature overlay (tomlExtraOverlayPath) is layered onto each component (see
                // DistributedClusterConfig.resolve) — mirrors the all-in-one extra-overlay merge.
                DistributedClusterConfig.Tomls tomls = DistributedClusterConfig.resolve(
                        moduleDir, param(context, PARAM_TOML_EXTRA_OVERLAY));
                // Product DDL for the shared MySQL, read from the built distribution (no stored copy).
                DistributedDbScripts.Ddl ddl = DistributedDbScripts.resolve(moduleDir);
                DistributedApimCluster cluster = new DistributedApimCluster(
                        label, tomls.acp, tomls.tm, tomls.gw, ddl.shared, ddl.apim);
                cluster.start();

                // Gateway-invocation blocks: attach the shared backend to this cluster's network so the GW
                // resolves "nodebackend" (the GW stays single-homed — see NodeAppServer.connectToNetwork).
                if (Boolean.parseBoolean(param(context, PARAM_INIT_BACKEND))) {
                    NodeAppServer.getInstance().connectToNetwork(cluster.getNetwork());
                }

                baseUrl = cluster.getServletHttpsUrl();
                gatewayUrl = cluster.getGatewayHttpsUrl();
                // The gateway management webapp lives on the GW node, not the ACP servlet.
                gatewayMgmtUrl = cluster.getGatewayMgmtHttpsUrl();
                stopHandle = cluster;

                // Ready = GW management health-check 200 (on the GW node) AND the ACP servlet plane up.
                if (!ServerReadiness.awaitReady(gatewayMgmtUrl) || !awaitControlPlaneReady(baseUrl)) {
                    cluster.stop();
                    throw new IllegalStateException("Distributed APIM block '" + label + "' did not become "
                            + "ready within " + (Constants.SERVER_STARTUP_WAIT_TIME / 1000) + "s");
                }
            } else {
                // All-in-one lane (default): a single container serves both planes on one node.
                DynamicApimContainer container = new DynamicApimContainer(label, resolveTomlContent(context));
                container.withLabel("block", label);
                // Opt-in integration coverage: attach the JaCoCo agent before boot (see CoverageSupport).
                if (CoverageSupport.enabled()) {
                    container.withCoverage();
                }
                container.start();

                baseUrl = container.getServletHttpsUrl();
                gatewayUrl = container.getGatewayHttpsUrl();
                gatewayMgmtUrl = baseUrl; // gateway webapp shares the servlet node
                stopHandle = container;
                if (!ServerReadiness.awaitReady(baseUrl)) {
                    container.stop();
                    throw new IllegalStateException("APIM block '" + label + "' did not become ready within "
                            + (Constants.SERVER_STARTUP_WAIT_TIME / 1000) + "s");
                }
            }

            TestContext.setShared(CONTAINER_KEY, stopHandle);
            TestContext.setShared(BASE_URL_KEY, baseUrl);
            TestContext.setShared(BASE_GATEWAY_URL_KEY, gatewayUrl);
            TestContext.setShared(GATEWAY_MGMT_URL_KEY, gatewayMgmtUrl);
            logger.info("Block '" + label + "' booted and ready: baseUrl=" + baseUrl
                    + " baseGatewayUrl=" + gatewayUrl + " gatewayMgmtUrl=" + gatewayMgmtUrl);

            if (Boolean.parseBoolean(param(context, PARAM_INIT_TENANT_USERS))) {
                provisionTenantUsers(label, param(context, PARAM_TENANT_SET));
            }
        } catch (Throwable t) {
            context.setAttribute(BOOT_ERROR_ATTRIBUTE, t);
            logger.error("Block '" + label + "' boot/readiness failed; its classes will be skipped", t);
        }
    }

    @Override
    public void onFinish(ITestContext context) {

        // Mirror the onStart opt-in: a block this listener never managed must be left entirely alone.
        String label = param(context, PARAM_BLOCK_LABEL);
        if (label == null || label.isBlank()) {
            return;
        }

        String sharedScopeId = TestContext.sharedScopeId(context);
        TestContext.setScope(sharedScopeId, sharedScopeId);
        try {
            Object stored = TestContext.get(CONTAINER_KEY);
            if (stored instanceof DynamicApimContainer container) {
                // Dump JaCoCo counters over the mapped tcpserver port BEFORE stopping (all-in-one lane).
                // Best-effort: a dump failure must never break teardown or fail the block.
                if (CoverageSupport.enabled()) {
                    try {
                        String moduleDir = ModulePathResolver.getModuleDir(BlockLifecycleListener.class);
                        JacocoCoverage.dump(container.getCoverageDumpHost(), container.getCoverageDumpPort(),
                                CoverageSupport.execFile(moduleDir, label));
                    } catch (Exception e) {
                        logger.warn("Coverage dump failed for block '" + label + "': " + e.getMessage());
                    }
                }
                container.stop();
                logger.info("Block '" + context.getName()
                        + "' container stopped; dynamic host ports released by Docker");
            } else if (stored instanceof DistributedApimCluster cluster) {
                cluster.stop();
                logger.info("Block '" + context.getName()
                        + "' distributed cluster stopped; per-cluster network + host ports released");
            }
        } finally {
            TestContext.clear();
            TestContext.clearScope();
        }
    }

    /**
     * Control-plane readiness for the distributed lane: polls the ACP servlet's {@code services/Version}
     * until 200, so the block does not proceed to tenant/user provisioning or publisher calls before the
     * ACP webapps are actually serving (the GW health-check alone attests only the Gateway node).
     */
    private boolean awaitControlPlaneReady(String baseUrl) {
        String url = baseUrl + "services/Version";
        long deadline = System.currentTimeMillis() + Constants.SERVER_STARTUP_WAIT_TIME;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpResponse response = SimpleHTTPClient.getInstance().doGet(url, null);
                if (response != null && response.getResponseCode() == 200) {
                    return true;
                }
            } catch (Exception ignored) {
                // ACP not serving yet
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

    /**
     * Provisions the selected tenant/user set against the block's OWN booted container. {@code baseUrl} is
     * already published into the block's shared scope, so {@link TenantUserProvisioner} (which reads it from
     * there) targets this container's mapped port. Mirrors the legacy init features: the {@code default} set
     * matches {@code tenant_users_initialisation.feature}; {@code adpsample} matches
     * {@code migrated_tenant_user_initialization.feature}. Called inside onStart's try, so a provisioning
     * failure becomes {@code bootError} and the block is skipped cleanly rather than NPE-ing mid-scenario.
     */
    private void provisionTenantUsers(String label, String tenantSet) throws java.io.IOException, JaxenException {

        // Gateway readiness can pass before the SOAP admin services finish deploying; gate on the Tenant Mgt
        // service being live so provisioning never fires into a transient 404 (a race parallel boots widen).
        TenantUserProvisioner.awaitTenantMgtServiceReady();

        if (TENANT_SET_ADPSAMPLE.equalsIgnoreCase(tenantSet)) {
            TenantUserProvisioner.addAdpsampleTenant();
            TenantUserProvisioner.addUser(Constants.ADPSAMPLE_TENANT_DOMAIN, "userKey1",
                    "testTenantUser11", "testTenantUser11", "ADP_CREATOR, ADP_PUBLISHER, ADP_SUBSCRIBER");
        } else {
            String allRoles = "Internal/creator, Internal/publisher, Internal/subscriber";
            String publisherRoles = "Internal/creator, Internal/publisher";
            String subscriberRoles = "Internal/subscriber";
            TenantUserProvisioner.addSuperTenant();
            TenantUserProvisioner.addTenant("tenant1.com", "admin", "admin", "First", "Tenant",
                    "admin@tenant1.com");
            // Keep the original all-roles user (back-compat for any actor that needs creator+publisher+subscriber).
            TenantUserProvisioner.addUser(Constants.SUPER_TENANT_DOMAIN, Constants.USER_KEY,
                    "testUser1", "testUser1", allRoles);
            TenantUserProvisioner.addUser("tenant1.com", Constants.USER_KEY, "testUser11", "testUser11", allRoles);
            // Least-privilege publisher (creator+publisher, NOT admin) — the default actor for publisher tests.
            TenantUserProvisioner.addUser(Constants.SUPER_TENANT_DOMAIN, Constants.PUBLISHER_USER_KEY,
                    "publisherUser1", "publisherUser1", publisherRoles);
            TenantUserProvisioner.addUser("tenant1.com", Constants.PUBLISHER_USER_KEY,
                    "publisherUser11", "publisherUser11", publisherRoles);
            // Subscriber-only (self-signup-equivalent) — for access-control negatives (publisher ops -> 403).
            TenantUserProvisioner.addUser(Constants.SUPER_TENANT_DOMAIN, Constants.SUBSCRIBER_USER_KEY,
                    "subscriberUser1", "subscriberUser1", subscriberRoles);
            TenantUserProvisioner.addUser("tenant1.com", Constants.SUBSCRIBER_USER_KEY,
                    "subscriberUser11", "subscriberUser11", subscriberRoles);
        }
        logger.info("Block '" + label + "' provisioned tenant set '"
                + (tenantSet == null || tenantSet.isBlank() ? "default" : tenantSet) + "'");
    }

    private String resolveTomlContent(ITestContext context) throws java.io.IOException {
        String overlayPath = param(context, PARAM_TOML_OVERLAY);
        if (overlayPath != null && !overlayPath.isBlank()) {
            // Explicit full-file replacement: the block supplies a complete deployment.toml verbatim.
            return Files.readString(Path.of(overlayPath));
        }
        // Default lane: merge the small basic overlay onto the product distribution toml (the base
        // shipped in the image), so the test config tracks distribution defaults instead of a stale copy.
        String moduleDir = ModulePathResolver.getModuleDir(BlockLifecycleListener.class);
        Path basePath = Paths.get(moduleDir, Constants.DISTRIBUTION_TOML_PATH).normalize();
        Path overlay = Paths.get(moduleDir, Constants.DEFAULT_TOML_PATH).normalize();

        // A block may layer a small feature-specific overlay on top of basic (e.g. custom auth header /
        // application sharing) without restating the whole distribution config.
        String extraOverlayPath = param(context, PARAM_TOML_EXTRA_OVERLAY);
        if (extraOverlayPath != null && !extraOverlayPath.isBlank()) {
            Path extraOverlay = Paths.get(moduleDir, extraOverlayPath).normalize();
            return Utils.mergeTomls(basePath.toString(),
                    java.util.List.of(overlay.toString(), extraOverlay.toString()));
        }
        return Utils.mergeToml(basePath.toString(), overlay.toString());
    }

    private String param(ITestContext context, String name) {
        XmlTest xmlTest = context.getCurrentXmlTest();
        return xmlTest != null ? xmlTest.getLocalParameters().get(name) : null;
    }
}
