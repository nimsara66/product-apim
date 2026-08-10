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

import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.Assert;
import org.wso2.am.integration.cucumbertests.utils.clients.SimpleHTTPClient;
import org.wso2.am.integration.test.utils.Constants;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provisions the multi-tenant portal SSO topology described in the official guide
 * (install-and-setup/setup/sso/configuring-external-idp-using-oidc-for-multi-tenancy), driving APIM's Carbon
 * SOAP admin services and IS 7.x's REST/SCIM2 APIs against the block-booted, host-mapped containers. Every call
 * targets the mapped host URLs ({@code baseUrl} for APIM, {@code isBaseUrl} for IS); the identity artifacts
 * themselves (IdP endpoints, SP callbacks) carry the containers' advertised in-network hosts
 * ({@code wso2is:9443}, {@code localhost:9443}) so APIM's own server-to-server legs resolve correctly.
 *
 * <p>Empirically-derived quirks this class encodes (verified against APIM 9.33.147 + IS 7.3.0):
 * <ul>
 *   <li>{@code addIdP} returns HTTP 500 ("No Identity Provider claim URIs defined for tenant N") but the IdP is
 *       created; the create is verified with {@code getIdPByName}, not the HTTP code. Including {@code idpClaims}
 *       in the {@code claimConfig} makes the claim mappings persist through the fault.</li>
 *   <li>IS 7.x shows a consent page in the federated leg; the IS OIDC app is created with
 *       {@code skipLoginConsent}/{@code skipLogoutConsent} so the headless flow is not stalled.</li>
 *   <li>The SP wiring needs BOTH {@code defaultAuthenticatorConfig} and {@code federatedAuthenticatorConfigs}
 *       (name + enabled) or the federation silently no-ops.</li>
 * </ul>
 */
public final class MultiTenantSsoProvisioner {

    private static final String SOAP_ENV_OPEN =
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">";
    private static final String SOAP_ENV_CLOSE = "</soapenv:Envelope>";
    private static final Pattern APP_ID_PATTERN = Pattern.compile("applicationID>([^<]+)");
    private static final Pattern CONSUMER_KEY_PATTERN =
            Pattern.compile("<ax\\d*:oauthConsumerKey>([^<]+)");

    private MultiTenantSsoProvisioner() {
    }

    /** Base URL (host-mapped) of the booted APIM, e.g. {@code https://localhost:32771/}. */
    private static String apimBase() {
        Object v = TestContext.get("baseUrl");
        if (v == null) {
            throw new IllegalStateException("baseUrl not in context; the SSO block must be booted first");
        }
        return v.toString();
    }

    /** Base URL (host-mapped) of the external IS, e.g. {@code https://localhost:32772/}. */
    private static String isBase() {
        Object v = TestContext.get("isBaseUrl");
        if (v == null) {
            throw new IllegalStateException("isBaseUrl not in context; the SSO block must set "
                    + "initExternalKeyManager=true so the external Identity Server is started");
        }
        return v.toString();
    }

    private static boolean isSuper(String tenantDomain) {
        return tenantDomain == null || tenantDomain.isEmpty()
                || Constants.SUPER_TENANT_DOMAIN.equals(tenantDomain);
    }

