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

package org.wso2.am.integration.cucumbertests.stepdefinitions;

import io.cucumber.java.en.When;
import org.testng.Assert;
import org.wso2.am.integration.cucumbertests.utils.Identity;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.am.integration.cucumbertests.utils.clients.SimpleHTTPClient;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Steps for the admin-plane API-category REST API ({@code /api/am/admin/v4/api-categories}). Ports
 * APICategoriesTestCase: category create / negatives / update / list / delete. Categories are tenant-global,
 * so scenarios use uniquely-generated names and delete the category they create as their final step.
 */
public class ApiCategorySteps {

    private final BaseSteps baseSteps = new BaseSteps();

    private String getBaseUrl() {
        return baseSteps.getBaseUrl();
    }

    private Map<String, String> adminHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.REQUEST_HEADERS.AUTHORIZATION, "Bearer " + Identity.adminToken());
        return headers;
    }

    /** Creates an API category (admin), asserts 201 and stores the created id under the given context key. */
    @When("I create an API category with payload {string} as {string}")
    public void iCreateApiCategory(String payload, String categoryIdKey) throws IOException {
        String jsonPayload = Utils.resolveContextPlaceholders(Utils.resolveFromContext(payload).toString());
        TestContext.remove("httpResponse");
        HttpResponse response = SimpleHTTPClient.getInstance().doPost(
                Utils.getApiCategoriesURL(getBaseUrl()), adminHeaders(), jsonPayload,
                Constants.CONTENT_TYPES.APPLICATION_JSON);
        TestContext.set("httpResponse", response);
        Assert.assertEquals(response.getResponseCode(), 201, response.getData());
        TestContext.set(Utils.normalizeContextKey(categoryIdKey),
                Utils.extractValueFromPayload(response.getData(), "id"));
    }

    /** Attempts to create an API category without asserting success — for the negative cases (no name /
     *  special characters / duplicate). The feature asserts the resulting status and body. */
    @When("I attempt to create an API category with payload {string}")
    public void iAttemptToCreateApiCategory(String payload) throws IOException {
        String jsonPayload = Utils.resolveContextPlaceholders(Utils.resolveFromContext(payload).toString());
        TestContext.remove("httpResponse");
        HttpResponse response = SimpleHTTPClient.getInstance().doPost(
                Utils.getApiCategoriesURL(getBaseUrl()), adminHeaders(), jsonPayload,
                Constants.CONTENT_TYPES.APPLICATION_JSON);
        TestContext.set("httpResponse", response);
    }

    /** Updates an API category by id (admin). Non-asserting; the feature confirms the status. */
    @When("I update the API category {string} with payload {string}")
    public void iUpdateApiCategory(String categoryIdKey, String payload) throws IOException {
        String categoryId = Utils.resolveFromContext(categoryIdKey).toString();
        String jsonPayload = Utils.resolveContextPlaceholders(Utils.resolveFromContext(payload).toString());
        TestContext.remove("httpResponse");
        HttpResponse response = SimpleHTTPClient.getInstance().doPut(
                Utils.getApiCategoryByIdURL(getBaseUrl(), categoryId), adminHeaders(), jsonPayload,
                Constants.CONTENT_TYPES.APPLICATION_JSON);
        TestContext.set("httpResponse", response);
    }

    /** Retrieves all API categories (admin). */
    @When("I retrieve all API categories")
    public void iRetrieveAllApiCategories() throws IOException {
        TestContext.remove("httpResponse");
        HttpResponse response = SimpleHTTPClient.getInstance().doGet(
                Utils.getApiCategoriesURL(getBaseUrl()), adminHeaders());
        TestContext.set("httpResponse", response);
    }

    /** Deletes an API category by id (admin). */
    @When("I delete the API category {string}")
    public void iDeleteApiCategory(String categoryIdKey) throws IOException {
        String categoryId = Utils.resolveFromContext(categoryIdKey).toString();
        TestContext.remove("httpResponse");
        HttpResponse response = SimpleHTTPClient.getInstance().doDelete(
                Utils.getApiCategoryByIdURL(getBaseUrl(), categoryId), adminHeaders());
        TestContext.set("httpResponse", response);
    }
}
