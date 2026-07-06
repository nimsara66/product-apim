@cleanup
Feature: MCP Server From Existing API

  MCP servers generated FROM an existing published API (ExistingApi subtype). An API is imported and deployed
  first; the MCP server then wraps selected API resources as tools, and at runtime the gateway routes each
  tools/call through that API to its backend (here the node petstore routes). Covers publisher-plane CRUD
  (create-from-API, read, narrow the exposed tools, delete), gateway invocation with value-adds (real backend
  data, a path-param tool, negative auth, error passthrough), and enforcement (scope-gated invocation +
  throttling). Both run in the super tenant and tenant1.com. Teardown via the per-scenario hook; the MCP server
  is deleted explicitly.

  # CRUD: import+deploy an API, generate an MCP server from it, read, narrow the tools, delete.
  @cap:mcp @feat:api-invocation @rule:crud @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Full CRUD lifecycle of an API-generated MCP server as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I import openapi definition from "artifacts/payloads/OAS/mcp_petstore_oas3.json" with additional properties "artifacts/payloads/mcp_petstore_api_props.json" as "backingApiId"
    Then The response status code should be 201
    When I deploy the "apis" resource with id "backingApiId"
    When I create an MCP server from api "backingApiId" exposing paths "/pets,/pets/{petId}" as "mcpId"
    Then The response status code should be 201
    And The response should contain "get_pets"
    And The response should contain "get_pets_by_petId"
    When I retrieve the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    And The response should contain "get_pets"
    And The response should contain "get_pets_by_petId"
    # UPDATE (REMOVE) — narrow the exposed tools (least-privilege).
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

  # Invocation + value-adds: the gateway routes tools/call through the underlying API to its backend.
  @cap:mcp @feat:api-invocation @rule:invocation @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: Invoke API-generated MCP tools through the gateway as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I import openapi definition from "artifacts/payloads/OAS/mcp_petstore_oas3.json" with additional properties "artifacts/payloads/mcp_petstore_api_props.json" as "backingApiId"
    Then The response status code should be 201
    When I deploy the "apis" resource with id "backingApiId"
    When I create an MCP server from api "backingApiId" exposing paths "/pets,/pets/{petId}" as "mcpId"
    Then The response status code should be 201
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I have set up application with keys, subscribed to API "mcpId" with plan "Unlimited", and obtained access token for "mcpSubId"
    Then The response status code should be 200
    # Value-add — real routing through the underlying API to its backend → real pet data.
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "max" within 90 seconds
    # Value-add — path-param tool routed to GET /pets/123 through the API.
    When I invoke the MCP tool "get_pets_by_petId" with arguments "{\"petId\":\"123\"}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting result containing "max" within 90 seconds
    # Value-add — error passthrough + negative auth.
    When I invoke the MCP tool "nosuchtool" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting an error within 90 seconds
    When I invoke the MCP server at gateway context "{{mcpContext}}" version "1.0.0" with an invalid token expecting status 403 within 60 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # Enforcement: scope-gated tool invocation on the API subtype.
  @cap:mcp @feat:api-invocation @rule:scope-enforcement @type:regression @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: A scope-gated API-generated MCP tool is enforced (200 with scope, 403 without) as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I import openapi definition from "artifacts/payloads/OAS/mcp_petstore_oas3.json" with additional properties "artifacts/payloads/mcp_petstore_api_props.json" as "backingApiId"
    Then The response status code should be 201
    When I deploy the "apis" resource with id "backingApiId"
    When I create an MCP server from api "backingApiId" exposing paths "/pets" as "mcpId"
    Then The response status code should be 201
    When I gate the MCP server "mcpId" tool "get_pets" with scope "mcpApiScopeEnf" bound to role "admin"
    Then The response status code should be 200
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "mcpApiAppPayload"
    And I create an application with payload "mcpApiAppPayload"
    Then The response status code should be 201
    When I put the following JSON payload in context as "mcpApiKeysPayload"
      """
      {"keyType": "PRODUCTION", "grantTypesToBeSupported": ["client_credentials", "password"]}
      """
    And I generate client credentials for application id "createdAppId" with payload "mcpApiKeysPayload"
    Then The response status code should be 200
    When I put the following JSON payload in context as "mcpApiSubPayload"
      """
      {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "Unlimited"}
      """
    And I subscribe to API "mcpId" using application "createdAppId" with payload "mcpApiSubPayload" as "mcpApiSubId"
    Then The response status code should be 201
    When I request an OAuth access token for the current user using password grant with scope "mcpApiScopeEnf"
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

  # Enforcement: subscription throttling on the API subtype.
  @cap:mcp @feat:api-invocation @rule:throttling @type:regression @dep:admin @dep:publisher @legacy:MCPServerTestCase
  Scenario Outline: An API-generated MCP subscription is throttled with 429 once it exceeds its limit as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    When I create a subscription throttling policy "${UNIQUE:mcpApiSub10perMin}" allowing 10 requests per minute
    Then The response status code should be 201
    When I import openapi definition from "artifacts/payloads/OAS/mcp_petstore_oas3.json" with additional properties "artifacts/payloads/mcp_petstore_api_props.json" as "backingApiId"
    Then The response status code should be 201
    When I deploy the "apis" resource with id "backingApiId"
    When I create an MCP server from api "backingApiId" exposing paths "/pets" as "mcpId"
    Then The response status code should be 201
    When I update the MCP server "mcpId" to offer policies "Unlimited,{{subThrottlePolicyName}}"
    Then The response status code should be 200
    When I deploy the "mcp-servers" resource with id "mcpId"
    When I publish the "mcp-servers" resource with id "mcpId"
    Then The response status code should be 200
    When I retrieve the "mcp-servers" resource with id "mcpId"
    And I extract response field "context" and store it as "mcpContext"
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "mcpApiThrAppPayload"
    And I create an application with payload "mcpApiThrAppPayload"
    Then The response status code should be 201
    When I put the following JSON payload in context as "mcpApiThrKeysPayload"
      """
      {"keyType": "PRODUCTION", "grantTypesToBeSupported": ["client_credentials", "password"]}
      """
    And I generate client credentials for application id "createdAppId" with payload "mcpApiThrKeysPayload"
    Then The response status code should be 200
    When I put the following JSON payload in context as "mcpApiThrSubPayload"
      """
      {"applicationId": "{{applicationId}}", "apiId": "{{apiId}}", "throttlingPolicy": "{{subThrottlePolicyName}}"}
      """
    And I subscribe to API "mcpId" using application "createdAppId" with payload "mcpApiThrSubPayload" as "mcpApiThrSubId"
    Then The response status code should be 201
    When I request an OAuth access token for the current user using password grant with scope "PRODUCTION"
    Then The response status code should be 200
    When I invoke the MCP tool "get_pets" with arguments "{}" at gateway context "{{mcpContext}}" version "1.0.0" using access token "generatedAccessToken" expecting status 429 within 90 seconds
    When I delete the MCP server "mcpId"

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |
