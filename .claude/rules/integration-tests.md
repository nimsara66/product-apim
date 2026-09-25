# Integration Test Framework Policy

This rule applies to product integration tests in the legacy integration-test modules and the integration-v2
Cucumber framework.

- Write new product integration scenarios for patches and features in `all-in-one-apim/modules/integration-v2/`.
  Do not add new product integration scenarios, test methods, or test classes to the legacy
  `all-in-one-apim/modules/integration/tests-integration/` suites.
- Editing an existing legacy test is allowed only to fix that existing test when it has a demonstrated CI
  failure. Identify the failed workflow/job and test in the PR context. The exception permits correcting that
  test; it does not permit adding another legacy scenario or test method.
- Add or update the corresponding integration-v2 coverage for the behavior fixed or added. If an existing V2
  scenario already asserts the exact behavior, cite its feature and scenario instead of duplicating it.
- Register every affected product runner in both
  `all-in-one-apim/modules/integration-v2/tests-integration/cucumber-tests/src/test/resources/testng-v2.xml`
  and `testng-v2_distributed.xml`, preserving each topology's listener and block-specific configuration.
- Before PR submission, run focused suites for the affected runner(s) in both all-in-one and distributed
  topologies. Verify from reports that the intended scenarios ran and record each topology's result in the PR.
  A passing run in only one topology is incomplete verification.

Do not apply this policy to unit tests, non-product framework-verification tests, or unrelated legacy test
maintenance. For an inherently topology-specific framework test, explain the scope and verify its applicable
topology.
