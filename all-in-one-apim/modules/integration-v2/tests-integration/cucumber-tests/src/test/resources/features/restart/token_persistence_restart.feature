Feature: Token Persistence Across Restart
  This feature validates that issued tokens keep their expected state across an APIM restart when token persistence is enabled.

  Background:
    Given I have initialized the NodeApp server container
    And I have initialized the API Manager container with label "token-persistence" and deployment toml changes file path at "src/test/resources/artifacts/configFiles/token-persistence"
    And I wait for the APIM server to be ready
    And I add super tenant to context
    And I use tenant domain "carbon.super" with user key "admin"
    And The system is ready and I have valid access tokens for current user

  Scenario: Valid and revoked tokens preserve state across restart
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "createAppPayload"
    And I create an application with payload "createAppPayload"
    Then The response status code should be 201

    When I put the following JSON payload in context as "generateApplicationKeysPayload"
    """
    {
      "keyType": "PRODUCTION",
      "grantTypesToBeSupported": [
        "client_credentials",
        "password",
        "refresh_token"
      ]
    }
    """
    And I generate client credentials for application id "createdAppId" with payload "generateApplicationKeysPayload"
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

    When I request an OAuth access token for the current user using password grant with scope "PRODUCTION"
    Then The response status code should be 200
    When I invoke the API resource at path "apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 30 seconds
    Then The response status code should be 200

    When I restart the API Manager container
    And I wait for the APIM server to be ready
    And I wait for deployment of the resource in "<createApiPayload>"
    And I invoke the API resource at path "apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 30 seconds
    Then The response status code should be 200

    When I revoke the OAuth access token "generatedAccessToken"
    Then The response status code should be 200
    When I invoke the API resource at path "apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 401 within 20 seconds
    Then The response status code should be 401

    When I restart the API Manager container
    And I wait for the APIM server to be ready
    And I wait for deployment of the resource in "<createApiPayload>"
    And I invoke the API resource at path "apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 401 within 30 seconds
    Then The response status code should be 401

    Given The system is ready and I have valid access tokens for current user
    When I delete the subscription with id "subscriptionId"
    Then The response status code should be 200
    When I delete the application with id "createdAppId"
    Then The response status code should be 200
    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
    And I stop the API Manager container
