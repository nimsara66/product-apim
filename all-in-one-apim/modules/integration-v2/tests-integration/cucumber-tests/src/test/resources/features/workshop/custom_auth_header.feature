@cleanup
Feature: Workshop 3 - Gateway Custom Authorization Header

  The same arc as Workshop 1 - create, deploy, publish, subscribe, invoke - but this block boots with a
  TOML overlay that tells the gateway to read the token from "Test-Custom-Header" instead of the standard
  "Authorization" header.

  Nothing about the test framework changed. What changed is the SERVER, via two lines of TOML wired to the
  block by the tomlExtraOverlayPath parameter. Notice which steps are unaffected: create, publish and
  subscribe all still pass, because the overlay changes the gateway DATA PLANE only - the management APIs
  still authenticate with Authorization.

  To see the break first, add Workshop 1's runner to this block (see the suite XML). Its invocation now
  fails, because it presents the token in a header the gateway no longer reads. The fix below is a
  different INVOKE step, not a new one - the framework already had a variant that takes a header name.

  Both scenarios run in both tenants, using the actor outline from Workshop 2. Worth noting: the gateway
  auth header is SERVER-WIDE, not per tenant, so the same overlay governs both rows.

  @cap:gateway @feat:custom-auth-header @type:smoke @dep:publisher
  Scenario Outline: A token presented in the configured custom header is accepted as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    And I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I retrieve the "apis" resource with id "createdApiId"
    And I extract response field "context" and store it as "apiContext"

    When I have set up application with keys, subscribed to API "createdApiId", and obtained access token for "subscriptionId"
    Then The response status code should be 200

    # The ONLY line that differs from Workshop 1: the invoke variant that names the header to use.
    When I invoke the API at gateway context "{{apiContext}}/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" in header "Authorization" and payload "" until response status code becomes 200 within 60 seconds
    Then The response status code should be 200

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |

  # The negative half. Assert the EXACT status the product returns (401), never a relaxed "401 or 403" -
  # a permissive assertion would still pass if a future regression changed the rejection code, including
  # one that let the request through some other way.
  @cap:gateway @feat:custom-auth-header @type:negative @dep:publisher
  Scenario Outline: The standard Authorization header is rejected when a custom auth header is configured as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    And I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I retrieve the "apis" resource with id "createdApiId"
    And I extract response field "context" and store it as "apiContext"

    When I have set up application with keys, subscribed to API "createdApiId", and obtained access token for "subscriptionId"
    Then The response status code should be 200

    # Same valid token, standard header - the gateway no longer reads it, so the call is rejected 401.
    When I invoke the API at gateway context "{{apiContext}}/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 401 within 60 seconds
    Then The response status code should be 401

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |
