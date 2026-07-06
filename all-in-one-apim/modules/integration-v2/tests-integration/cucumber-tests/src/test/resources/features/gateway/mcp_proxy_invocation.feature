@cleanup
Feature: MCP Server Proxy

  MCP servers created by PROXYING a third-party MCP server. The proxy backend is a REAL, session-stateful MCP
  server built on the official @modelcontextprotocol SDK (node mcp-server) — unlike the legacy test, which used
  a stateless WireMock stub. Covers publisher-plane CRUD (create with a selected tool set, read, update the
  exposed tools, delete) and gateway-plane invocation (the full MCP JSON-RPC handshake initialize →
  notifications/initialized → tools/call, carrying the Mcp-Session-Id end-to-end). Both run in the super tenant
  and tenant1.com. Teardown via the per-scenario hook; the MCP server is deleted explicitly (no ResourceCleanup
  hook for MCP servers).

  # CRUD: create exposing a subset of the backend's tools, read them back, update the exposed set, delete.
  @cap:mcp @feat:proxy-invocation @rule:crud @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Full CRUD lifecycle of a proxied MCP server as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    # CREATE — expose only echo + add (of the backend's echo/add/get_pets); assert the discovered tools persist
    When I create an MCP server proxy to "http://nodebackend:3020/mcp" exposing tools "echo,add" as "mcpId"
    Then The response status code should be 201
    And The response should contain "echo"
    And The response should contain "add"
    # Least-privilege: the backend also offers get_pets, but it was NOT selected — so it must not be exposed.
    And The response should not contain "get_pets"
    # READ — retrieve returns the server with its operations (still the selected subset only)
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    And The response should contain "echo"
    And The response should contain "add"
    And The response should not contain "get_pets"
    # UPDATE (ADD) — expand the exposed set to add get_pets; the persisted operations reflect it
    When I update the MCP server "mcpId" to expose tools "echo,add,get_pets"
    Then The response status code should be 200
    And The response should contain "get_pets"
    # READ-BACK — the add persisted
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response should contain "get_pets"
    # UPDATE (REMOVE) — narrow back to echo,add; get_pets is dropped
    When I update the MCP server "mcpId" to expose tools "echo,add"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response should contain "echo"
    And The response should not contain "get_pets"
    # DELETE — removed; a subsequent retrieve 404s
    When I delete the MCP server "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 404

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Backend-endpoint management for a PROXY MCP server. A correct update PUTs the backend back in full, INCLUDING
  # its `definition` (MCP-tools JSON, not an OpenAPI spec). carbon-apimgt < 9.33.147 wrongly re-validated that
  # definition as OpenAPI on update, failing a correct PUT with 900754 "Error while parsing OpenAPI definition —
  # attribute tools is unexpected" (HTTP 400) — a product regression. It is fixed in carbon-apimgt 9.33.147, the
  # version this branch now builds, so the scenario is enabled. Do NOT work around any recurrence by stripping the
  # definition (that would hide a regression).
  @cap:mcp @feat:proxy-invocation @rule:backend-crud @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Manage the backend endpoint of a proxied MCP server as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server proxy to "http://nodebackend:3020/mcp" exposing tools "echo,add" as "mcpId"
    Then The response status code should be 201
    # LIST the server's backend endpoints and capture the (single) backend's id
    When I retrieve the backends of MCP server "mcpId" and store the first backend id as "mcpBackendId"
    Then The response status code should be 200
    # GET the backend by id and capture it for a round-trip update
    When I retrieve backend "mcpBackendId" of MCP server "mcpId"
    Then The response status code should be 200
    And I put the response payload in context as "mcpBackendPayload"
    # endpointConfig is a stringified JSON blob (escaped \/), so edit the endpoint URL at text level (its port
    # has no slashes). The definition is sent back unchanged (a correct update includes it).
    When I replace "nodebackend:3020" with "nodebackend:3021" in the payload "mcpBackendPayload"
    And I update backend "mcpBackendId" of MCP server "mcpId" with payload "mcpBackendPayload"
    Then The response status code should be 200
    When I retrieve backend "mcpBackendId" of MCP server "mcpId"
    Then The response status code should be 200
    And The response should contain "nodebackend:3021"
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Invocation: publish + subscribe + the full stateful MCP handshake through the gateway.
  @cap:mcp @feat:proxy-invocation @rule:invocation @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Invoke a tool on a proxied MCP server through the gateway as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server proxy to "http://nodebackend:3020/mcp" exposing tools "echo,add,get_pets" as "mcpId"
    Then The response status code should be 201
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I have set up application with keys, subscribed to API "mcpId" with plan "Unlimited", and obtained access token for "mcpSubId"
    Then The response status code should be 200
    # Full MCP handshake through the gateway: initialize (session) → notifications/initialized → tools/list
    # (must advertise echo) → tools/call echo — the stateful round-trip to the real SDK-backed MCP server.
    When I invoke the MCP tool "echo" with arguments "{\"message\":\"hello mcp\"}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "hello mcp" within 90 seconds
    # Value-add 1 — REAL tool execution (legacy asserted only canned echoes): args are forwarded and the real
    # result is computed/returned by the SDK server (add 2+3=5; get_pets returns actual pet data).
    When I invoke the MCP tool "add" with arguments "{\"a\":2,\"b\":3}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "5" within 90 seconds
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "max" within 90 seconds
    # Value-add 2 — multi-call SESSION CONTINUITY: one initialize, then several tools/call on the SAME
    # Mcp-Session-Id (proves the gateway persists MCP session state across calls).
    When I invoke MCP tools in one session at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" with calls "echo|{\"message\":\"multi\"}|multi ; add|{\"a\":10,\"b\":20}|30" within 90 seconds
    # Value-add 3 — JSON-RPC error passthrough: a non-exposed tool yields an MCP error through the gateway.
    When I invoke the MCP tool "nosuchtool" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting an error within 90 seconds
    # Value-add 4 — negative auth at the gateway: an invalid token is rejected (401).
    When I invoke the MCP server at gateway context "{{mcpContext}}" version "1.0.0" with an invalid token expecting status 401 within 60 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Enforcement: a scope-gated MCP tool is refused (403) without the scope and allowed (200) with it.
  @cap:mcp @feat:proxy-invocation @rule:scope-enforcement @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: A scope-gated MCP tool is enforced (200 with the scope, 403 without) as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server proxy to "http://nodebackend:3020/mcp" exposing tools "echo" as "mcpId"
    Then The response status code should be 201
    # Gate the echo tool with a scope bound to the tenant admin role.
    When I gate the MCP server "mcpId" tool "echo" with scope "mcpScopeEnf" bound to role "admin"
    Then The response status code should be 200
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    # Subscribe an app with client_credentials + password grants (password needed to mint a scoped user token).
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "mcpScopeAppPayload"
    And I create an application with payload "mcpScopeAppPayload"
    Then The response status code should be 201
    When I put the following JSON payload in context as "mcpScopeKeysPayload"
      """
      {"keyType": "PRODUCTION", "grantTypesToBeSupported": ["client_credentials", "password"]}
      """
    And I generate client credentials for application id "createdAppId" with payload "mcpScopeKeysPayload"
    Then The response status code should be 200
    When I put the following JSON payload in context as "mcpScopeSubPayload"
      """
      {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "Unlimited"}
      """
    And I subscribe to API "mcpId" using application "createdAppId" with payload "mcpScopeSubPayload" as "mcpScopeSubId"
    Then The response status code should be 201
    # A token WITH the scope calls the gated tool (200).
    When I request an OAuth access token for the current user using password grant with scope "mcpScopeEnf"
    Then The response status code should be 200
    When I invoke the MCP tool "echo" with arguments "{\"message\":\"scoped\"}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 200 within 90 seconds
    # A token WITHOUT the scope is refused at the tool call (403).
    When I request an OAuth access token for the current user using password grant with scope "openid"
    Then The response status code should be 200
    When I invoke the MCP tool "echo" with arguments "{\"message\":\"scoped\"}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 403 within 90 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Enforcement: a subscription bound to a low request-count policy is throttled (429) once it exceeds the limit.
  # Doc-advocated (auth+throttling+analytics on MCP servers) though the legacy left it disabled. Uses the robust
  # cumulative until-429 pattern within the minute window (each /mcp request counts toward the subscription quota).
  @cap:mcp @feat:proxy-invocation @rule:throttling @type:regression @dep:admin @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: A proxied MCP server subscription is throttled with 429 once it exceeds its limit as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    # A bespoke subscription policy allowing only 10 requests/min (reachable in a test; high enough that a few
    # full MCP handshakes succeed before the quota trips).
    When I create a subscription throttling policy "${UNIQUE:mcpSub10perMin}" allowing 10 requests per minute
    Then The response status code should be 201
    When I create an MCP server proxy to "http://nodebackend:3020/mcp" exposing tools "echo" as "mcpId"
    Then The response status code should be 201
    # The MCP server must OFFER the low tier for a subscription to use it.
    When I update the MCP server "mcpId" to offer policies "Unlimited,{{subThrottlePolicyName}}"
    Then The response status code should be 200
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    # An application subscribed on the LOW subscription tier, keyed (password grant for a user token).
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "mcpThrottleAppPayload"
    And I create an application with payload "mcpThrottleAppPayload"
    Then The response status code should be 201
    When I put the following JSON payload in context as "mcpThrottleKeysPayload"
      """
      {"keyType": "PRODUCTION", "grantTypesToBeSupported": ["client_credentials", "password"]}
      """
    And I generate client credentials for application id "createdAppId" with payload "mcpThrottleKeysPayload"
    Then The response status code should be 200
    When I put the following JSON payload in context as "mcpThrottleSubPayload"
      """
      {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "{{subThrottlePolicyName}}"}
      """
    And I subscribe to API "mcpId" using application "createdAppId" with payload "mcpThrottleSubPayload" as "mcpThrottleSubId"
    Then The response status code should be 201
    When I request an OAuth access token for the current user using password grant with scope "PRODUCTION"
    Then The response status code should be 200
    # Drive past the 10/min subscription limit — the gateway must eventually refuse with 429 (cumulative retry).
    When I invoke the MCP tool "echo" with arguments "{\"message\":\"t\"}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 429 within 90 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |
