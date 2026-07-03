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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.Network;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wso2.am.integration.cucumbertests.utils.DistributedDbScripts;
import org.wso2.am.integration.cucumbertests.utils.ModulePathResolver;
import org.wso2.am.testcontainers.DistributedMysqlContainer;

/**
 * Phase 3 (T3.1) verification: proves {@link DistributedMysqlContainer} self-initializes the shared
 * distributed-APIM datastore. Because the container's wait strategy gates on MySQL logging "ready for
 * connections" a second time (i.e. after the {@code /docker-entrypoint-initdb.d} scripts have run),
 * {@code start()} returning already implies init is complete — so this probe asserts state directly, with
 * no polling/sleep. It runs {@code mysql} inside the container (no JDBC driver on the test classpath needed)
 * and checks: both DBs exist with {@code latin1} charset, the product DDL loaded (table counts + spot
 * tables), and {@code max_connections} was raised. Container is torn down in {@code finally}.
 */
public class DistributedMysqlContainerVerificationTest {

    private static final Log logger = LogFactory.getLog(DistributedMysqlContainerVerificationTest.class);

    @Test
    public void verifySharedMysqlInitialises() throws Exception {

        String moduleDir = ModulePathResolver.getModuleDir(DistributedMysqlContainerVerificationTest.class);
        DistributedDbScripts.Ddl ddl = DistributedDbScripts.resolve(moduleDir);
        Network network = Network.newNetwork();
        DistributedMysqlContainer mysql = new DistributedMysqlContainer(network, ddl.shared, ddl.apim);
        try {
            mysql.start();

            // 1) both DBs exist AND are latin1 with a case-SENSITIVE collation (latin1_bin): utf8mb4 would
            //    overrun InnoDB index key limits on APIM tables, and latin1's default latin1_swedish_ci is
            //    case-insensitive (which WSO2 forbids).
            Assert.assertEquals(query(mysql,
                    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN "
                            + "('WSO2AM_DB','WSO2AM_SHARED_DB') AND DEFAULT_CHARACTER_SET_NAME='latin1' "
                            + "AND DEFAULT_COLLATION_NAME='latin1_bin';"),
                    "2", "both WSO2 DBs should exist with latin1 charset and latin1_bin (case-sensitive) collation");

            // 2) product DDL actually loaded — table counts well above trivial
            int apimTables = Integer.parseInt(query(mysql,
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='WSO2AM_DB';"));
            int sharedTables = Integer.parseInt(query(mysql,
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='WSO2AM_SHARED_DB';"));
            Assert.assertTrue(apimTables > 100, "WSO2AM_DB should have the full APIM schema, found " + apimTables);
            Assert.assertTrue(sharedTables > 20, "WSO2AM_SHARED_DB should have the shared schema, found " + sharedTables);

            // 3) spot tables from each script are present
            Assert.assertEquals(query(mysql,
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='WSO2AM_DB' "
                            + "AND TABLE_NAME='AM_API';"), "1", "AM_API missing from WSO2AM_DB");
            Assert.assertEquals(query(mysql,
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='WSO2AM_SHARED_DB' "
                            + "AND TABLE_NAME='REG_CLUSTER_LOCK';"), "1", "REG_CLUSTER_LOCK missing from WSO2AM_SHARED_DB");

            // 4) tuned my.cnf applied
            Assert.assertEquals(query(mysql, "SELECT @@max_connections;"), "1000",
                    "my.cnf max_connections override not applied");

            // 5) accessor URLs are well-formed for both the internal (alias) and host (mapped) paths
            Assert.assertTrue(mysql.getInternalJdbcUrl(DistributedMysqlContainer.APIM_DB)
                    .startsWith("jdbc:mysql://mysql:3306/WSO2AM_DB"), "internal JDBC URL malformed");
            Assert.assertTrue(mysql.getMappedJdbcUrl(DistributedMysqlContainer.SHARED_DB)
                    .matches("jdbc:mysql://[^:]+:\\d+/WSO2AM_SHARED_DB\\?.*"), "mapped JDBC URL malformed");

            logger.info("Phase 3 (T3.1) assertions passed: apimTables=" + apimTables
                    + " sharedTables=" + sharedTables);
        } finally {
            mysql.stop();
            network.close();
        }
    }

    /** Run a scalar query inside the container as root; return the single trimmed value. */
    private String query(DistributedMysqlContainer mysql, String sql) throws Exception {
        Container.ExecResult r = mysql.execInContainer("mysql", "-uroot", "-proot", "-N", "-B", "-e", sql);
        Assert.assertEquals(r.getExitCode(), 0, "mysql query failed: " + r.getStderr());
        return r.getStdout().trim();
    }
}
