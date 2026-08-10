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

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.wso2.am.integration.cucumbertests.utils.MultiTenantSsoProvisioner;
import org.wso2.am.integration.cucumbertests.utils.TestContext;
import org.wso2.am.integration.cucumbertests.utils.Utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.CookieHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steps for the multi-tenant portal SSO block: provisions the nested-OIDC broker topology (see
 * {@link MultiTenantSsoProvisioner}) and drives the Publisher login end-to-end, headlessly, over HTTP.
 *
 * <p>The login driver follows every 302 manually (no auto-redirect) with a single shared cookie jar so the
 * load-bearing session cookies survive the ~30-hop chain. Because the two servers advertise their in-network
 * hosts ({@code https://localhost:9443} for APIM, {@code https://wso2is:9443} for IS) in the redirect Locations,
 * and the driver runs on the host, only the URL AUTHORITY of each Location is rewritten to the host-mapped base
 * URL - the query-string values (notably {@code redirect_uri}) are left intact, or APIM's server-side callback
 * validation would rightly reject them.
 */
public class MultiTenantSsoSteps {

    private static final Log logger = LogFactory.getLog(MultiTenantSsoSteps.class);

    /** In-network host authorities the servers advertise in redirect Locations. */
    private static final String APIM_ADVERTISED = "https://localhost:9443";
    private static final String IS_ADVERTISED = "https://wso2is:9443";

    private static final Pattern SESSION_DATA_KEY_HTML =
            Pattern.compile("name=\"sessionDataKey\"[^>]*value=\"([^\"]+)\"");

    // ---------------------------------------------------------------------------------------------------------
    // Provisioning steps (the setup feature)
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Creates the tenant in the IDENTITY SERVER and awaits its IS -> APIM synchronization (the block's IS runs
     * the TenantSyncListener via isTomlExtraOverlayPath), exercising the tenant-sharing path the official SSO
     * guide prescribes instead of creating the APIM tenant directly. The synced APIM tenant carries the same
     * admin credentials as the IS tenant.
     */
    @When("I provision the SSO tenant {string} on the identity server and await its sync to API Manager with admin password {string}")
    public void iProvisionTenant(String domain, String adminPass) throws Exception {
        String email = "admin@" + domain;
        MultiTenantSsoProvisioner.createIsTenant(domain, "admin", adminPass, email);
        MultiTenantSsoProvisioner.awaitApimTenantSynced(domain);
        TestContext.set("ssoTenantDomain", domain);
        TestContext.set("ssoTenantAdmin", "admin@" + domain);
        TestContext.set("ssoTenantAdminPassword", adminPass);
    }

    @When("I provision the SSO identity server application in tenant {string}")
    public void iProvisionIsApp(String domain) throws Exception {
        String adminUser = TestContext.resolve("ssoTenantAdmin").toString();
        String adminPass = TestContext.resolve("ssoTenantAdminPassword").toString();
        String[] creds = MultiTenantSsoProvisioner.createIsOidcApp(domain, "APIM_SSO", adminUser, adminPass);
        TestContext.set("ssoIsClientId", creds[0]);
        TestContext.set("ssoIsClientSecret", creds[1]);
    }

    @When("I provision the SSO user {string} with password {string} in groups {string} in tenant {string}")
    public void iProvisionUser(String userName, String password, String groupsCsv, String domain)
            throws Exception {
        String adminUser = TestContext.resolve("ssoTenantAdmin").toString();
        String adminPass = TestContext.resolve("ssoTenantAdminPassword").toString();
        String userId = MultiTenantSsoProvisioner.createIsUser(domain, userName, password, adminUser, adminPass);
        for (String group : groupsCsv.split(",")) {
            String g = group.trim();
            if (g.isEmpty()) {
                continue;
            }
            String groupId = MultiTenantSsoProvisioner.createIsGroup(domain, g, adminUser, adminPass);
            MultiTenantSsoProvisioner.addIsUserToGroup(domain, groupId, userId, userName, adminUser, adminPass);
        }
    }

    @When("I configure the SSO tenant OIDC identity provider {string} in tenant {string}")
    public void iConfigureTenantOidcIdp(String idpName, String domain) throws Exception {
        String adminUser = TestContext.resolve("ssoTenantAdmin").toString();
        String adminPass = TestContext.resolve("ssoTenantAdminPassword").toString();
        String cid = TestContext.resolve("ssoIsClientId").toString();
        String csec = TestContext.resolve("ssoIsClientSecret").toString();
        MultiTenantSsoProvisioner.addApimOidcIdp(domain, idpName, cid, csec, adminUser, adminPass);
    }

    @When("I configure the SSO common service provider {string} federating to {string} in tenant {string}")
    public void iConfigureCommonSp(String spName, String idpName, String domain) throws Exception {
        String adminUser = TestContext.resolve("ssoTenantAdmin").toString();
        String adminPass = TestContext.resolve("ssoTenantAdminPassword").toString();
        MultiTenantSsoProvisioner.setupCommonServiceProvider(domain, spName, idpName, adminUser, adminPass);
    }

    @When("I configure the SSO super tenant multi-tenant identity provider {string} using common service provider {string}")
    public void iConfigureMtIdp(String idpName, String commonSpName) throws Exception {
        String cid = TestContext.resolve("ssoIsClientId").toString();
        String csec = TestContext.resolve("ssoIsClientSecret").toString();
        MultiTenantSsoProvisioner.addApimMultiTenantIdp(idpName, commonSpName, cid, csec);
    }

    @When("I wire the publisher portal service provider to multi-tenant identity provider {string}")
    public void iWirePortal(String mtIdpName) throws Exception {
        MultiTenantSsoProvisioner.wirePublisherPortalToMultiTenantIdp(
                apimBase() + "publisher/services/auth/login", "apim_publisher", mtIdpName);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The login flow (the action under test) + assertions
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Drives the full headless Publisher login through the nested broker: start at the publisher login endpoint,
     * follow to the tenant-selection page, POST the tenant choice, follow to the IS login page, POST the user's
     * credentials, then follow the callback chain back to APIM. Records the final landing URL, whether an
     * authorization code was delivered to the publisher callback, and whether the APIM access-token cookie was
     * set - all under context keys the assertion steps read.
     */
    @When("user {string} completes the multi-tenant publisher login selecting tenant {string} with password {string}")
    public void userCompletesLogin(String user, String tenant, String password) throws Exception {
        HttpClient http = trustAllHttpClientWithCookies();
        StringBuilder trace = new StringBuilder();

        // 1. Start login; follow to the tenant-selection page (a 200 whose URL is /select-tenant/...).
        String body = getFollowingToSelectTenant(http);
        String sdk1 = firstMatch(SESSION_DATA_KEY_HTML, body);
        Assert.assertNotNull(sdk1, "Did not reach the tenant-selection page (no sessionDataKey in body)");

        // 2. POST the tenant choice to /commonauth.
        String loc = post(http, APIM_ADVERTISED + "/commonauth",
                "sessionDataKey=" + Utils.urlEncode(sdk1) + "&authenticator=multiTenantAuthenticator"
                        + "&idp=" + Utils.urlEncode(TestContext.resolve("ssoMtIdpName").toString())
                        + "&tenantIdentifier=" + Utils.urlEncode(tenant));

        // 3. Follow the chain to the IS login page (login.do carries sessionDataKey on its URL).
        String loginDo = null;
        for (int i = 0; i < 12 && loc != null; i++) {
            if (loc.contains("login.do")) {
                loginDo = loc;
            }
            trace.append("\n  fwd").append(i).append(": ").append(abbreviate(loc));
            String[] hop = getHop(http, loc);
            loc = hop[0];
            if (loc == null && hop[1] != null && hop[1].contains("login.do")) {
                loginDo = hop[1];
            }
        }
        Assert.assertNotNull(loginDo, "Did not reach the identity server login page (login.do). Trace:" + trace);

        // 4. POST the user's credentials to IS's commonauth (tenant-qualified).
        String isk = Utils.queryParam(loginDo, "sessionDataKey");
        Matcher tp = Pattern.compile("(/t/[^/]+)/authenticationendpoint").matcher(loginDo);
        String isCommonAuth = IS_ADVERTISED + (tp.find() ? tp.group(1) : "") + "/commonauth";
        loc = post(http, isCommonAuth,
                "username=" + Utils.urlEncode(user) + "&password=" + Utils.urlEncode(password) + "&sessionDataKey=" + Utils.urlEncode(isk));
        trace.append("\n  IS-creds-POST -> ").append(abbreviate(loc));

        // 5. Follow the callback chain back; detect the publisher landing + authz-code hop + fail signature.
        boolean landedInPublisher = false;
        boolean codeDeliveredToPublisher = false;
        String failSignatureLocation = null;
        for (int i = 0; i < 16 && loc != null; i++) {
            if (loc.contains("error_description")) {
                failSignatureLocation = loc;
            }
            String[] hop = getHop(http, loc);
            trace.append("\n  back").append(i).append(" [").append(hop[2]).append(" sc=").append(hop[3])
                    .append("]: ").append(abbreviate(loc));
            String next = hop[0];
            if (next != null && next.contains("callback/login") && next.contains("code=")) {
                codeDeliveredToPublisher = true;
            }
            if (next != null && next.replaceAll("[?#].*$", "").replaceAll("/+$", "")
                    .endsWith("/publisher")) {
                landedInPublisher = true;
            }
            loc = next;
            if (landedInPublisher) {
                break;
            }
        }

        HostScopedCookieHandler cookies = (HostScopedCookieHandler) http.cookieHandler().orElseThrow();
        boolean tokenCookie = cookies.hasCookie("AM_ACC_TOKEN_DEFAULT_P2");

        TestContext.set("ssoLandedInPublisher", landedInPublisher);
        TestContext.set("ssoCodeDeliveredToPublisher", codeDeliveredToPublisher);
        TestContext.set("ssoAccessTokenCookiePresent", tokenCookie);
        TestContext.set("ssoFailSignatureLocation", failSignatureLocation == null ? "" : failSignatureLocation);
        trace.append("\n  cookies: ").append(cookies.summary());
        TestContext.set("ssoHopTrace", trace.toString());
        logger.info("SSO login hop trace:" + trace);
    }

    /** The exact IS-side rejection API Manager logs when its outbound token call presents two client-auth methods. */
    private static final String DOUBLE_CLIENT_AUTH_ERROR =
            "The client MUST NOT use more than one authentication method";

    /**
     * Asserts the documented multi-tenant SSO token-exchange regression: no authorization code reaches the
     * publisher callback, the login ends in login_required, and - to prove it is THIS bug and not a look-alike
     * (PKIX, consent, timeout) - API Manager's own log carries the double-client-auth rejection. This pins the
     * CURRENT broken behaviour (see the feature description); it is expected to fail (turn RED) once API Manager
     * stops leaking the client key manager into the global SSL context, at which point it should be flipped to
     * assert the success path.
     */
    @Then("the multi-tenant publisher login is rejected with the token-exchange double client-auth regression")
    public void thenRejectedWithRegression() {
        String trace = String.valueOf(TestContext.get("ssoHopTrace"));
        Assert.assertEquals(TestContext.resolve("ssoCodeDeliveredToPublisher"), Boolean.FALSE,
                "Expected the token-exchange regression (no code to the publisher), but a code WAS delivered - "
                        + "the flow may be fixed; flip this to the success assertion. Hop trace:" + trace);
        Assert.assertEquals(TestContext.resolve("ssoLandedInPublisher"), Boolean.FALSE,
                "Expected login_required, but the chain landed in the publisher - the flow may be fixed; flip "
                        + "this to the success assertion. Hop trace:" + trace);
        String failSig = String.valueOf(TestContext.get("ssoFailSignatureLocation"));
        Assert.assertTrue(failSig.contains("login_required") || failSig.contains("Authentication+required"),
                "Expected a login_required / Authentication+required fail signature but got: '" + failSig
                        + "'. Hop trace:" + trace);
        // Belt-and-suspenders: confirm the ROOT cause in API Manager's log, so an unrelated login failure
        // (PKIX handshake, consent stall, auth-context timeout) does not masquerade as this regression.
        String apimLog = apimContainerLogs();
        Assert.assertTrue(apimLog.contains(DOUBLE_CLIENT_AUTH_ERROR),
                "The login failed but API Manager's log does not contain the double-client-auth rejection ('"
                        + DOUBLE_CLIENT_AUTH_ERROR + "') - the failure is NOT the token-exchange regression this "
                        + "test pins (check for PKIX/consent/timeout). Hop trace:" + trace);
    }

    /** Reads the booted API Manager container's stdout+stderr logs for root-cause assertions. */
    private static String apimContainerLogs() {
        Object container = TestContext.get("blockApimContainer");
        Assert.assertNotNull(container, "blockApimContainer not in context; cannot read API Manager logs");
        return ((org.testcontainers.containers.GenericContainer<?>) container).getLogs();
    }

    /** Records the multi-tenant broker IdP name so the login step can pass it to /commonauth. */
    @When("I remember the SSO multi-tenant identity provider name {string}")
    public void iRememberMtIdpName(String name) {
        TestContext.set("ssoMtIdpName", name);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Headless HTTP plumbing
    // ---------------------------------------------------------------------------------------------------------

    /** Base URL (host-mapped) of the booted APIM. */
    private static String apimBase() {
        Object v = TestContext.get("baseUrl");
        if (v == null) {
            throw new IllegalStateException("baseUrl not in context; the SSO block must be booted first");
        }
        return v.toString();
    }

    /**
     * Rewrites ONLY the advertised authority of a redirect Location to the host-mapped base URL, and percent-
     * encodes spaces so {@link URI#create} accepts the URL (APIM emits the {@code scope} query param with raw
     * spaces in the initial authorize redirect). Only the authority is swapped; query values are left intact
     * (bar the space-encoding) so {@code redirect_uri} still matches APIM's registered callback.
     */
    private String rewrite(String url) {
        if (url == null) {
            return null;
        }
        String apim = trimSlash(apimBase());
        String is = trimSlash(TestContext.resolve("isBaseUrl").toString());
        String rewritten;
        if (url.startsWith(APIM_ADVERTISED)) {
            rewritten = apim + url.substring(APIM_ADVERTISED.length());
        } else if (url.startsWith(IS_ADVERTISED)) {
            rewritten = is + url.substring(IS_ADVERTISED.length());
        } else {
            rewritten = url;
        }
        return rewritten.replace(" ", "%20");
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Starts at the publisher login endpoint and follows redirects until the select-tenant page (200) is read. */
    private String getFollowingToSelectTenant(HttpClient http) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(apimBase() + "publisher/services/auth/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        for (int i = 0; i < 10; i++) {
            String loc = resp.headers().firstValue("location").orElse(null);
            if (loc == null) {
                return resp.body();
            }
            resp = http.send(HttpRequest.newBuilder(URI.create(rewrite(loc))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        return resp.body();
    }

    /**
     * GETs a (rewritten) URL without following its redirect; returns
     * {@code [nextLocation, effectiveUrl, statusCode, setCookieNames]}.
     */
    private String[] getHop(HttpClient http, String url) throws Exception {
        String target = rewrite(url);
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(target)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return new String[]{resp.headers().firstValue("location").orElse(null), target,
                String.valueOf(resp.statusCode()), setCookieNames(resp)};
    }

    /** Comma-joined names from a response's Set-Cookie headers, for diagnostic tracing. */
    private static String setCookieNames(HttpResponse<?> resp) {
        java.util.List<String> sc = resp.headers().allValues("set-cookie");
        if (sc.isEmpty()) {
            return "-";
        }
        StringBuilder b = new StringBuilder();
        for (String c : sc) {
            int eq = c.indexOf('=');
            b.append(eq > 0 ? c.substring(0, eq) : c).append(',');
        }
        return b.toString();
    }

    /** POSTs a form body to a (rewritten) URL without following the redirect; returns the next Location. */
    private String post(HttpClient http, String url, String formBody) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(rewrite(url)))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.headers().firstValue("location").orElse(null);
    }

    private HttpClient trustAllHttpClientWithCookies() throws Exception {
        TrustManager[] trustAll = {new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trustAll, new SecureRandom());
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        return HttpClient.newBuilder()
                .sslContext(ssl)
                .cookieHandler(new HostScopedCookieHandler())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Trims a URL to its path + a short query prefix for readable hop logging (no secrets/scopes dumped). */
    private static String abbreviate(String url) {
        if (url == null) {
            return "(none)";
        }
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q) + "?" + url.substring(q + 1,
                Math.min(url.length(), q + 60));
    }

    private static String firstMatch(Pattern p, String s) {
        if (s == null) {
            return null;
        }
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /**
     * A cookie handler for the SSO redirect chain that mirrors the Python reference driver / a browser precisely,
     * where the JDK {@code CookieManager} does not. Two properties matter for this flow:
     * <ul>
     *   <li><b>Keyed by authority (host:port), not host.</b> The driver rewrites BOTH servers to
     *       {@code localhost:<mapped-port>}, so keying by host alone would conflate APIM's and IS's cookies (both
     *       {@code localhost}) - e.g. their separate {@code commonAuthId}/{@code opbs} would clobber each other.
     *       Keying by authority keeps each server's jar separate.</li>
     *   <li><b>Path-scoped, send all path-matching, longest-path first.</b> APIM issues a super-tenant
     *       {@code commonAuthId} (Path=/) AND a tenant {@code commonAuthId} (Path=/t/abc.com/); collapsing them to
     *       one value (as a path-agnostic jar does) makes the commonsp authorize resume fail with
     *       "Authentication required". Storing per (name,path) and sending every cookie whose path is a prefix of
     *       the request path - most specific first - delivers the right {@code commonAuthId} to both the tenant
     *       authorize and the super commonauth.</li>
     * </ul>
     */
    private static final class HostScopedCookieHandler extends CookieHandler {

        /** authority (host:port) -> list of stored cookies. */
        private final Map<String, List<StoredCookie>> byAuthority = new ConcurrentHashMap<>();

        private static final class StoredCookie {
            final String name;
            final String value;
            final String path;

            StoredCookie(String name, String value, String path) {
                this.name = name;
                this.value = value;
                this.path = path;
            }
        }

        @Override
        public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
            List<StoredCookie> jar = byAuthority.get(uri.getAuthority());
            if (jar == null) {
                return Map.of();
            }
            String reqPath = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
            List<StoredCookie> matches = new ArrayList<>();
            synchronized (jar) {
                for (StoredCookie c : jar) {
                    if (pathMatches(reqPath, c.path)) {
                        matches.add(c);
                    }
                }
            }
            // RFC 6265: cookies with longer paths are sent first.
            matches.sort((a, b) -> Integer.compare(b.path.length(), a.path.length()));
            if (matches.isEmpty()) {
                return Map.of();
            }
            StringBuilder cookie = new StringBuilder();
            for (StoredCookie c : matches) {
                if (cookie.length() > 0) {
                    cookie.append("; ");
                }
                cookie.append(c.name).append('=').append(c.value);
            }
            return Map.of("Cookie", List.of(cookie.toString()));
        }

        @Override
        public void put(URI uri, Map<String, List<String>> responseHeaders) {
            List<StoredCookie> jar = byAuthority.computeIfAbsent(uri.getAuthority(),
                    a -> java.util.Collections.synchronizedList(new ArrayList<>()));
            for (Map.Entry<String, List<String>> e : responseHeaders.entrySet()) {
                if (e.getKey() == null || !e.getKey().equalsIgnoreCase("Set-Cookie")) {
                    continue;
                }
                for (String header : e.getValue()) {
                    String[] parts = header.split(";");
                    String pair = parts[0].trim();
                    int eq = pair.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String name = pair.substring(0, eq).trim();
                    String value = pair.substring(eq + 1).trim();
                    String path = "/";
                    for (int i = 1; i < parts.length; i++) {
                        String attr = parts[i].trim();
                        if (attr.regionMatches(true, 0, "Path=", 0, 5)) {
                            path = attr.substring(5).trim();
                        }
                    }
                    final String cookiePath = path.isEmpty() ? "/" : path;
                    synchronized (jar) {
                        // Replace any existing cookie with the same (name, path); a delete value evicts it.
                        jar.removeIf(c -> c.name.equals(name) && c.path.equals(cookiePath));
                        if (!value.isEmpty() && !"deleteMe".equalsIgnoreCase(value)) {
                            jar.add(new StoredCookie(name, value, cookiePath));
                        }
                    }
                }
            }
        }

        private static boolean pathMatches(String requestPath, String cookiePath) {
            if (cookiePath.equals("/") || requestPath.equals(cookiePath)) {
                return true;
            }
            if (requestPath.startsWith(cookiePath)) {
                return cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/';
            }
            return false;
        }

        boolean hasCookie(String name) {
            return byAuthority.values().stream()
                    .anyMatch(jar -> jar.stream().anyMatch(c -> c.name.equals(name)));
        }

        String summary() {
            List<String> parts = new ArrayList<>();
            byAuthority.forEach((authority, jar) -> {
                synchronized (jar) {
                    List<String> names = new ArrayList<>();
                    for (StoredCookie c : jar) {
                        names.add(c.name + "@" + c.path);
                    }
                    parts.add(authority + "{" + String.join(",", names) + "}");
                }
            });
            return String.join(" ", parts);
        }
    }
}
