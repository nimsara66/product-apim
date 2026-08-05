@cleanup
Feature: Workshop 1 - Gateway REST API Invocation (single tenant)

  The happy path, end to end, in one scenario: create a REST API, deploy it, publish it, create an
  application with keys, subscribe, get an access token, and invoke through the gateway expecting 200.
  This is the automated counterpart of the manual walkthrough - the same calls, in the same order.

  Read the file-level "at cleanup" tag above: it opts this feature into the per-scenario teardown hook
  (Hooks.cleanUpCreatedResources), which deletes every resource the scenario registered, as the actor
  that created it. Without it, a second run collides on leftover resources.

  @cap:gateway @feat:rest-invocation @type:smoke @dep:publisher
  Scenario: Invoke a published REST API through the gateway as the super tenant admin
    # Readiness, never a sleep. The block's container is already booted by BlockLifecycleListener; this
    # step gates on it answering, so the scenario cannot start against a half-started server.
    Given The system is ready

    # There is no mutable "current user". This resolves the actor by reference and mints its tokens, so
    # every later step in this scenario acts as "admin". Provisioned into the block by initTenantUsers.
    And I have valid access tokens as "admin"

    # One composite step: POST /apis then deploy a revision. Open ApplicationBaseSteps/PublisherBaseSteps
    # to see the individual REST calls it wraps - the same ones done by hand in the manual walkthrough.
    # The payload's "${UNIQUE:APIMTest}" is expanded to a collision-proof name (never hardcode one).
    And I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it

    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    # Read the API back and capture its gateway context into the scenario's TestContext under "apiContext".
    # The context is generated (it came from ${UNIQUE:...}), so it must be captured, not guessed.
    When I retrieve the "apis" resource with id "createdApiId"
    And I extract response field "context" and store it as "apiContext"

    # The consumer half in one step: create application -> generate keys -> subscribe -> request a token.
    # The token lands in TestContext as "generatedAccessToken".
    When I have set up application with keys, subscribed to API "createdApiId", and obtained access token for "subscriptionId"
    Then The response status code should be 200

    # Gateway propagation is asynchronous, so the invocation RETRIES until it sees 200 (or the window
    # expires) instead of sleeping. "{{apiContext}}" is resolved from TestContext at step execution.
    When I invoke the API at gateway context "{{apiContext}}/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 60 seconds
    Then The response status code should be 200
