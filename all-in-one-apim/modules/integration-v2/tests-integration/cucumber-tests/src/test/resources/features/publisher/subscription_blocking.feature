@cleanup
Feature: Subscription Blocking
  This feature validates that blocking and unblocking a subscription changes runtime invocation behavior.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Block and unblock an API subscription
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I have set up application with keys, subscribed to API "createdApiId", and obtained access token for "subscriptionId"
    Then The response status code should be 200

    When I invoke the API resource at path "/apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload ""
    Then The response status code should be 200

    When I block the subscription with "subscriptionId" for the resource
    Then The response status code should be 200
    When I invoke the API resource at path "/apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 401 within 10 seconds
    Then The response status code should be 401

    When I unblock the subscription with "subscriptionId" for the resource
    Then The response status code should be 200
    When I invoke the API resource at path "/apiTestContext/1.0.0/customers/123/" with method "GET" using access token "generatedAccessToken" and payload "" until response status code becomes 200 within 10 seconds
    Then The response status code should be 200

    When I delete the subscription with id "subscriptionId"
    Then The response status code should be 200
    When I delete the application with id "createdAppId"
    Then The response status code should be 200
    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
