@setup
Feature: Setup - Multi-tenant portal SSO topology (external WSO2 IS 7.x federated IdP)

  Provisions the nested-OIDC broker described in the official multi-tenant SSO guide so the login scenario can
  drive it: the tenant is created in the IDENTITY SERVER and synchronized to API Manager via the tenant-sharing
  feature (the block's IS runs the TenantSyncListener - see is7sso/is-tenant-sync-overlay.toml - exactly the
  tenant-synchronization path the SSO guide prescribes), then the IS OIDC application (+ user and groups), the
  API Manager tenant OIDC IdP and its common service provider, the super tenant multi-tenant broker IdP, and the
  publisher portal SP wired to that broker. Asserts only that each provisioning call succeeds (the identity artifacts persist for the
  runner's later scenario); torn down per-runner by the AfterClass sweep, not per-scenario. Listed first by the
  _setup_ prefix so it runs before the login scenario. Everything targets the block-booted, host-mapped APIM and
  IS containers; the identity artifacts carry the containers' advertised in-network hosts so APIM's own
  server-to-server legs resolve.

  Scenario: Provision the multi-tenant SSO broker topology
    Given The system is ready
    And I have valid access tokens as "admin"
    # Register IS as a third-party key manager (admin capability, feature-level - not block infra). This is a
    # true prerequisite of the login scenario: the KM registration is what pollutes APIM's JVM SSL context and
    # triggers the pinned token-exchange double-client-auth regression the login scenario asserts.
    When I create a key manager from payload "artifacts/payloads/keymanagers/wso2is7.json" as "ssoKm"
    Then The response status code should be 201
    When I provision the SSO tenant "abc.com" on the identity server and await its sync to API Manager with admin password "Admin@12345"
    And I provision the SSO identity server application in tenant "abc.com"
    And I provision the SSO user "ssouser" with password "Test@12345" in groups "publisher,devportal" in tenant "abc.com"
    And I configure the SSO tenant OIDC identity provider "WSO2IS7_OIDC" in tenant "abc.com"
    And I configure the SSO common service provider "commonsp" federating to "WSO2IS7_OIDC" in tenant "abc.com"
    And I configure the SSO super tenant multi-tenant identity provider "WSO2IS7_MT" using common service provider "commonsp"
    And I remember the SSO multi-tenant identity provider name "WSO2IS7_MT"
    And I wire the publisher portal service provider to multi-tenant identity provider "WSO2IS7_MT"
