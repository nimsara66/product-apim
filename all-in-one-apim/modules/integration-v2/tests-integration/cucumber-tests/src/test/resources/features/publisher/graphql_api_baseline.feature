Feature: GraphQL API Baseline
  This feature validates GraphQL API creation and publish flow in integration-v2.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Create and publish a GraphQL API
    When I put JSON payload from file "artifacts/payloads/create_apim_test_graphql_api.json" in context as "graphQLAPIPayload"
    And I create a GraphQL API with schema file "artifacts/payloads/graphql_schema.graphql" and additional properties "graphQLAPIPayload" as "graphQLApiId"
    Then The response status code should be 201

    When I retrieve the "apis" resource with id "graphQLApiId"
    Then The response status code should be 200
    And The response should contain "GRAPHQL"
    And I put the response payload in context as "graphQLRetrievedPayload"

    When I put the following JSON payload in context as "createRevisionPayload"
    """
    {
      "description":"Initial Revision"
    }
    """
    And I make a request to create a revision for "apis" resource "graphQLApiId" with payload "createRevisionPayload"
    And I put the following JSON payload in context as "deployRevisionPayload"
    """
    [
      {
        "name": "{{gatewayEnvironment}}",
        "vhost": "localhost",
        "displayOnDevportal": true
      }
    ]
    """
    And I make a request to deploy revision "revisionId" of "apis" resource "graphQLApiId" with payload "deployRevisionPayload"
    Then The response status code should be 201
    And I wait for deployment of the resource in "graphQLRetrievedPayload"
    And I publish the "apis" resource with id "graphQLApiId"
    Then The lifecycle status of API "graphQLApiId" should be "Published"

    When I delete the "apis" resource with id "graphQLApiId"
    Then The response status code should be 200