    private static Map<String, String> basicAuth(String user, String pass) {
        Map<String, String> h = new HashMap<>();
        h.put(Constants.REQUEST_HEADERS.AUTHORIZATION, "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8)));
        return h;
    }

    private static HttpResponse soap(String url, String body, String action, String user, String pass)
            throws IOException {
        return SimpleHTTPClient.getInstance().sendSoapRequest(url, body, action, user, pass);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Tenants
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Awaits IS -> APIM tenant synchronization: polls APIM's {@code TenantMgtAdminService.getTenant} until the
     * tenant created in IS has been synced AND activated on the APIM side (the sync is two-phase: the
     * TenantSyncListener event creates the tenant, a follow-up activates it - an inactive tenant cannot log in).
     * The IS-side listener retries with exponential backoff, so a generous window is allowed.
     */
    public static void awaitApimTenantSynced(String domain) throws IOException {
        String body = SOAP_ENV_OPEN
                + "<soapenv:Body><ser:getTenant xmlns:ser=\"http://services.mgt.tenant.carbon.wso2.org\">"
                + "<ser:tenantDomain>" + domain + "</ser:tenantDomain></ser:getTenant></soapenv:Body>"
                + SOAP_ENV_CLOSE;
        long deadline = System.currentTimeMillis() + 120_000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse r = soap(apimBase() + "services/TenantMgtAdminService", body, "urn:getTenant",
                    Constants.SUPER_TENANT_ADMIN_USERNAME, Constants.SUPER_TENANT_ADMIN_PASSWORD);
            last = (r == null) ? null : r.getData();
            if (last != null && last.contains("tenantDomain>" + domain)
                    && last.matches("(?s).*:active>true<.*")) {
                return;
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while awaiting tenant sync for " + domain, e);
            }
        }
        Assert.fail("Tenant '" + domain + "' was not synchronized (created+activated) from IS to APIM within "
                + "120s - check the TenantSyncListener config/logs. Last getTenant response: " + last);
    }

    /** Creates a tenant (organization) on the IS side via the tenants REST API (super-admin). */
    public static void createIsTenant(String domain, String adminUser, String adminPass, String email)
            throws IOException {
        String payload = new JSONObject()
                .put("domain", domain)
                .put("owners", new JSONArray().put(new JSONObject()
                        .put("username", adminUser)
                        .put("password", adminPass)
                        .put("email", email)
                        .put("firstname", "SSO")
                        .put("lastname", "Admin")
                        .put("provisioningMethod", "inline-password")))
                .toString();
        SimpleHTTPClient.getInstance().doPost(isBase() + "api/server/v1/tenants",
                basicAuth(Constants.SUPER_TENANT_ADMIN_USERNAME, Constants.SUPER_TENANT_ADMIN_PASSWORD),
                payload, Constants.CONTENT_TYPES.APPLICATION_JSON);
        // 201 = created, 409/other = already present (reuse-enabled IS); either is fine for a repeatable run.
    }

    // ---------------------------------------------------------------------------------------------------------
    // IS-side OIDC application + users/groups
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Creates the IS tenant's OIDC "Traditional Web Application" per the guide (groups user attribute, subject =
     * username without tenant domain, both super and tenant commonauth callbacks), disables login/logout consent
     * so the headless flow is not stalled, and returns its {@code clientId}/{@code clientSecret} in a two-element
     * array {@code [clientId, clientSecret]}.
     */
    public static String[] createIsOidcApp(String tenantDomain, String appName, String adminUser,
                                           String adminPass) throws IOException {
        String isTenantBase = isBase() + "t/" + tenantDomain + "/";
        String payload = new JSONObject()
                .put("name", appName)
                .put("templateId", "b9c5e11e-fc78-484b-9bec-015d247561b8")
                .put("inboundProtocolConfiguration", new JSONObject().put("oidc", new JSONObject()
                        .put("grantTypes", new JSONArray().put("authorization_code"))
                        .put("callbackURLs", new JSONArray().put(
                                "regexp=(https://localhost:9443/commonauth|https://localhost:9443/t/"
                                        + tenantDomain + "/commonauth)"))
                        .put("allowedOrigins", new JSONArray().put("https://localhost:9443"))
                        .put("publicClient", false)))
                .put("claimConfiguration", new JSONObject()
                        .put("dialect", "LOCAL")
                        .put("requestedClaims", new JSONArray().put(new JSONObject()
                                .put("claim", new JSONObject().put("uri", "http://wso2.org/claims/groups"))
                                .put("mandatory", true)))
                        .put("subject", new JSONObject()
                                .put("claim", new JSONObject().put("uri", "http://wso2.org/claims/username"))
                                .put("includeTenantDomain", false)))
                .toString();
        Map<String, String> headers = basicAuth(adminUser, adminPass);
        HttpResponse create = SimpleHTTPClient.getInstance().doPost(
                isTenantBase + "api/server/v1/applications", headers, payload,
                Constants.CONTENT_TYPES.APPLICATION_JSON);
        Assert.assertTrue(create != null && create.getResponseCode() == 201,
                "IS OIDC app create failed: got=" + (create == null ? "null"
                        : create.getResponseCode() + "/" + create.getData()));
        // The created id is in the Location header; the framework HttpResponse exposes headers via getHeaders().
        String appId = locationId(create);
        Assert.assertNotNull(appId, "Could not read created IS app id from Location header");

        // Skip consent so the federated leg does not stall on the consent page (IS 7.x default shows it).
        SimpleHTTPClient.getInstance().doPatch(
                isTenantBase + "api/server/v1/applications/" + appId, headers,
                new JSONObject().put("advancedConfigurations", new JSONObject()
                        .put("skipLoginConsent", true).put("skipLogoutConsent", true)).toString(),
                Constants.CONTENT_TYPES.APPLICATION_JSON);

        HttpResponse oidc = SimpleHTTPClient.getInstance().doGet(
                isTenantBase + "api/server/v1/applications/" + appId + "/inbound-protocols/oidc", headers);
        Assert.assertTrue(oidc != null && oidc.getResponseCode() == 200 && oidc.getData() != null,
                "IS OIDC inbound fetch failed: got=" + (oidc == null ? "null"
                        : oidc.getResponseCode() + "/" + oidc.getData()));
        JSONObject inbound = new JSONObject(oidc.getData());
        return new String[]{inbound.getString("clientId"), inbound.getString("clientSecret")};
    }

