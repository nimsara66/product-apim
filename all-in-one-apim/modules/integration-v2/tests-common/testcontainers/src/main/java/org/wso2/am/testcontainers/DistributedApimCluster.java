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

package org.wso2.am.testcontainers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.wso2.am.integration.test.utils.Constants;

import java.time.Duration;

/**
 * Distributed-APIM cluster for the parallel-on-shared lane: a single logical "server" made of four
 * containers — a shared {@link DistributedMysqlContainer} plus the three APIM components
 * (API Control Plane, Universal Gateway, Traffic Manager) — on ONE per-cluster {@link Network}.
 *
 * <p>This is the distributed counterpart of {@link DynamicApimContainer}: it presents the same URL
 * accessors ({@link #getServletHttpsUrl()} / {@link #getGatewayHttpsUrl()}) so the block lifecycle and
 * step definitions consume it unchanged, but internally the servlet plane (publisher/devportal/admin/
 * token/SOAP) lives on the ACP container and the runtime plane (API invocation) on the GW container.
 * A third accessor {@link #getGatewayMgmtHttpsUrl()} exposes the GW's management webapp
 * ({@code api/am/gateway/v2/...}) which in a distributed deployment lives on the GW node, not the ACP.
 *
 * <p>Because each cluster owns its own network, the components address each other by fixed aliases
 * ({@code mysql}/{@code acp}/{@code gateway}/{@code trafficmanager}) on canonical ports — no port
 * offsets — and only the host-facing ports are mapped to ephemeral host ports (resolved via
 * {@code getMappedPort}). Parallel clusters therefore never collide on host ports OR on network aliases.
 * The caller supplies each component's {@code deployment.toml} (resolved by {@code DistributedClusterConfig});
 * boot order is MySQL (schema-initialized) then ACP (event-hub source) then TM + GW (which subscribe to it).
 * The topology is proven end-to-end by the Phase 1 spike (docs/devs/distributed-apim-implementation-plan.md).
 */
public class DistributedApimCluster {

    private static final Logger logger = LoggerFactory.getLogger(DistributedApimCluster.class);

    private static final String DEFAULT_ACP_IMAGE = "wso2am-acp:4.7.0-SNAPSHOT-jdk21";
    private static final String DEFAULT_TM_IMAGE = "wso2am-tm:4.7.0-SNAPSHOT-jdk21";
    private static final String DEFAULT_GW_IMAGE = "wso2am-universal-gw:4.7.0-SNAPSHOT-jdk21";

    static final String ACP_ALIAS = "acp";
    static final String GW_ALIAS = "gateway";
    static final String TM_ALIAS = "trafficmanager";

    private final String version;
    private final Network network;
    private final DistributedMysqlContainer mysql;
    private final GenericContainer<?> acp;
    private final GenericContainer<?> trafficManager;
    private final GenericContainer<?> gateway;

    /**
     * @param acpToml/tmToml/gwToml complete {@code deployment.toml} contents for each component — a small
     *        role overlay merged onto the component's distribution config (see {@code DistributedClusterConfig}).
     * @param sharedDdl/apimDdl the Carbon shared- and APIM-schema DDL for the backing MySQL, resolved from the
     *        built product distribution (see {@code DistributedDbScripts}). The caller owns all config/DDL
     *        resolution; this facade only orchestrates the containers.
     */
    public DistributedApimCluster(String label, String acpToml, String tmToml, String gwToml,
                                  String sharedDdl, String apimDdl) {

        this.version = System.getProperty("apim.server.version", "4.7.0-SNAPSHOT");
        // Per-cluster network: aliases are scoped to it, so parallel clusters don't collide.
        this.network = Network.newNetwork();
        this.mysql = new DistributedMysqlContainer(network, sharedDdl, apimDdl);

        this.acp = component("wso2am-acp", ACP_ALIAS,
                System.getProperty("acp.docker.image.name", DEFAULT_ACP_IMAGE), acpToml, label + "-acp")
                .withExposedPorts(Constants.HTTPS_PORT, Constants.HTTP_PORT);

        this.trafficManager = component("wso2am-tm", TM_ALIAS,
                System.getProperty("tm.docker.image.name", DEFAULT_TM_IMAGE), tmToml, label + "-tm")
                // 9443 exposed only so the listening-port wait has a target; TM has no test-facing URL.
                .withExposedPorts(Constants.HTTPS_PORT);

        this.gateway = component("wso2am-universal-gw", GW_ALIAS,
                System.getProperty("gw.docker.image.name", DEFAULT_GW_IMAGE), gwToml, label + "-gw")
                .withExposedPorts(Constants.GATEWAY_HTTPS_PORT, Constants.GATEWAY_HTTP_PORT, Constants.HTTPS_PORT);
    }

