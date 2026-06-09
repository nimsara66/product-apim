@cleanup
Feature: API Key Invocation
  This feature validates API-key-based invocation for a standard published API in the default v2 suite.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Create publish subscribe and invoke an API using an API key
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I retrieve the "apis" resource with id "createdApiId"
    Then The response status code should be 200
    And I put the response payload in context as "createdApiPayload"

    When I update the "apis" resource "createdApiId" and "createdApiPayload" with configuration type "securityScheme" and value:
      """
      ["api_key", "oauth_basic_auth_api_key_mandatory", "oauth2"]
      """
    Then The response status code should be 200

    When I retrieve the "apis" resource with id "createdApiId"
    Then The response status code should be 200
    And The "apis" resource should reflect the updated "securityScheme" as:
      """
      ["api_key", "oauth_basic_auth_api_key_mandatory", "oauth2"]
      """

    When I deploy the API with id "createdApiId"
    Then The response status code should be 201
    And I wait until "apis" "createdApiId" revision is deployed in the gateway
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "createAppPayload"
    And I create an application with payload "createAppPayload"
    Then The response status code should be 201

    When I put the following JSON payload in context as "apiSubscriptionPayload"
    """
    {
      "applicationId": "{{applicationId}}",
      "apiId": "{{apiId}}",
      "throttlingPolicy": "Unlimited"
    }
    """
    And I subscribe to API "createdApiId" using application "createdAppId" with payload "apiSubscriptionPayload" as "subscriptionId"
    And I retrieve the subscription for Api "createdApiId" by Application "createdAppId"
    Then The subscription with id "subscriptionId" should be in the list of all subscriptions

    When I put the following JSON payload in context as "apiKeyGenerationPayload"
    """
    {
      "keyName": "TestAPIKey",
      "validityPeriod": 3600,
      "additionalProperties": {
        "permittedIP": "",
        "permittedReferer": ""
      }
    }
    """
    And I request an api key for application id "createdAppId" using payload "apiKeyGenerationPayload"
    Then The response status code should be 200

    When I invoke the API resource at path "/apiTestContext/1.0.0/customers/123/" with method "GET" using api key "apiKey" until response status code becomes 200 within 30 seconds
    Then The response status code should be 200

    When I delete the subscription with id "subscriptionId"
    Then The response status code should be 200
    When I delete the application with id "createdAppId"
    Then The response status code should be 200
    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