    /** Creates a SCIM2 group in the IS tenant and returns its id. */
    public static String createIsGroup(String tenantDomain, String groupName, String adminUser,
                                       String adminPass) throws IOException {
        String url = isBase() + "t/" + tenantDomain + "/scim2/Groups";
        HttpResponse r = SimpleHTTPClient.getInstance().doPost(url, basicAuth(adminUser, adminPass),
                new JSONObject().put("displayName", groupName).toString(),
                Constants.CONTENT_TYPES.APPLICATION_JSON);
        Assert.assertTrue(r != null && r.getResponseCode() == 201 && r.getData() != null,
                "IS group create failed for " + groupName + ": got=" + (r == null ? "null"
                        : r.getResponseCode() + "/" + r.getData()));
        return new JSONObject(r.getData()).getString("id");
    }

    /** Creates a SCIM2 user in the IS tenant and returns its id. */
    public static String createIsUser(String tenantDomain, String userName, String password,
                                      String adminUser, String adminPass) throws IOException {
        String url = isBase() + "t/" + tenantDomain + "/scim2/Users";
        String payload = new JSONObject()
                .put("schemas", new JSONArray().put("urn:ietf:params:scim:schemas:core:2.0:User"))
                .put("userName", userName)
                .put("password", password)
                .put("name", new JSONObject().put("givenName", userName).put("familyName", "SsoTest"))
                .put("emails", new JSONArray().put(new JSONObject()
                        .put("value", userName + "@" + tenantDomain).put("primary", true)))
                .toString();
        HttpResponse r = SimpleHTTPClient.getInstance().doPost(url, basicAuth(adminUser, adminPass),
                payload, Constants.CONTENT_TYPES.APPLICATION_JSON);
        Assert.assertTrue(r != null && r.getResponseCode() == 201 && r.getData() != null,
                "IS user create failed for " + userName + ": got=" + (r == null ? "null"
                        : r.getResponseCode() + "/" + r.getData()));
        return new JSONObject(r.getData()).getString("id");
    }

