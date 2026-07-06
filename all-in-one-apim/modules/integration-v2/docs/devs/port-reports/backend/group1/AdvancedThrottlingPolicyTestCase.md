# Port report — `AdvancedThrottlingPolicyTestCase` (group1)

Legacy: `.../restapi/admin/throttlingpolicy/AdvancedThrottlingPolicyTestCase.java` — Factory ×2. 13 `@Test`.
Delivered: `admin/throttling_policy.feature` — `Scenario Outline: Advanced (API-level) throttling policy CRUD` (×2). **Verified 8/8.**

| # | Method | Disposition | Where / note |
|---|--------|-------------|--------------|
| 1 | testAddPolicyWithRequestCountLimit | ✅ ported | create (req-count) → 201 |
| 2 | testAddPolicyWithBandwidthLimit | ✅ ported | create (KB/min) → 201 (BANDWIDTHLIMIT) |
| 3 | testAddPolicyWithConditionalGroups | ✅ ported | create with a HEADERCONDITION conditional group → 201 |
| 4 | testGetAndUpdatePolicy | ✅ ported | retrieve + update-description |
| 6 | testDeletePolicy | ✅ ported | delete → 200 |
| 8 | testDeletePolicyWithNonExistingPolicyId | ✅ ported | delete-again → 404 |
| 5 | testDeletePolicyAlreadyExisting | ⏭️ deferred → increment 2 | delete a policy assigned to an API (needs an API assignment) |
| 7 | testAddPolicyWithExistingPolicyName | ⏭️ deferred → increment 2 | 409 duplicate-name |
| 9,10 | testChangePolicy Operation↔API Level | ⏭️ deferred → increment 2 | op-level ↔ API-level (needs an API with the policy attached) |
| 11,12,13 | testDelete…WithDifferentAdminUser (×3) | ⏭️ deferred → increment 2 | cross-admin permission (needs a 2nd admin user) |

**Net:** advanced-policy CRUD (req-count + bandwidth + conditional groups) + update + delete + 404 ported ×2. The assignment/op-level/cross-admin cases (need an API + 2nd admin) deferred to increment 2.
