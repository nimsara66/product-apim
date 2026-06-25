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

import org.jaxen.JaxenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.xml.XmlTest;
import org.wso2.am.integration.cucumbertests.utils.ModulePathResolver;
import org.wso2.am.integration.cucumbertests.utils.ServerReadiness;
import org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.am.testcontainers.DynamicApimContainer;

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
 * {@code BaseBlockRunner}'s skip guard) instead of throwing, so the block's classes are reported SKIPPED
 * with a single root cause rather than failing with an NPE cascade from the absent container.
 *
 * <p>Registered only in the new-lane verification suite; the legacy testng.xml is untouched.
 */
public class BlockLifecycleListener implements ITestListener {

    private static final Logger logger = LoggerFactory.getLogger(BlockLifecycleListener.class);

    /** Must match {@code BaseBlockRunner.BOOT_ERROR_ATTRIBUTE}. */
    static final String BOOT_ERROR_ATTRIBUTE = "bootError";

    static final String CONTAINER_KEY = "blockApimContainer";
    static final String BASE_URL_KEY = "baseUrl";
    static final String BASE_GATEWAY_URL_KEY = "baseGatewayUrl";

    /** Optional {@code <parameter>} names read from the block's {@code <test>}. */
    static final String PARAM_BLOCK_LABEL = "blockLabel";
    static final String PARAM_TOML_OVERLAY = "tomlOverlayPath";
    /** When {@code true}, onStart provisions tenants/users into the block's own container after readiness. */
    static final String PARAM_INIT_TENANT_USERS = "initTenantUsers";
    /** Selects which tenant/user set to provision: {@code default} (the else branch) or {@code adpsample}. */
    static final String PARAM_TENANT_SET = "tenantSet";
    static final String TENANT_SET_ADPSAMPLE = "adpsample";

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
            DynamicApimContainer container = new DynamicApimContainer(label, resolveTomlContent(context));
            container.withLabel("block", label);
            container.start();

            String baseUrl = container.getServletHttpsUrl();
            String gatewayUrl = container.getGatewayHttpsUrl();
            if (!ServerReadiness.awaitReady(baseUrl)) {
                container.stop();
                throw new IllegalStateException("APIM block '" + label + "' did not become ready within "
                        + (Constants.SERVER_STARTUP_WAIT_TIME / 1000) + "s");
            }

            TestContext.setShared(CONTAINER_KEY, container);
            TestContext.setShared(BASE_URL_KEY, baseUrl);
            TestContext.setShared(BASE_GATEWAY_URL_KEY, gatewayUrl);
            logger.info("Block '{}' booted and ready: baseUrl={} baseGatewayUrl={}",
                    label, baseUrl, gatewayUrl);

            if (Boolean.parseBoolean(param(context, PARAM_INIT_TENANT_USERS))) {
                provisionTenantUsers(label, param(context, PARAM_TENANT_SET));
            }
        } catch (Throwable t) {
            context.setAttribute(BOOT_ERROR_ATTRIBUTE, t);
            logger.error("Block '{}' boot/readiness failed; its classes will be skipped", label, t);
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
                container.stop();
                logger.info("Block '{}' container stopped; dynamic host ports released by Docker",
                        context.getName());
            }
        } finally {
            TestContext.clear();
            TestContext.clearScope();
        }
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
            String roles = "Internal/creator, Internal/publisher, Internal/subscriber";
            TenantUserProvisioner.addSuperTenant();
            TenantUserProvisioner.addTenant("tenant1.com", "admin", "admin", "First", "Tenant",
                    "admin@tenant1.com");
            TenantUserProvisioner.addUser(Constants.SUPER_TENANT_DOMAIN, "userKey1",
                    "testUser1", "testUser1", roles);
            TenantUserProvisioner.addUser("tenant1.com", "userKey1", "testUser11", "testUser11", roles);
        }
        logger.info("Block '{}' provisioned tenant set '{}'", label,
                tenantSet == null || tenantSet.isBlank() ? "default" : tenantSet);
    }

    private String resolveTomlContent(ITestContext context) throws java.io.IOException {
        String overlayPath = param(context, PARAM_TOML_OVERLAY);
        Path tomlPath;
        if (overlayPath != null && !overlayPath.isBlank()) {
            tomlPath = Path.of(overlayPath);
        } else {
            String moduleDir = ModulePathResolver.getModuleDir(BlockLifecycleListener.class);
            tomlPath = Paths.get(moduleDir, Constants.DEFAULT_TOML_PATH);
        }
        return Files.readString(tomlPath);
    }

    private String param(ITestContext context, String name) {
        XmlTest xmlTest = context.getCurrentXmlTest();
        return xmlTest != null ? xmlTest.getLocalParameters().get(name) : null;
    }
}