    /** Adds an IS user (by id) to a SCIM2 group (by id). IS 7.3 requires {@code display} alongside {@code value}. */
    public static void addIsUserToGroup(String tenantDomain, String groupId, String userId, String userName,
                                        String adminUser, String adminPass) throws IOException {
        String url = isBase() + "t/" + tenantDomain + "/scim2/Groups/" + groupId;
        String patch = new JSONObject()
                .put("schemas", new JSONArray().put("urn:ietf:params:scim:api:messages:2.0:PatchOp"))
                .put("Operations", new JSONArray().put(new JSONObject()
                        .put("op", "add").put("path", "members")
                        .put("value", new JSONArray().put(new JSONObject()
                                .put("value", userId).put("display", userName)))))
                .toString();
        HttpResponse r = SimpleHTTPClient.getInstance().doPatch(url, basicAuth(adminUser, adminPass),
                patch, Constants.CONTENT_TYPES.APPLICATION_JSON);
        Assert.assertTrue(r != null && r.getResponseCode() >= 200 && r.getResponseCode() < 300,
                "IS group member add failed for " + userName + ": got=" + (r == null ? "null"
                        : r.getResponseCode() + "/" + r.getData()));
    }

    // ---------------------------------------------------------------------------------------------------------
    // APIM-side identity providers
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Creates the APIM tenant's OIDC IdP ({@code OpenIDConnectAuthenticator}) federating to the IS tenant's OIDC
     * app, with the groups→role claim mapping and role mappings from the guide. Endpoints carry IS's advertised
     * in-network host ({@code wso2is:9443}) so APIM's server-to-server token call resolves. Verified by
     * {@code getIdPByName} because {@code addIdP} returns 500-on-success.
     */
    public static void addApimOidcIdp(String tenantDomain, String idpName, String isClientId,
                                      String isClientSecret, String adminUser, String adminPass)
            throws IOException {
        String fed = "<m:federatedAuthenticatorConfigs><m:displayName>openidconnect</m:displayName>"
                + "<m:enabled>true</m:enabled><m:name>OpenIDConnectAuthenticator</m:name>"
                + property("ClientId", isClientId)
                + property("ClientSecret", isClientSecret)
                + property("OAuth2AuthzEPUrl", "https://wso2is:9443/t/" + tenantDomain + "/oauth2/authorize")
                + property("OAuth2TokenEPUrl", "https://wso2is:9443/t/" + tenantDomain + "/oauth2/token")
                + property("UserInfoUrl", "https://wso2is:9443/t/" + tenantDomain + "/oauth2/userinfo")
                + property("callbackUrl", "https://localhost:9443/commonauth")
                + property("Scopes", "openid groups")
                + property("IsBasicAuthEnabled", "false")
                + "</m:federatedAuthenticatorConfigs>";
        addIdp(tenantDomain, idpName, "OpenIDConnectAuthenticator", fed, adminUser, adminPass);
    }

    /**
     * Creates the super tenant's multi-tenant broker IdP ({@code multiTenantAuthenticator}) with the tenant
     * selection page, common SP name and scopes from the guide. Verified by {@code getIdPByName}.
     */
    public static void addApimMultiTenantIdp(String idpName, String commonSpName, String isClientId,
                                             String isClientSecret) throws IOException {
        String fed = "<m:federatedAuthenticatorConfigs><m:displayName>Multi Tenant Authenticator</m:displayName>"
                + "<m:enabled>true</m:enabled><m:name>multiTenantAuthenticator</m:name>"
                + property("ClientId", isClientId)
                + property("ClientSecret", isClientSecret)
                + property("OAuth2AuthzEPUrl", "https://wso2is:9443/oauth2/authorize")
                + property("OAuth2TokenEPUrl", "https://wso2is:9443/oauth2/token")
                + property("UserInfoUrl", "https://wso2is:9443/oauth2/userinfo")
                + property("callbackUrl", "https://localhost:9443/commonauth")
                + property("Scopes", "openid groups")
                + property("CommonSPName", commonSpName)
                + property("TenantSelectionPageUrl", "https://localhost:9443/select-tenant/")
                + property("IsBasicAuthEnabled", "false")
                + "</m:federatedAuthenticatorConfigs>";
        addIdp(Constants.SUPER_TENANT_DOMAIN, idpName, "multiTenantAuthenticator", fed,
                Constants.SUPER_TENANT_ADMIN_USERNAME, Constants.SUPER_TENANT_ADMIN_PASSWORD);
    }

