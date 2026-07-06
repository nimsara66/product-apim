# Port report — `CustomThrottlingPolicyTestCase` (group1)

Legacy: `.../restapi/admin/throttlingpolicy/CustomThrottlingPolicyTestCase.java` — Factory ×2 (but custom rules are admin-global: tenant → 403). 6 `@Test`.
Delivered: `admin/throttling_policy.feature` — `Scenario: Custom (Siddhi) throttling rule CRUD` (super only). **Verified 8/8.**

| # | Method | Disposition | Where / note |
|---|--------|-------------|--------------|
| 1 | testAddPolicy | ✅ ported | create → 201 (contains siddhiQuery) |
| 2 | testGetPolicy | ✅ ported | retrieve → 200 |
| 3 | testUpdatePolicy | ✅ ported | update-description → 200 |
| 5 | testDeletePolicy | ✅ ported | delete → 200 |
| 6 | testDeletePolicyWithNonExistentPolicyId | ✅ ported | delete-again → 404 |
| 4 | testAddPolicyWithExistingPolicyName | ⏭️ deferred → increment 2 | 409 duplicate-name |

**Note:** the legacy asserts 403 for a TENANT admin on every custom operation — v2 reflects this by running the custom scenario **super only** (documented in the feature + `custom-throttling-policy-restart-port.md`). **Net:** custom-rule CRUD ported (super); 409 deferred.
