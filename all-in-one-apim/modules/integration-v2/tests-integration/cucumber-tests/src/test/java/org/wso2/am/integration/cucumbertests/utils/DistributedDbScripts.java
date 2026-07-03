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

package org.wso2.am.integration.cucumbertests.utils;

import org.wso2.am.testcontainers.DistributedMysqlContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Resolves the two WSO2 product DDL scripts the shared {@link DistributedMysqlContainer} loads —
 * the Carbon shared schema ({@code dbscripts/mysql.sql} → {@code WSO2AM_SHARED_DB}) and the APIM schema
 * ({@code dbscripts/apimgt/mysql.sql} → {@code WSO2AM_DB}) — straight from the built product distribution,
 * rather than keeping (drift-prone) copies in the test resources. This mirrors {@link DistributedClusterConfig},
 * which resolves the component {@code deployment.toml} from the product tree instead of storing full copies.
 *
 * <p>The DDL is not present in the source tree (it is contributed by p2 features and only materialises when
 * the distribution is assembled), but it IS packed into the built distribution zip — the very artifact the
 * distributed Docker images are built from. We read the two entries out of the API Control Plane zip
 * ({@code api-control-plane/modules/distribution/product/target/wso2am-acp-<ver>.zip}); every component packs
 * the identical DDL, and the control plane is the natural owner of {@code apim_db}. Reading two small entries
 * from the zip's central directory is a fast seek (no full scan), so this is cheap enough to run per block.
 *
 * <p>The MySQL entrypoint runs {@code /docker-entrypoint-initdb.d/*.sql} with no default database selected,
 * so each script content is prefixed with a {@code USE <db>;} line pinning it to the correct database (the
 * product DDL itself is database-agnostic).
 */
public final class DistributedDbScripts {

    // cucumber-tests -> tests-integration -> integration-v2 -> modules -> all-in-one-apim -> product-apim
    private static final String PRODUCT_ROOT_REL = "../../../../..";
    private static final String APIM_VERSION = System.getProperty("apim.server.version", "4.7.0-SNAPSHOT");

    /** ACP distribution zip (relative to the product root) and the entry root inside it. */
    private static final String ACP_ZIP_REL =
            "/api-control-plane/modules/distribution/product/target/wso2am-acp-" + APIM_VERSION + ".zip";
    private static final String ZIP_ROOT = "wso2am-acp-" + APIM_VERSION;
    private static final String SHARED_DDL_ENTRY = ZIP_ROOT + "/dbscripts/mysql.sql";
    private static final String APIM_DDL_ENTRY = ZIP_ROOT + "/dbscripts/apimgt/mysql.sql";

    private DistributedDbScripts() {
    }

    /** The two DDL script contents (each already prefixed with its {@code USE <db>;} line). */
    public static final class Ddl {
        public final String shared;
        public final String apim;

        Ddl(String shared, String apim) {
            this.shared = shared;
            this.apim = apim;
        }
    }

    /**
     * @param moduleDir the cucumber-tests module dir (from {@link ModulePathResolver#getModuleDir}).
     * @return the shared and APIM DDL read from the built ACP distribution zip.
     * @throws IOException if the zip or either DDL entry is missing (build the api-control-plane reactor first).
     */
    public static Ddl resolve(String moduleDir) throws IOException {
        Path zip = Paths.get(moduleDir, PRODUCT_ROOT_REL).normalize().resolve("." + ACP_ZIP_REL).normalize();
        if (!Files.exists(zip)) {
            throw new IOException("ACP distribution zip not found: " + zip
                    + " — build the api-control-plane reactor first so the DDL can be read from it.");
        }
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            return new Ddl(
                    "USE " + DistributedMysqlContainer.SHARED_DB + ";\n" + readEntry(zf, SHARED_DDL_ENTRY),
                    "USE " + DistributedMysqlContainer.APIM_DB + ";\n" + readEntry(zf, APIM_DDL_ENTRY));
        }
    }

    private static String readEntry(ZipFile zf, String entryName) throws IOException {
        ZipEntry entry = zf.getEntry(entryName);
        if (entry == null) {
            throw new IOException("DDL entry not found in distribution zip: " + entryName + " (in " + zf.getName() + ")");
        }
        try (InputStream in = zf.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