    /** Shared {@code addIdP} body builder + create-then-verify. {@code idpClaims} makes claim mappings persist. */
    private static void addIdp(String tenantDomain, String idpName, String defaultAuth, String federatedConfig,
                               String adminUser, String adminPass) throws IOException {
        String body = SOAP_ENV_OPEN
                + "<soapenv:Body><ns:addIdP xmlns:ns=\"http://mgt.idp.carbon.wso2.org\" "
                + "xmlns:m=\"http://model.common.application.identity.carbon.wso2.org/xsd\">"
                + "<ns:identityProvider>"
                + "<m:claimConfig>"
                + "<m:claimMappings><m:localClaim><m:claimUri>http://wso2.org/claims/role</m:claimUri>"
                + "</m:localClaim><m:remoteClaim><m:claimUri>groups</m:claimUri></m:remoteClaim></m:claimMappings>"
                + "<m:idpClaims><m:claimUri>groups</m:claimUri></m:idpClaims>"
                + "<m:localClaimDialect>false</m:localClaimDialect>"
                + "<m:roleClaimURI>groups</m:roleClaimURI>"
                + "</m:claimConfig>"
                + "<m:defaultAuthenticatorConfig><m:name>" + defaultAuth + "</m:name></m:defaultAuthenticatorConfig>"
                + "<m:enable>true</m:enable>"
                + federatedConfig
                + "<m:federationHub>false</m:federationHub>"
                + "<m:identityProviderName>" + idpName + "</m:identityProviderName>"
                + "<m:justInTimeProvisioningConfig><m:provisioningEnabled>true</m:provisioningEnabled>"
                + "<m:passwordProvisioningEnabled>true</m:passwordProvisioningEnabled>"
                + "</m:justInTimeProvisioningConfig>"
                + "<m:permissionAndRoleConfig>"
                + roleMapping("publisher", "Internal/publisher")
                + roleMapping("devportal", "Internal/subscriber")
                + "</m:permissionAndRoleConfig>"
                + "</ns:identityProvider></ns:addIdP></soapenv:Body>" + SOAP_ENV_CLOSE;
        // addIdP returns 500-on-success; verify with getIdPByName rather than asserting the HTTP code.
        soap(idpServiceUrl(tenantDomain), body, "urn:addIdP", adminUser, adminPass);
        Assert.assertTrue(idpExists(tenantDomain, idpName, adminUser, adminPass),
                "IdP '" + idpName + "' was not created in tenant '" + tenantDomain + "' (addIdP reported failure "
                        + "and getIdPByName does not find it)");
    }

    private static boolean idpExists(String tenantDomain, String idpName, String adminUser, String adminPass)
            throws IOException {
        String body = SOAP_ENV_OPEN
                + "<soapenv:Body><ns:getIdPByName xmlns:ns=\"http://mgt.idp.carbon.wso2.org\">"
                + "<ns:idPName>" + idpName + "</ns:idPName></ns:getIdPByName></soapenv:Body>" + SOAP_ENV_CLOSE;
        HttpResponse r = soap(idpServiceUrl(tenantDomain), body, "urn:getIdPByName", adminUser, adminPass);
        return r != null && r.getData() != null && r.getData().contains("identityProviderName>" + idpName);
    }

    private static String idpServiceUrl(String tenantDomain) {
        return apimBase() + tenantPrefixNoLeadSlash(tenantDomain) + "services/IdentityProviderMgtService";
    }

