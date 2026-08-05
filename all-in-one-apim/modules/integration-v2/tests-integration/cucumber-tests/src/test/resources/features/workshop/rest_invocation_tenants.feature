@cleanup
Feature: Workshop 2 - Gateway REST API Invocation (both tenants)

  The same happy path as Workshop 1, now run in BOTH the super tenant and tenant1.com. Note what did
  NOT change: not one step. Only the actor reference became a Scenario Outline column, and the actor's
  domain drives tenant routing for every call underneath.

  Two things to point out while this runs:
  1. The context captured below already carries the "/t/tenant1.com" prefix for a tenant API, which is
     why the invocation uses the "at gateway context" variant that takes the context verbatim. Passing
     a tenant path through the tenant-prefixing "at path" variant instead yields a doubled
     "/t/tenant1.com/t/tenant1.com/..." and a 404.
  2. An outline leaves the acting actor set to its LAST Examples row. Any scenario that follows must
     open with its own actor step rather than assuming the actor carried over.

  @cap:gateway @feat:rest-invocation @type:smoke @dep:publisher
  Scenario Outline: Invoke a published REST API through the gateway as <actor>
    Given The system is ready
    And I have valid access tokens as "<actor>"
    And I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it

    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    # Already carries /t/<tenant> for a tenant API - capture it rather than building the path by hand.
    When I retrieve the "apis" resource with id "createdApiId"
    And I extract response field "context" and store it as "apiContext"

    When I have set up application with keys, subscribed to API "createdApiId", and obtained access token for "subscriptionId"
    Then The response status code should be 200

    When I invoke the API at gateway context "{{apiContext}}/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 60 seconds
    Then The response status code should be 200

    Examples:
      | actor             |
      | admin             |
      | admin@tenant1.com |
