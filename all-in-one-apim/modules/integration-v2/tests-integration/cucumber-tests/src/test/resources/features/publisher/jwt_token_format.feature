@cleanup
Feature: JWT Token Format
  This feature validates JWT token format generation for OAuth password-grant flow.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Generate a production OAuth token in JWT format
    When I put JSON payload from file "artifacts/payloads/create_apim_test_app.json" in context as "createAppPayload"
    And I create an application with payload "createAppPayload"
    Then The response status code should be 201

    When I put the following JSON payload in context as "generateApplicationKeysPayload"
    """
    {
      "keyType": "PRODUCTION",
      "grantTypesToBeSupported": [
        "client_credentials",
        "password"
      ]
    }
    """
    And I generate client credentials for application id "createdAppId" with payload "generateApplicationKeysPayload"
    Then The response status code should be 200

    When I request an OAuth access token for the current user using password grant with scope "PRODUCTION"
    Then The response status code should be 200
    And The generated access token should be in JWT format

    When I delete the application with id "createdAppId"
    Then The response status code should be 200
