@cleanup
Feature: Invalid Token Invocation
  This feature validates that gateway invocation fails with a clear 401 response for invalid bearer tokens.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Invoke a published API with an invalid token
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I put the value "abcdefgh" in context as "invalidAccessToken"
    And I invoke the API resource at path "/apiTestContext/1.0.0/customers/123/" with method "GET" using access token "invalidAccessToken" and payload ""
    Then The response status code should be 401
    And The response should contain "Make sure you have provided the correct security credentials"

    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
