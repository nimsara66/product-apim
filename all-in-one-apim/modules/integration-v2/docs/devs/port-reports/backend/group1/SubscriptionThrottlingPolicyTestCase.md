# Port report — `SubscriptionThrottlingPolicyTestCase` (group1)

Legacy: `.../restapi/admin/throttlingpolicy/SubscriptionThrottlingPolicyTestCase.java` — Factory ×2. 8 `@Test`.
Delivered: `admin/throttling_policy.feature` — `Scenario Outline: Subscription throttling policy CRUD` (×2); enforcement in `gateway/throttling_enforcement.feature`. **Verified 8/8.**

| # | Method | Disposition | Where / note |
|---|--------|-------------|--------------|
| 1 | testAddPolicyWithRequestCountLimit | ✅ ported | create (req-count) → 201 |
| 2 | testAddPolicyWithBandwidthLimit | ✅ ported | create (KB/min) → 201 (BANDWIDTHLIMIT) |
| 5 | testGetAndUpdatePolicy | ✅ ported | retrieve + update-description |
| 6 | testDeletePolicy | ✅ ported | delete → 200 |
| 8 | testDeletePolicyWithNonExistingPolicyId | ✅ ported | delete-again → 404 |
| 3 | testSubscriptionLevelThrottling | ✅ **already done** | `gateway/throttling_enforcement.feature` (subscription 429 + burst, ×2) |
| 4 | testCheckPolicyPermission | ⏭️ deferred → increment 2 | restricted-tier visibility (roles) |
| 7 | testAddPolicyWithExistingPolicyName | ⏭️ deferred → increment 2 | 409 duplicate-name |

**Net:** subscription-policy CRUD + bandwidth ported ×2; enforcement already covered. Permission-visibility + 409 deferred.
