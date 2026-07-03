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
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

/**
 * Shared MySQL backing store for the distributed-APIM lane. All three component containers
 * (control-plane, gateway, traffic-manager) of one cluster point their {@code apim_db} and
 * {@code shared_db} datasources at a single instance of this container, reached over the cluster's
 * network by the alias {@link #ALIAS} on the canonical MySQL port (3306) — that is how distributed
 * APIM coordinates state (subscriptions, keys, throttle policies, key-manager data).
 *
 * <p>The container is <b>ephemeral</b> (no persistent volume): every start re-runs the bundled
 * {@code /docker-entrypoint-initdb.d} scripts from a clean data dir, so each cluster begins from a
 * pristine schema — isolation for free, no cross-run leakage. The bundled {@code 01_create_dbs.sql}
 * (classpath resource under {@code distributed/mysql/}) creates both databases with
 * {@code CHARACTER SET latin1 COLLATE latin1_bin} (utf8mb4 overruns InnoDB's index key-length limit on
 * several APIM tables, and latin1_bin is the case-sensitive collation WSO2 mandates — latin1's default
 * latin1_swedish_ci is case-insensitive and must not be used) and grants the {@code wso2carbon} user. The
 * product DDL that follows ({@code dbscripts/mysql.sql} into {@code WSO2AM_SHARED_DB},
 * {@code dbscripts/apimgt/mysql.sql} into {@code WSO2AM_DB}) is NOT bundled here: the caller supplies it as
 * constructor content resolved from the built product distribution (see {@code DistributedDbScripts}), so this
 * library keeps no copy of the product schema that could drift from it. A tuned {@code my.cnf}
 * raises {@code max_connections} (three nodes with pool maxActive=100 each exceed MySQL's default 151).
 * Readiness is gated on the FINAL server's log line (see the wait strategy below), so no init-complete-flag
 * sentinel is needed here (that pattern is only for the dev-setup/compose healthchecks).
 *
 * <p>The recipe mirrors {@code apim-distributed-dev-setup} and is verified end-to-end by the Phase 1
 * spike (see {@code docs/devs/mysql-setup-learnings.md}). No host port is required for cluster use
 * (east-west traffic uses {@code mysql:3306}); {@code 3306} is exposed so tests/probes can also reach
 * it from the host via {@link #getMappedJdbcUrl(String)}.
 */
public class DistributedMysqlContainer extends GenericContainer<DistributedMysqlContainer> {

    private static final Logger logger = LoggerFactory.getLogger(DistributedMysqlContainer.class);
    private static final String DEFAULT_MYSQL_IMAGE = "mysql:8.4.0-oraclelinux8";

    /** Network alias the APIM component TOMLs reference (jdbc:mysql://mysql:3306/...). */
    public static final String ALIAS = "mysql";
    public static final int MYSQL_PORT = 3306;

    public static final String APIM_DB = "WSO2AM_DB";
    public static final String SHARED_DB = "WSO2AM_SHARED_DB";
    public static final String DB_USER = "wso2carbon";
    public static final String DB_PASSWORD = "wso2carbon";
    /** JDBC query params: MySQL-8 caching_sha2_password over a plaintext link needs allowPublicKeyRetrieval. */
    private static final String JDBC_PARAMS = "autoReconnect=true&allowPublicKeyRetrieval=true&useSSL=false";

    private static final String RESOURCE_ROOT = "distributed/mysql/";

    /**
     * @param network   the cluster's network. Each parallel cluster must use its OWN {@link Network} so the
     *                  {@code mysql}/{@code acp}/{@code gateway} aliases do not collide across blocks.
     * @param sharedDdl the Carbon shared-schema DDL to load into {@code WSO2AM_SHARED_DB} (product
     *                  {@code dbscripts/mysql.sql}, prefixed with its {@code USE} line by the caller).
     * @param apimDdl   the APIM-schema DDL to load into {@code WSO2AM_DB} (product {@code dbscripts/apimgt/mysql.sql},
     *                  prefixed with its {@code USE} line by the caller). Both are supplied by the caller (resolved
     *                  from the built product distribution) rather than stored here, so this library keeps no copy
     *                  of the product DDL and cannot drift from it.
     */
    public DistributedMysqlContainer(Network network, String sharedDdl, String apimDdl) {

        super(System.getProperty("mysql.docker.image.name", DEFAULT_MYSQL_IMAGE));

        withNetwork(network);
        withNetworkAliases(ALIAS);
        withExposedPorts(MYSQL_PORT);

        withEnv("MYSQL_ROOT_PASSWORD", "root");
        withEnv("MYSQL_ROOT_HOST", "%");
        withEnv("MYSQL_USER", DB_USER);
        withEnv("MYSQL_PASSWORD", DB_PASSWORD);

        // Tuned server config: raise max_connections, skip reverse-DNS.
        withCopyToContainer(MountableFile.forClasspathResource(RESOURCE_ROOT + "my.cnf"),
                "/etc/mysql/conf.d/my.cnf");

        // First-boot init scripts (run alphabetically, once, on the empty data dir):
        //   01 create DBs (latin1/latin1_bin) + user  ->  02 shared DDL  ->  03 apim DDL.
        // 01 is OUR config (DB/user/collation) and stays a bundled resource; 02/03 are the PRODUCT DDL,
        // injected as content resolved from the built distribution (see DistributedDbScripts) so no copy is kept here.
        withCopyToContainer(MountableFile.forClasspathResource(RESOURCE_ROOT + "initdb/01_create_dbs.sql"),
                "/docker-entrypoint-initdb.d/01_create_dbs.sql");
        withCopyToContainer(Transferable.of(sharedDdl),
                "/docker-entrypoint-initdb.d/02_shared_schema.sql");
        withCopyToContainer(Transferable.of(apimDdl),
                "/docker-entrypoint-initdb.d/03_apim_schema.sql");

        // Gate on the FINAL server being ready, not the temporary init server. The mysql entrypoint runs a
        // throwaway server on port 0 while the /docker-entrypoint-initdb.d scripts execute (root auth is not
        // yet in place there), then starts the real server on 3306. A naive count of "ready for connections"
        // matches the temp server's main + X-Plugin lines and returns mid-init (root access denied). Anchoring
        // on "mysqld: ready for connections ... port: 3306" excludes the temp server (port 0) and the
        // "X Plugin ready for connections" line, so start() returns only once the initialized server is live.
        waitingFor(Wait.forLogMessage(".*mysqld: ready for connections.*port: 3306.*", 1)
                .withStartupTimeout(Duration.ofMinutes(5)));

        withLogConsumer(new Slf4jLogConsumer(logger).withPrefix(ALIAS));
    }

    /** JDBC URL a cluster peer (APIM node) uses — via the network alias on the canonical port. */
    public String getInternalJdbcUrl(String database) {
        return String.format("jdbc:mysql://%s:%d/%s?%s", ALIAS, MYSQL_PORT, database, JDBC_PARAMS);
    }

    /** JDBC URL for host-side access (tests/probes) — via the ephemeral mapped port. */
    public String getMappedJdbcUrl(String database) {
        return String.format("jdbc:mysql://%s:%d/%s?%s", getHost(), getMappedPort(MYSQL_PORT), database, JDBC_PARAMS);
    }
}
