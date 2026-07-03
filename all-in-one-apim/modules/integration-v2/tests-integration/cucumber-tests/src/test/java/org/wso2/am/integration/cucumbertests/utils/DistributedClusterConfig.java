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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the three distributed-component {@code deployment.toml} contents for
 * {@code DistributedApimCluster}, mirroring how the all-in-one lane resolves config: each small role
 * overlay under {@code artifacts/configFiles/distributed/} is merged (via {@link Utils#mergeToml}) ONTO
 * that component's product distribution {@code deployment.toml}, so the test config inherits every
 * distribution default and only adds/overrides the cluster wiring. Keeping the overlays small (not
 * full-file copies) means they don't drift from the distribution.
 *
 * <p>The component distributions are sibling reactors of {@code all-in-one-apim} under the
 * {@code product-apim} root; paths are resolved relative to the cucumber-tests module dir.
 */
public final class DistributedClusterConfig {

    // cucumber-tests -> tests-integration -> integration-v2 -> modules -> all-in-one-apim -> product-apim
    private static final String PRODUCT_ROOT_REL = "../../../../..";

    private static final String ACP_DIST =
            "/api-control-plane/modules/distribution/product/src/main/conf/deployment.toml";
    private static final String TM_DIST =
            "/traffic-manager/modules/distribution/product/src/main/conf/deployment.toml";
    private static final String GW_DIST =
            "/gateway/modules/distribution/product/src/main/conf/deployment.toml";

    private static final String OVERLAY_DIR = "src/test/resources/artifacts/configFiles/distributed";

    /**
     * Shared symmetric crypto key, appended (in TABLE form) to every component TOML so all nodes decrypt
     * data one another wrote (e.g. the GW decrypting a tenant primary certificate the ACP encrypted).
     * It is appended AFTER {@link Utils#mergeToml} rather than living in an overlay because the Jackson TOML
     * writer emits dotted-key form ({@code encryption.key = '...'}), which WSO2 config-mapper's encryption
     * pre-detection does NOT recognise — it then generates its own random key and appends a {@code [encryption]}
     * table, producing a DUPLICATE {@code encryption.key} that fails {@code TomlParser}. Providing the key in
     * the expected table form makes config-mapper use it (no regeneration, no duplicate).
     */
    private static final String ENCRYPTION_KEY = "04e72c83932b2c2647acb836cba836f1e4e4e59ef98e70a56ddeedb04f883f3a";

    private DistributedClusterConfig() {
    }

    /** The three merged component TOML contents. */
    public static final class Tomls {
        public final String acp;
        public final String tm;
        public final String gw;

        Tomls(String acp, String tm, String gw) {
            this.acp = acp;
            this.tm = tm;
            this.gw = gw;
        }
    }

    /**
     * @param moduleDir the cucumber-tests module dir (from {@link ModulePathResolver#getModuleDir}).
     * @return distribution+overlay merged TOMLs for ACP, TM and GW.
     */
    public static Tomls resolve(String moduleDir) throws IOException {
        return resolve(moduleDir, null);
    }

    /**
     * @param extraOverlayRel optional block-specific feature overlay (the block's {@code tomlExtraOverlayPath},
     *        relative to {@code moduleDir}) merged on top of each component's role overlay. Applied to ALL
     *        three components because a feature overlay may target different planes (e.g. custom-auth-header
     *        is a gateway concern, application-sharing an ACP concern); deepMerge only adds keys, so a
     *        component harmlessly ignores keys it doesn't use. Pass {@code null} for none.
     */
    public static Tomls resolve(String moduleDir, String extraOverlayRel) throws IOException {
        Path productRoot = Paths.get(moduleDir, PRODUCT_ROOT_REL).normalize();
        String extra = (extraOverlayRel == null || extraOverlayRel.isBlank())
                ? null : Paths.get(moduleDir, extraOverlayRel).normalize().toString();
        return new Tomls(
                merge(productRoot, ACP_DIST, moduleDir, "acp.toml", extra),
                merge(productRoot, TM_DIST, moduleDir, "tm.toml", extra),
                merge(productRoot, GW_DIST, moduleDir, "gw.toml", extra));
    }

    private static String merge(Path productRoot, String distRel, String moduleDir, String overlayFile,
                                String extraOverlayAbs) throws IOException {
        String base = productRoot.resolve("." + distRel).normalize().toString();
        String roleOverlay = Paths.get(moduleDir, OVERLAY_DIR, overlayFile).normalize().toString();
        java.util.List<String> overlays = extraOverlayAbs == null
                ? java.util.List.of(roleOverlay)
                : java.util.List.of(roleOverlay, extraOverlayAbs);
        String merged = Utils.mergeTomls(base, overlays);
        // Append the shared crypto key in TABLE form (see ENCRYPTION_KEY javadoc). A trailing table is valid
        // TOML regardless of the dotted-key body above it, and config-mapper detects it as the encryption key.
        return merged + "\n[encryption]\nkey = \"" + ENCRYPTION_KEY + "\"\n";
    }
}
