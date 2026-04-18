Feature: WebSocket API Baseline
  This feature validates WebSocket API create and publish flow in integration-v2.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Create and publish a WebSocket API
    Given I have created an api from "artifacts/payloads/create_apim_test_websocket_api.json" as "websocketApiId" and deployed it
    When I publish the "apis" resource with id "websocketApiId"
    Then The lifecycle status of API "websocketApiId" should be "Published"

    When I delete the "apis" resource with id "websocketApiId"
    Then The response status code should be 200
