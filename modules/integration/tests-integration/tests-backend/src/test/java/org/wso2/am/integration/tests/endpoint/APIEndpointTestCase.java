/*
 *   Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.am.integration.tests.endpoint;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.annotations.*;
import org.wso2.am.integration.clients.publisher.api.ApiException;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIEndpointDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIEndpointListDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyGenerateRequestDTO;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.am.integration.tests.api.lifecycle.APIManagerLifecycleBaseTest;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class APIEndpointTestCase extends APIManagerLifecycleBaseTest {

    private static final Log log = LogFactory.getLog(APIEndpointTestCase.class);

    private final String API_NAME = "APIEndpointTestCase";
    private final String API_CONTEXT = "APIEndpointTestCase";
    private final String API_VERSION_1_0_0 = "1.0.0";
    private final String API_END_POINT_POSTFIX_URL = "xmlapi";
    private String applicationId;
    private String apiId;
    private String accessToken;
    private Map<String, String> apiEndpoints;

    @Factory(dataProvider = "userModeDataProvider")
    public APIEndpointTestCase(TestUserMode userMode) {

        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {

        return new Object[][]{
                new Object[]{TestUserMode.SUPER_TENANT_ADMIN},
                new Object[]{TestUserMode.TENANT_ADMIN},
        };
    }

    @BeforeClass(alwaysRun = true)
    public void initialize() throws Exception {
        super.init(userMode);

        HttpResponse applicationResponse = restAPIStore.createApplication(APPLICATION_NAME,
                "Test Application Endpoint APITestCase", APIMIntegrationConstants.APPLICATION_TIER.UNLIMITED,
                ApplicationDTO.TokenTypeEnum.JWT);
        applicationId = applicationResponse.getData();

        String apiEndPointUrl = getAPIInvocationURLHttp(API_END_POINT_POSTFIX_URL, API_VERSION_1_0_0);
        APIRequest apiRequest = new APIRequest(API_NAME, API_CONTEXT, new URL(apiEndPointUrl));
        apiRequest.setVersion(API_VERSION_1_0_0);
        apiRequest.setTiersCollection(APIMIntegrationConstants.API_TIER.UNLIMITED);
        apiRequest.setTier(APIMIntegrationConstants.API_TIER.UNLIMITED);
        apiRequest.setTags(API_TAGS);
        apiId = createPublishAndSubscribeToAPIUsingRest(apiRequest, restAPIPublisher, restAPIStore, applicationId,
                APIMIntegrationConstants.API_TIER.UNLIMITED);

        ArrayList grantTypes = new ArrayList();
        grantTypes.add("client_credentials");

        ApplicationKeyDTO applicationKeyDTO = restAPIStore.generateKeys(applicationId, "3600", null,
                ApplicationKeyGenerateRequestDTO.KeyTypeEnum.PRODUCTION, null, grantTypes);
        accessToken = applicationKeyDTO.getToken().getAccessToken();
        apiEndpoints = new HashMap<>();
    }

    @Test(groups = {"wso2.am"}, description = "Add API Endpoint.")
    public void testAddNewAPIEndpoint() throws Exception {

        HttpResponse addEndpointResponse = addAPIEndpoint(apiId, "newEndpoint.json");

        assertNotNull(addEndpointResponse, "Error adding API Endpoint.");
        assertEquals(addEndpointResponse.getResponseCode(), 201, "Response code mismatched");

        APIEndpointDTO apiEndpointDTO = new Gson().fromJson(addEndpointResponse.getData(), APIEndpointDTO.class);
        String createdApiEndpointId = apiEndpointDTO.getId();
        assertNotNull(createdApiEndpointId, "APIEndpoint Id is null");

        apiEndpoints.put("createdApiEndpoint", createdApiEndpointId);
        log.info("API Endpoint created : " + createdApiEndpointId);
    }

    @Test(groups = {"wso2.am"}, description = "Get API Endpoint By UUID of Endpoint.",
            dependsOnMethods = {"testAddNewAPIEndpoint"})
    public void testGetAPIEndpointById() throws Exception {
        HttpResponse getEndpointResponse = restAPIPublisher.getAPIEndpointById(
                apiId, apiEndpoints.get("createdApiEndpoint"));

        assertNotNull(getEndpointResponse, "Error getting API Endpoint By UUID.");
        assertEquals(getEndpointResponse.getResponseCode(), 200, "Response code mismatched");

    }

    @Test(groups = {"wso2.am"}, description = "Update API Endpoint By UUID of Endpoint.",
            dependsOnMethods = {"testAddNewAPIEndpoint"})
    public void testUpdateAPIEndpointById() throws Exception {
        HttpResponse updateEndpointResponse = updateAPIEndpoint(apiId, "updateEndpoint.json");

        assertNotNull(updateEndpointResponse, "Error updating API Endpoint By UUID.");
        assertEquals(updateEndpointResponse.getResponseCode(), 200, "Response code mismatched");

        APIEndpointDTO apiEndpointDTO = new Gson().fromJson(updateEndpointResponse.getData(), APIEndpointDTO.class);
        String createdApiEndpointId = apiEndpointDTO.getId();
        assertNotNull(createdApiEndpointId, "APIEndpoint Id is null");
    }

    @Test(groups = {"wso2.am"}, description = "Delete API Endpoint By UUID of Endpoint.",
            dependsOnMethods = {"testAddNewAPIEndpoint"})
    public void testDeleteAPIEndpointById() throws Exception {
        HttpResponse getEndpointResponse = restAPIPublisher.deleteAPIEndpointById(
                apiId, apiEndpoints.get("createdApiEndpoint"));

        assertEquals(getEndpointResponse.getResponseCode(), 200, "Response code mismatched");
        apiEndpoints.remove("createdApiEndpoint");
    }


    @Test(groups = {"wso2.am"}, description = "Get all API Endpoints.")
    public void testGetAllAPIEndpoints() throws Exception {

        HttpResponse getEndpointListResponse = restAPIPublisher.getAllAPIEndpoints(apiId);

        assertNotNull(getEndpointListResponse, "Error getting all API Endpoints.");
        assertEquals(getEndpointListResponse.getResponseCode(), 200, "Response code mismatched");

        APIEndpointListDTO apiEndpointListDTO = new Gson().fromJson(
                getEndpointListResponse.getData(), APIEndpointListDTO.class);
        for (APIEndpointDTO apiEndpointDTO : apiEndpointListDTO.getList()) {
            apiEndpoints.put(apiEndpointDTO.getName(), apiEndpointDTO.getId());
        }
    }


    public HttpResponse addAPIEndpoint(String apiId, String fileName)
            throws APIManagerIntegrationTestException, ApiException {
        String apiEndpointPath = getAMResourceLocation() + File.separator + "endpoint" +
                File.separator + fileName;

        File apiEndpointFile = new File(apiEndpointPath);
        HttpResponse addApiEndpointResponse = restAPIPublisher.addAPIEndpoint(apiId, apiEndpointFile);

        return addApiEndpointResponse;
    }

    public HttpResponse updateAPIEndpoint(String apiId, String fileName)
            throws APIManagerIntegrationTestException, ApiException {
        String apiEndpointPath = getAMResourceLocation() + File.separator + "endpoint" +
                File.separator + fileName;

        File apiEndpointFile = new File(apiEndpointPath);
        HttpResponse updateApiEndpointResponse = restAPIPublisher.updateAPIEndpoint(
                apiId, apiEndpoints.get("createdApiEndpoint"), apiEndpointFile);

        return updateApiEndpointResponse;
    }

    @AfterClass(alwaysRun = true)
    public void cleanUpArtifacts() throws Exception {
        restAPIStore.deleteApplication(applicationId);
        undeployAndDeleteAPIRevisionsUsingRest(apiId, restAPIPublisher);
        restAPIPublisher.deleteAPI(apiId);
    }
}
