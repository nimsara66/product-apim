@cleanup
Feature: Governance Policy Baseline
  This feature validates baseline governance policy lifecycle operations.

  Background:
    Given The system is ready and I have valid access tokens for current user

  Scenario: Create and remove common and API-specific policies
    Given I have created an api from "artifacts/payloads/create_apim_test_api.json" as "createdApiId" and deployed it

    When I create a new common policy with spec "artifacts/payloads/policySpecFiles/custom_add_common_header.j2" and "artifacts/payloads/policySpecFiles/custom_add_common_header.yaml" as "commonPolicyId"
    Then The response status code should be 201

    When I create a new API specific policy for api "createdApiId" with spec "artifacts/payloads/policySpecFiles/custom_add_api_specific_header.j2" and "artifacts/payloads/policySpecFiles/custom_add_api_specific_header.yaml" as "apiSpecificPolicyId"
    Then The response status code should be 201

    When I delete the api "createdApiId" specific policy "apiSpecificPolicyId"
    Then The response status code should be 200

    When I delete the "operation-policies" resource with id "commonPolicyId"
    Then The response status code should be 200

    When I delete the "apis" resource with id "createdApiId"
    Then The response status code should be 200
