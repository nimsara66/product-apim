@cap:admin @feat:external-key-manager @rule:multitenant-sso @type:negative
Feature: Multi-Tenant Portal SSO via External Identity Server

  Verifies that a tenant user can complete the API Manager Publisher login end-to-end through the nested-OIDC
  multi-tenant broker to an external WSO2 IS 7.x, headlessly (no browser). The broker chain is: the publisher
  portal SP federates to the super tenant multiTenantAuthenticator IdP (which renders the tenant-selection page),
  which brokers to the API Manager tenant's commonsp via its own OAuth2 client, which federates over OIDC to the
  IS tenant application where the user actually authenticates; the callback chain then returns an authenticated
  publisher session. The topology is provisioned by _setup_multitenant_sso in the same runner.

  REGRESSION PIN (asserts the CURRENT broken behaviour, deliberately). The official guide sets this flow up with
  WSO2 IS7 connected as a key manager / tenant synchronisation (the guide's own words: "connect WSO2 Identity
  Server 7.x (or WSO2 Identity Server as a Keymanager)"; "you need to enable tenant synchronization"; "import the
  Keymanager certificate"), so registering the IS7 key manager is faithful to the document - here via
  initExternalKeyManager, which also boots IS and augments the truststore. That key-manager registration pollutes
  API Manager's JVM-global SSL context with a client key manager, so the innermost broker leg's OIDC token call
  to IS presents BOTH a client TLS certificate and the client secret, IS rejects it ("The client MUST NOT use
  more than one authentication method"), and the login fails with login_required at the publisher callback and
  cascades to logout. This scenario pins that exact failure (the documented multi-tenant SSO token-exchange
  regression). (An unfaithful shortcut - a clean API Manager with no key manager - completes the same login
  successfully, which isolates the key-manager SSL-context pollution as the trigger; but omitting the key manager
  drops a documented step.) The test will go RED - correctly signalling the fix - once API Manager stops leaking
  the client key manager into the global SSL context; at that point flip it to assert the success path. Tagged
  framework so it is excluded from the product tree; runs in its own block/suite because it needs the
  multi-tenant tenant_context and select-tenant configuration.

  Scenario: The multi-tenant broker login fails on the token-exchange double client-auth regression
    When user "ssouser" completes the multi-tenant publisher login selecting tenant "abc.com" with password "Test@12345"
    Then the multi-tenant publisher login is rejected with the token-exchange double client-auth regression
