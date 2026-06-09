@cleanup
Feature: Sandbox Token
  This feature validates sandbox-key token generation and invocation against APIs with sandbox endpoints.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Invoke a published API using a sandbox-scoped token
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "createAppPayload"
    And I create an application with payload "createAppPayload"
    Then The response status code should be 201

    When I put the following JSON payload in context as "generateSandboxApplicationKeysPayload"
    """
    {
      "keyType": "SANDBOX",
      "grantTypesToBeSupported": [
        "client_credentials",
        "password",
        "refresh_token"
      ]
    }
    """
    And I generate client credentials for application id "createdAppId" with payload "generateSandboxApplicationKeysPayload"
    Then The response status code should be 200

    When I put the following JSON payload in context as "apiSubscriptionPayload"
    """
    {
      "applicationId": "{{applicationId}}",
      "apiId": "{{apiId}}",
      "throttlingPolicy": "Unlimited"
    }
    """
    And I subscribe to API "createdApiId" using application "createdAppId" with payload "apiSubscriptionPayload" as "subscriptionId"
    Then The response status code should be 201

    When I request an OAuth access token for the current user using password grant with scope "SANDBOX"
    Then The response status code should be 200
    And I invoke the API resource at path "/apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 30 seconds
    Then The response status code should be 200

    When I delete the subscription with id "subscriptionId"
    Then The response status code should be 200
    When I delete the application with id "createdAppId"
    Then The response status code should be 200
    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
