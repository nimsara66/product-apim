@cleanup
Feature: DevPortal Search And Visibility
  This feature validates non-migration API search visibility in DevPortal.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Search a newly published API from DevPortal
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it
    When I publish the "apis" resource with id "createdApiId"
    Then The lifecycle status of API "createdApiId" should be "Published"

    When I search DevPortal APIs with query "name:APIMTest"
    Then The response status code should be 200
    And The response should contain "APIMTest"

    When I search DevPortal APIs with query "context:apiTestContext"
    Then The response status code should be 200
    And The response should contain "apiTestContext"

    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
