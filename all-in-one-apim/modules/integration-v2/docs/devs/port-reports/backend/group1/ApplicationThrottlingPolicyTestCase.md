# Port report — `ApplicationThrottlingPolicyTestCase` (group1)

Legacy: `.../restapi/admin/throttlingpolicy/ApplicationThrottlingPolicyTestCase.java` — Factory ×2 (super+tenant). 6 `@Test`.
Delivered: `admin/throttling_policy.feature` — `Scenario Outline: Application throttling policy CRUD` (×2). **Verified 8/8.**

| # | Method | Disposition | Where / note |
|---|--------|-------------|--------------|
| 1 | testAddPolicyWithRequestCountLimit | ✅ ported | create (req-count) → 201 (contains REQUESTCOUNTLIMIT) |
| 2 | testAddPolicyWithBandwidthLimit | ✅ ported | create (KB/min) → 201 (contains BANDWIDTHLIMIT) |
| 3 | testGetAndUpdatePolicy | ✅ ported | retrieve → 200; update description (GET→set→PUT) → 200 |
| 4 | testDeletePolicy | ✅ ported | delete → 200 |
| 6 | testDeletePolicyWithNonExistingPolicyId | ✅ ported | delete-again (same id) → 404 |
| 5 | testAddPolicyWithExistingPolicyName | ⏭️ **deferred → increment 2** | 409 duplicate-name (needs a per-type duplicate-create) |

**Net:** application-policy CRUD (request-count + bandwidth) + update + delete + 404 ported ×2. Only the 409 duplicate-name negative deferred.