    // ---------------------------------------------------------------------------------------------------------
    // APIM-side service providers (commonsp + portal SP)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Registers the {@code commonsp} OAuth2 client, creates the bare SP, and wires it: OAuth2 inbound (its own
     * client) + federated outbound to the tenant OIDC IdP, with the username/roles claim config and
     * {@code useMappedLocalSubject} from the guide. Returns nothing; the SP is referenced by name thereafter.
     */
    public static void setupCommonServiceProvider(String tenantDomain, String spName, String oidcIdpName,
                                                  String adminUser, String adminPass) throws IOException {
        // 1. Register the OAuth client.
        String regBody = SOAP_ENV_OPEN
                + "<soapenv:Body><ax:registerAndRetrieveOAuthApplicationData "
                + "xmlns:ax=\"http://org.apache.axis2/xsd\" "
                + "xmlns:dto=\"http://dto.oauth.identity.carbon.wso2.org/xsd\"><ax:application>"
                + "<dto:OAuthVersion>OAuth-2.0</dto:OAuthVersion>"
                + "<dto:applicationName>" + spName + "</dto:applicationName>"
                + "<dto:callbackUrl>https://localhost:9443/commonauth</dto:callbackUrl>"
                + "<dto:grantTypes>authorization_code refresh_token</dto:grantTypes>"
                + "</ax:application></ax:registerAndRetrieveOAuthApplicationData></soapenv:Body>" + SOAP_ENV_CLOSE;
        HttpResponse reg = soap(oauthServiceUrl(tenantDomain), regBody,
                "urn:registerAndRetrieveOAuthApplicationData", adminUser, adminPass);
        String consumerKey = firstMatch(CONSUMER_KEY_PATTERN, reg == null ? null : reg.getData());
        Assert.assertNotNull(consumerKey, "Could not read commonsp consumer key: got="
                + (reg == null ? "null" : reg.getData()));

        // 2. Create the bare SP.
        String createBody = SOAP_ENV_OPEN
                + "<soapenv:Body><axis2:createApplication xmlns:axis2=\"http://org.apache.axis2/xsd\">"
                + "<axis2:serviceProvider "
                + "xmlns:m=\"http://model.common.application.identity.carbon.wso2.org/xsd\">"
                + "<m:applicationName>" + spName + "</m:applicationName><m:saasApp>true</m:saasApp>"
                + "</axis2:serviceProvider></axis2:createApplication></soapenv:Body>" + SOAP_ENV_CLOSE;
        soap(appServiceUrl(tenantDomain), createBody, "urn:createApplication", adminUser, adminPass);

        // 3. Read back its numeric applicationID.
        String appId = getApplicationId(tenantDomain, spName, adminUser, adminPass);
        Assert.assertNotNull(appId, "Could not read commonsp applicationID after create");

        // 4. Wire inbound OAuth2 + federated outbound to the OIDC IdP, with claim config + mapped subject.
        String updateBody = SOAP_ENV_OPEN
                + "<soapenv:Body><axis2:updateApplication xmlns:axis2=\"http://org.apache.axis2/xsd\">"
                + "<axis2:serviceProvider "
                + "xmlns:m=\"http://model.common.application.identity.carbon.wso2.org/xsd\">"
                + "<m:applicationID>" + appId + "</m:applicationID>"
                + "<m:applicationName>" + spName + "</m:applicationName>"
                + "<m:claimConfig>"
                + spClaimMapping("http://wso2.org/claims/username")
                + spClaimMapping("http://wso2.org/claims/roles")
                + "<m:localClaimDialect>true</m:localClaimDialect></m:claimConfig>"
                + "<m:inboundAuthenticationConfig><m:inboundAuthenticationRequestConfigs>"
                + "<m:inboundAuthKey>" + consumerKey + "</m:inboundAuthKey>"
                + "<m:inboundAuthType>oauth2</m:inboundAuthType>"
                + "</m:inboundAuthenticationRequestConfigs></m:inboundAuthenticationConfig>"
                + federatedOutbound(oidcIdpName, "OpenIDConnectAuthenticator")
                + "<m:subjectClaimUri>http://wso2.org/claims/username</m:subjectClaimUri>"
                + "</m:localAndOutBoundAuthenticationConfig>"
                + "<m:saasApp>true</m:saasApp></axis2:serviceProvider>"
                + "</axis2:updateApplication></soapenv:Body>" + SOAP_ENV_CLOSE;
        soap(appServiceUrl(tenantDomain), updateBody, "urn:updateApplication", adminUser, adminPass);
    }

    /**
     * Triggers auto-creation of the publisher portal SP (a first hit to the portal login endpoint) and wires it
     * to federate to the multi-tenant broker IdP in the super tenant, preserving its own OAuth inbound client.
     */
    public static void wirePublisherPortalToMultiTenantIdp(String portalLoginUrl, String portalSpName,
                                                           String mtIdpName) throws IOException {
        // Trigger creation of the portal SP (idempotent: a 302 to the tenant-selection or authorize).
        SimpleHTTPClient.getInstance().doGet(portalLoginUrl, new HashMap<>());

        String appId = getApplicationId(Constants.SUPER_TENANT_DOMAIN, portalSpName,
                Constants.SUPER_TENANT_ADMIN_USERNAME, Constants.SUPER_TENANT_ADMIN_PASSWORD);
        Assert.assertNotNull(appId, "Portal SP '" + portalSpName + "' not found after triggering login");
        String consumerKey = getSpInboundAuthKey(Constants.SUPER_TENANT_DOMAIN, portalSpName);
        Assert.assertNotNull(consumerKey, "Portal SP '" + portalSpName + "' has no OAuth inbound key");

        String updateBody = SOAP_ENV_OPEN
                + "<soapenv:Body><axis2:updateApplication xmlns:axis2=\"http://org.apache.axis2/xsd\">"
                + "<axis2:serviceProvider "
                + "xmlns:m=\"http://model.common.application.identity.carbon.wso2.org/xsd\">"
                + "<m:applicationID>" + appId + "</m:applicationID>"
                + "<m:applicationName>" + portalSpName + "</m:applicationName>"
                + "<m:inboundAuthenticationConfig><m:inboundAuthenticationRequestConfigs>"
                + "<m:inboundAuthKey>" + consumerKey + "</m:inboundAuthKey>"
                + "<m:inboundAuthType>oauth2</m:inboundAuthType>"
                + "</m:inboundAuthenticationRequestConfigs></m:inboundAuthenticationConfig>"
                + federatedOutbound(mtIdpName, "multiTenantAuthenticator")
                + "<m:useTenantDomainInLocalSubjectIdentifier>false</m:useTenantDomainInLocalSubjectIdentifier>"
                + "<m:useUserstoreDomainInLocalSubjectIdentifier>false"
                + "</m:useUserstoreDomainInLocalSubjectIdentifier>"
                + "</m:localAndOutBoundAuthenticationConfig>"
                + "<m:saasApp>true</m:saasApp></axis2:serviceProvider>"
                + "</axis2:updateApplication></soapenv:Body>" + SOAP_ENV_CLOSE;
        soap(appServiceUrl(Constants.SUPER_TENANT_DOMAIN), updateBody, "urn:updateApplication",
                Constants.SUPER_TENANT_ADMIN_USERNAME, Constants.SUPER_TENANT_ADMIN_PASSWORD);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Small builders / readers
    // ---------------------------------------------------------------------------------------------------------

    private static String federatedOutbound(String idpName, String authenticatorName) {
        return "<m:localAndOutBoundAuthenticationConfig><m:authenticationSteps><m:federatedIdentityProviders>"
                + "<m:defaultAuthenticatorConfig><m:name>" + authenticatorName + "</m:name>"
                + "<m:enabled>true</m:enabled></m:defaultAuthenticatorConfig>"
                + "<m:federatedAuthenticatorConfigs><m:name>" + authenticatorName + "</m:name>"
                + "<m:enabled>true</m:enabled></m:federatedAuthenticatorConfigs>"
                + "<m:identityProviderName>" + idpName + "</m:identityProviderName>"
                + "</m:federatedIdentityProviders><m:stepOrder>1</m:stepOrder></m:authenticationSteps>"
                + "<m:authenticationType>federated</m:authenticationType>";
    }

    private static String spClaimMapping(String uri) {
        return "<m:claimMappings><m:localClaim><m:claimUri>" + uri + "</m:claimUri></m:localClaim>"
                + "<m:mandatory>false</m:mandatory><m:remoteClaim><m:claimUri>" + uri + "</m:claimUri>"
                + "</m:remoteClaim><m:requested>true</m:requested></m:claimMappings>";
    }

    private static String property(String name, String value) {
        return "<m:properties><m:name>" + name + "</m:name><m:value>" + value + "</m:value></m:properties>";
    }

    private static String roleMapping(String remote, String local) {
        return "<m:roleMappings><m:localRole><m:localRoleName>" + local + "</m:localRoleName></m:localRole>"
                + "<m:remoteRole>" + remote + "</m:remoteRole></m:roleMappings>";
    }

    private static String getApplicationId(String tenantDomain, String spName, String adminUser,
                                           String adminPass) throws IOException {
        String body = SOAP_ENV_OPEN
                + "<soapenv:Body><ns1:getApplication xmlns:ns1=\"http://org.apache.axis2/xsd\">"
                + "<ns1:applicationName>" + spName + "</ns1:applicationName></ns1:getApplication></soapenv:Body>"
                + SOAP_ENV_CLOSE;
        HttpResponse r = soap(appServiceUrl(tenantDomain), body, "urn:getApplication", adminUser, adminPass);
        return firstMatch(APP_ID_PATTERN, r == null ? null : r.getData());
    }

    private static String getSpInboundAuthKey(String tenantDomain, String spName) throws IOException {
        String body = SOAP_ENV_OPEN
                + "<soapenv:Body><ns1:getApplication xmlns:ns1=\"http://org.apache.axis2/xsd\">"
                + "<ns1:applicationName>" + spName + "</ns1:applicationName></ns1:getApplication></soapenv:Body>"
                + SOAP_ENV_CLOSE;
        HttpResponse r = soap(appServiceUrl(tenantDomain), body, "urn:getApplication",
                Constants.SUPER_TENANT_ADMIN_USERNAME, Constants.SUPER_TENANT_ADMIN_PASSWORD);
        return firstMatch(Pattern.compile("inboundAuthKey>([^<]+)"), r == null ? null : r.getData());
    }

    private static String oauthServiceUrl(String tenantDomain) {
        return apimBase() + tenantPrefixNoLeadSlash(tenantDomain) + "services/OAuthAdminService";
    }

    private static String appServiceUrl(String tenantDomain) {
        return apimBase() + tenantPrefixNoLeadSlash(tenantDomain)
                + "services/IdentityApplicationManagementService";
    }

    /** Tenant path segment appended after the base URL (base already ends with '/'): {@code t/<domain>/} or "". */
    private static String tenantPrefixNoLeadSlash(String tenantDomain) {
        return isSuper(tenantDomain) ? "" : "t/" + tenantDomain + "/";
    }

    private static String locationId(HttpResponse resp) {
        if (resp == null || resp.getHeaders() == null) {
            return null;
        }
        String location = resp.getHeaders().get("Location");
        if (location == null) {
            location = resp.getHeaders().get("location");
        }
        if (location == null) {
            return null;
        }
        int slash = location.lastIndexOf('/');
        return slash >= 0 ? location.substring(slash + 1) : location;
    }

    private static String firstMatch(Pattern p, String s) {
        if (s == null) {
            return null;
        }
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
