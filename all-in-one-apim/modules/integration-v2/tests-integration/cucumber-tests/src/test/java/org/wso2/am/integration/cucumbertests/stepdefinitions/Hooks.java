/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.am.integration.cucumbertests.stepdefinitions;

import io.cucumber.java.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.am.integration.cucumbertests.utils.clients.SimpleHTTPClient;
import org.wso2.am.integration.test.utils.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cucumber lifecycle hooks shared across all step definitions.
 */
public class Hooks {

    private static final Logger logger = LoggerFactory.getLogger(Hooks.class);

    /**
     * Best-effort teardown for scenarios tagged {@code @cleanup}. Deletes every API and application
     * registered during the scenario so that the next scenario in a single shared-server suite starts
     * from a clean slate and does not collide on duplicate resource names (HTTP 409). The deletion is
     * tag-scoped so suites that intentionally persist resources across scenarios (e.g. migration) are
     * never affected. The registered-id lists are cleared afterwards to avoid leaking ids between
     * scenarios executing on the same thread.
     */
    @After("@cleanup")
    public void cleanUpCreatedResources() {

        Object baseUrlObj = TestContext.get("baseUrl");
        if (baseUrlObj == null) {
            return;
        }
        String baseUrl = baseUrlObj.toString();

        try {
            // Delete applications first: removing an application also removes its subscriptions, which
            // would otherwise block deletion of the subscribed API with a 409.
            deleteResources(Constants.CREATED_APPLICATION_IDS, "devportalAccessToken",
                    id -> Utils.getApplicationEndpointURL(baseUrl, id));
            deleteResources(Constants.CREATED_API_IDS, "publisherAccessToken",
                    id -> Utils.getResourceEndpointURL(baseUrl, "apis", id));
        } finally {
            TestContext.remove(Constants.CREATED_API_IDS);
            TestContext.remove(Constants.CREATED_APPLICATION_IDS);
        }
    }

    private void deleteResources(String contextKey, String tokenKey, java.util.function.Function<String, String> urlBuilder) {

        Object tokenObj = TestContext.get(tokenKey);
        if (tokenObj == null) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.REQUEST_HEADERS.AUTHORIZATION, "Bearer " + tokenObj);

        List<Object> ids = TestContext.getList(contextKey);
        for (Object id : ids) {
            if (id == null) {
                continue;
            }
            try {
                SimpleHTTPClient.getInstance().doDelete(urlBuilder.apply(id.toString()), headers);
            } catch (Exception e) {
                // Teardown is best-effort: a resource may already be deleted by the scenario itself.
                logger.warn("Cleanup failed to delete resource {} ({}): {}", id, contextKey, e.getMessage());
            }
        }
    }
}
