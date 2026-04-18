Feature: Subscription Throttling Policy
  This feature validates subscription plan updates for a subscribed API.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Update a subscription plan from Unlimited to Gold
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I have set up application with keys, subscribed to API "createdApiId", and obtained access token for "subscriptionId"
    Then The response status code should be 200

    When I put the following JSON payload in context as "subscriptionPayload"
    """
    {
      "applicationId": "{{applicationId}}",
      "apiId": "{{apiId}}",
      "throttlingPolicy": "Unlimited"
    }
    """
    And I update the subscription "subscriptionId" with subscription plan "Gold"
    Then The response status code should be 200

    When I get the subscription with id "subscriptionId"
    Then The response status code should be 200
    And The response should contain "Gold"

    When I delete the subscription with id "subscriptionId"
    Then The response status code should be 200
    When I delete the application with id "createdAppId"
    Then The response status code should be 200
    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200