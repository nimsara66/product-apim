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
 */

package org.wso2.am.integration.cucumbertests.stepdefinitions;

import com.microsoft.playwright.PlaywrightException;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import org.json.JSONObject;
import org.testng.Assert;
import org.wso2.am.integration.cucumbertests.utils.IntegrationActors;
import org.wso2.am.integration.cucumbertests.utils.Names;
import org.wso2.am.integration.cucumbertests.utils.PlaywrightSsoClient;
import org.wso2.am.integration.cucumbertests.utils.Requests;
import org.wso2.am.integration.cucumbertests.utils.SsoProvisioner;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.cucumbertests.utils.Utils;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.am.testcontainers.DistributedDynamicApimContainer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Drives the real external OIDC authorization-code path used by FederatedUserJWTTestCase. */
public class FederatedBackendJwtSteps {

    private PlaywrightSsoClient browser;

    @And("I register an external OIDC IdP for backend JWT in tenant {string}")
    public void registerExternalOidcIdp(String tenant) throws Exception {
        // FederatedUserJWTTestCase registers these non-standard OIDC claim names and adds them to the openid
        // scope. Without both pieces, APIM drops mobile/organization before it builds the backend JWT.
        org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner.addOidcExternalClaim(tenant,
                "mobile", "http://wso2.org/claims/mobile");
        org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner.addOidcExternalClaim(tenant,
                "organization", "http://wso2.org/claims/organization");
        org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner.addOidcExternalClaim(tenant,
                "phone_number", "http://wso2.org/claims/telephone");
        org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner.updateOidcScopeClaims(tenant, "openid",
                new String[] {"given_name", "family_name", "mobile", "organization", "phone_number", "email"});
        String[] creds = SsoProvisioner.createIsSsoApp(Names.unique("FED_JWT_IS_APP"), true);
        String idpName = Names.unique("FED_JWT_IDP");
        SsoProvisioner.registerOidcIdp(tenant, idpName, creds[0], creds[1], true);
        String user = Names.unique("federatedJwtUser");
        String password = "FederatedJwt@123";
        String email = user + "@wso2test.com";
        SsoProvisioner.createIsFederatedUser(user, password, email, java.util.List.of("subscriber"), true);
        TestContext.set("fedJwtIdpName", idpName);
        TestContext.set("fedJwtUser", user);
        TestContext.set("fedJwtEmail", email);
        TestContext.set("fedJwtPassword", password);
    }

    @And("I set the OAuth callback URI for this topology")
    public void setOAuthCallbackForTopology() {
        String callback = TestContext.get("blockApimContainer") instanceof DistributedDynamicApimContainer
                ? "https://apim-cp:9443/services/Version" : "https://localhost:9443/services/Version";
        TestContext.set("fedJwtCallback", callback);
    }

    @And("I configure OAuth consumer key {string} for federated OIDC authentication in tenant {string}")
    public void configureOAuthAppForFederation(String consumerKeyRef, String tenant) throws Exception {
        String consumerKey = TestContext.resolve(consumerKeyRef).toString();
        String provider = TestContext.resolve("fedJwtIdpName").toString();
        SsoProvisioner.configureOAuthApplicationForFederation(tenant, consumerKey, provider);
        String[] claimUris = {
                "http://wso2.org/claims/givenname", "http://wso2.org/claims/lastname",
                "http://wso2.org/claims/mobile", "http://wso2.org/claims/organization",
                "http://wso2.org/claims/emailaddress", "http://wso2.org/claims/telephone"
        };
        org.wso2.am.integration.cucumbertests.utils.TenantUserProvisioner
                // Claims returned by a federated provider are requested but optional: marking profile claims
                // mandatory makes APIM interrupt the external authorization-code flow with a claims-entry page
                // whenever the provider omits one, instead of completing the redirect for the existing identity.
                .addRequestedClaimsToServiceProvider(tenant, consumerKey, claimUris, false);
    }

    @When("I obtain an OAuth authorization-code token through the external OIDC IdP")
    public void obtainFederatedAuthorizationCodeToken() throws Exception {
        Object gateway = TestContext.get("baseGatewayUrl");
        browser = new PlaywrightSsoClient(TestContext.get("baseUrl").toString(),
                IntegrationActors.baseUrl(IntegrationActors.IS), gateway == null ? null : gateway.toString());

        String callback = TestContext.resolve("fedJwtCallback").toString();
        String consumerKey = TestContext.resolve("consumerKey").toString();
        // Start the browser at the same internal authority used by APIM's own callback. Starting at the mapped host
        // IP sets the APIM session cookie for that IP, then the external IdP returns to localhost/apim-cp without
        // the cookie, so APIM cannot correlate the authorization state and sends the browser to retry.do.
        String apimInternal = callback.substring(0, callback.indexOf("/services/Version"));
        String authorizeUrl = apimInternal + "/oauth2/authorize"
                + "?response_type=code&client_id=" + Utils.urlEncode(consumerKey)
                + "&redirect_uri=" + Utils.urlEncode(callback) + "&scope=openid";
        String finalRedirect = browser.authorizeOAuthClientThroughExternalIs(authorizeUrl, callback,
                TestContext.resolve("fedJwtUser").toString(), TestContext.resolve("fedJwtPassword").toString());
        String code = Utils.queryParam(finalRedirect, "code");
        Assert.assertNotNull(code, "External OIDC authorization did not return a code: " + finalRedirect);

        String credentials = consumerKey + ":" + TestContext.resolve("consumerSecret");
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)));
        String form = "grant_type=authorization_code&code=" + Utils.urlEncode(code)
                + "&redirect_uri=" + Utils.urlEncode(callback);
        HttpResponse response = Requests.post(Utils.getAPIMTokenEndpointURL(Utils.getBaseUrl()), headers, form,
                "application/x-www-form-urlencoded");
        Assert.assertEquals(response.getResponseCode(), 200,
                "Federated authorization-code exchange failed: " + response.getData());
        JSONObject tokenResponse = new JSONObject(response.getData());
        TestContext.set("generatedAccessToken", tokenResponse.getString("access_token"));
        TestContext.set("tokenResponse", tokenResponse);
    }

    @After
    public void cleanupFederatedBackendJwt() throws Exception {
        if (browser != null) {
            try {
                browser.close();
            } catch (PlaywrightException ignored) {
                // Preserve the test failure if the browser already stopped during an interrupted redirect.
            }
            browser = null;
        }
        // IdPs are block-scoped: the isolated APIM container is discarded after this runner. Once the OAuth
        // service provider references an IdP, APIM refuses to delete it until that SP is removed, so leave the
        // pair together for block teardown rather than emitting a misleading cleanup failure.
    }
}