    private GenericContainer<?> component(String serverName, String alias, String image,
                                          String deploymentToml, String logPrefix) {
        String tomlPath = Constants.APIM_CONTAINER_USER_HOME + "/" + serverName + "-" + version
                + Constants.DEPLOYMENT_TOML_PATH;
        return new GenericContainer<>(image)
                .withNetwork(network)
                .withNetworkAliases(alias)
                .withExtraHost("host.docker.internal", "host-gateway")
                .withCopyToContainer(Transferable.of(deploymentToml), tomlPath)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(20)))
                .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix(logPrefix));
    }

    /**
     * Boots the cluster in dependency order: MySQL (blocks until schema-initialized) → ACP (the event-hub
     * source) → Traffic Manager + Gateway (which connect to the ACP hub, retrying until it is reachable).
     * Each {@code start()} blocks until that container's listening-port wait passes.
     */
    public void start() {
        logger.info("Starting distributed APIM cluster (version {})", version);
        mysql.start();
        acp.start();
        trafficManager.start();
        gateway.start();
        logger.info("Distributed APIM cluster started: servlet={} gateway={} gatewayMgmt={}",
                getServletHttpsUrl(), getGatewayHttpsUrl(), getGatewayMgmtHttpsUrl());
    }

    /**
     * The cluster's per-cluster network. Gateway-invocation blocks attach the shared backend
     * ({@code NodeAppServer}) to THIS network (see {@code NodeAppServer.connectToNetwork}) so the gateway can
     * reach {@code nodebackend} without the gateway itself being multi-homed — multi-homing the gateway
     * perturbs Testcontainers' {@code getHost()}/{@code getMappedPort()} host-port resolution and breaks the
     * readiness poll, whereas the backend has no test-facing mapped port to perturb.
     */
    public Network getNetwork() {
        return network;
    }

    public void stop() {
        // Reverse boot order; best-effort so one failure doesn't strand the rest.
        stopQuietly(gateway);
        stopQuietly(trafficManager);
        stopQuietly(acp);
        stopQuietly(mysql);
        try {
            network.close();
        } catch (Exception e) {
            logger.warn("Error closing cluster network: {}", e.getMessage());
        }
    }

    private void stopQuietly(GenericContainer<?> container) {
        try {
            if (container != null) {
                container.stop();
            }
        } catch (Exception e) {
            logger.warn("Error stopping container {}: {}", container == null ? "?" : container.getDockerImageName(),
                    e.getMessage());
        }
    }

    /** Servlet HTTPS URL (ACP): publisher/devportal/admin REST, DCR, oauth2/token, SOAP admin. */
    public String getServletHttpsUrl() {
        return String.format("https://%s:%d/", acp.getHost(), acp.getMappedPort(Constants.HTTPS_PORT));
    }

    public String getServletHttpUrl() {
        return String.format("http://%s:%d/", acp.getHost(), acp.getMappedPort(Constants.HTTP_PORT));
    }

    /** Gateway HTTPS passthrough URL (GW): runtime invocation of deployed APIs. */
    public String getGatewayHttpsUrl() {
        return String.format("https://%s:%d/", gateway.getHost(), gateway.getMappedPort(Constants.GATEWAY_HTTPS_PORT));
    }

    public String getGatewayHttpUrl() {
        return String.format("http://%s:%d/", gateway.getHost(), gateway.getMappedPort(Constants.GATEWAY_HTTP_PORT));
    }

    /**
     * Gateway MANAGEMENT HTTPS URL (GW node's 9443): serves the {@code api/am/gateway/v2/*} webapp
     * (server-startup-healthcheck, api-artifact). In the all-in-one this shared the servlet 9443; in a
     * distributed deployment it lives on the GW node, so readiness/artifact polls must target this.
     */
    public String getGatewayMgmtHttpsUrl() {
        return String.format("https://%s:%d/", gateway.getHost(), gateway.getMappedPort(Constants.HTTPS_PORT));
    }

    public DistributedMysqlContainer getMysql() {
        return mysql;
    }
}
