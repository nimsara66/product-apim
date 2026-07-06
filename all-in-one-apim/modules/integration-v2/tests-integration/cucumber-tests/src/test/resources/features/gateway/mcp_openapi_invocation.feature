@cleanup
Feature: MCP Server From OpenAPI

  MCP servers generated FROM an OpenAPI definition (DirectBackend subtype). The gateway generates a TOOL per OAS
  operation and, at runtime, translates each tools/call into an HTTP request to the configured REST backend
  (MCP↔HTTP) — here the node petstore routes. Covers publisher-plane CRUD (create from OAS, read, narrow the
  exposed tools, delete), gateway invocation with value-adds (real backend data, a path-param tool, negative
  auth, error passthrough), and enforcement (scope-gated invocation + throttling). Both run in the super tenant
  and tenant1.com. Teardown via the per-scenario hook; the MCP server is deleted explicitly.

  # CRUD: create from OAS (both tools generated), read, narrow to a subset (remove a tool), delete.
  @cap:mcp @feat:openapi-invocation @rule:crud @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Full CRUD lifecycle of an OpenAPI-generated MCP server as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server from openapi "artifacts/payloads/OAS/mcp_petstore_oas3.json" with backend "http://nodebackend:3001/jaxrs_basic/services/customers/customerservice" as "mcpId"
    Then The response status code should be 201
    And The response should contain "get_pets"
    And The response should contain "get_pets_by_petId"
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    And The response should contain "get_pets"
    And The response should contain "get_pets_by_petId"
    # UPDATE (REMOVE) — narrow the exposed tools to just get_pets (docs "select tools to import" / least-privilege).
    When I update the MCP server "mcpId" removing tool "get_pets_by_petId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response should contain "get_pets"
    And The response should not contain "get_pets_by_petId"
    # UPDATE (ADD) — re-add the removed tool (inverse of remove); it comes back
    When I re-add the removed tool to the MCP server "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response should contain "get_pets_by_petId"
    When I delete the MCP server "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 404

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Backend-endpoint management: an OpenAPI-generated MCP server has its OWN backend (the REST endpoint the
  # generated tools call). List it, get it by id, update its URL, and read the update back. (list/get/update
  # only — the backend is created implicitly with the server and has no separate add/delete.)
  @cap:mcp @feat:openapi-invocation @rule:backend-crud @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Manage the backend endpoint of an OpenAPI-generated MCP server as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server from openapi "artifacts/payloads/OAS/mcp_petstore_oas3.json" with backend "http://nodebackend:3001/jaxrs_basic/services/customers/customerservice" as "mcpId"
    Then The response status code should be 201
    When I retrieve the backends of MCP server "mcpId" and store the first backend id as "mcpBackendId"
    Then The response status code should be 200
    When I retrieve backend "mcpBackendId" of MCP server "mcpId"
    Then The response status code should be 200
    And I put the response payload in context as "mcpBackendPayload"
    # A correct update sends the backend back in full (INCLUDING its definition — an OpenAPI spec here, which the
    # server validates cleanly). endpointConfig is a stringified JSON blob (escaped \/), so edit the endpoint URL
    # at text level using a slash-free segment ("customerservice") to avoid the blob's escaped slashes.
    When I replace "customerservice" with "customerservice_updated" in the payload "mcpBackendPayload"
    And I update backend "mcpBackendId" of MCP server "mcpId" with payload "mcpBackendPayload"
    Then The response status code should be 200
    When I retrieve backend "mcpBackendId" of MCP server "mcpId"
    Then The response status code should be 200
    And The response should contain "customerservice_updated"
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Invocation + value-adds: the gateway translates tools/call → HTTP to the REST backend and returns real data.
  @cap:mcp @feat:openapi-invocation @rule:invocation @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Invoke OpenAPI-generated MCP tools through the gateway (MCP to HTTP) as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server from openapi "artifacts/payloads/OAS/mcp_petstore_oas3.json" with backend "http://nodebackend:3001/jaxrs_basic/services/customers/customerservice" as "mcpId"
    Then The response status code should be 201
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I have set up application with keys, subscribed to API "mcpId" with plan "Unlimited", and obtained access token for "mcpSubId"
    Then The response status code should be 200
    # Value-add — real MCP↔HTTP: tools/call get_pets → gateway calls the REST backend → returns real pet data.
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "max" within 90 seconds
    # Value-add — path-param tool: get_pets_by_petId {petId:123} → gateway maps to GET /pets/123 on the backend.
    When I invoke the MCP tool "get_pets_by_petId" with arguments "{\"petId\":\"123\"}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "max" within 90 seconds
    # Value-add — error passthrough + negative auth.
    When I invoke the MCP tool "nosuchtool" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting an error within 90 seconds
    # The DirectBackend (OpenAPI) subtype rejects an invalid token at tools/call with 403 (the proxy subtype
    # returns 401 — a verified per-subtype difference); asserted strictly so a future code change is caught.
    When I invoke the MCP server at gateway context "{{mcpContext}}" version "1.0.0" with an invalid token expecting status 403 within 60 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Enforcement: scope-gated tool invocation on the OpenAPI subtype (legacy tested this only for proxy/existing-api).
  @cap:mcp @feat:openapi-invocation @rule:scope-enforcement @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: A scope-gated OpenAPI-generated MCP tool is enforced (200 with scope, 403 without) as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create an MCP server from openapi "artifacts/payloads/OAS/mcp_petstore_oas3.json" with backend "http://nodebackend:3001/jaxrs_basic/services/customers/customerservice" as "mcpId"
    Then The response status code should be 201
    When I gate the MCP server "mcpId" tool "get_pets" with scope "mcpOasScopeEnf" bound to role "admin"
    Then The response status code should be 200
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "mcpOasAppPayload"
    And I create an application with payload "mcpOasAppPayload"
    Then The response status code should be 201
    When I put the following JSON payload in context as "mcpOasKeysPayload"
      """
      {"keyType": "PRODUCTION", "grantTypesToBeSupported": ["client_credentials", "password"]}
      """
    And I generate client credentials for application id "createdAppId" with payload "mcpOasKeysPayload"
    Then The response status code should be 200
    When I put the following JSON payload in context as "mcpOasSubPayload"
      """
      {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "Unlimited"}
      """
    And I subscribe to API "mcpId" using application "createdAppId" with payload "mcpOasSubPayload" as "mcpOasSubId"
    Then The response status code should be 201
    When I request an OAuth access token for the current user using password grant with scope "mcpOasScopeEnf"
    Then The response status code should be 200
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 200 within 90 seconds
    When I request an OAuth access token for the current user using password grant with scope "openid"
    Then The response status code should be 200
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 403 within 90 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Enforcement: subscription throttling on the OpenAPI subtype.
  @cap:mcp @feat:openapi-invocation @rule:throttling @type:regression @dep:admin @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: An OpenAPI-generated MCP subscription is throttled with 429 once it exceeds its limit as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create a subscription throttling policy "${UNIQUE:mcpOasSub10perMin}" allowing 10 requests per minute
    Then The response status code should be 201
    When I create an MCP server from openapi "artifacts/payloads/OAS/mcp_petstore_oas3.json" with backend "http://nodebackend:3001/jaxrs_basic/services/customers/customerservice" as "mcpId"
    Then The response status code should be 201
    When I update the MCP server "mcpId" to offer policies "Unlimited,{{subThrottlePolicyName}}"
    Then The response status code should be 200
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "mcpOasThrAppPayload"
    And I create an application with payload "mcpOasThrAppPayload"
    Then The response status code should be 201
    When I put the following JSON payload in context as "mcpOasThrKeysPayload"
      """
      {"keyType": "PRODUCTION", "grantTypesToBeSupported": ["client_credentials", "password"]}
      """
    And I generate client credentials for application id "createdAppId" with payload "mcpOasThrKeysPayload"
    Then The response status code should be 200
    When I put the following JSON payload in context as "mcpOasThrSubPayload"
      """
      {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "{{subThrottlePolicyName}}"}
      """
    And I subscribe to API "mcpId" using application "createdAppId" with payload "mcpOasThrSubPayload" as "mcpOasThrSubId"
    Then The response status code should be 201
    When I request an OAuth access token for the current user using password grant with scope "PRODUCTION"
    Then The response status code should be 200
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 429 within 90 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |
