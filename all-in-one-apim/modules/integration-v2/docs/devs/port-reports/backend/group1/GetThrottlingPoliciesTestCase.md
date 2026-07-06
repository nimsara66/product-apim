# Port report — `GetThrottlingPoliciesTestCase` (group1)

Legacy: `.../restapi/admin/throttlingpolicy/GetThrottlingPoliciesTestCase.java` — Factory ×2. 1 `@Test`.
Delivered: `admin/throttling_policy.feature` — `Scenario: List throttling policies and confirm the built-in defaults are present`. **Verified 8/8.**

| # | Method | Disposition | Where |
|---|--------|-------------|-------|
| 1 | testThrottlePoliciesGet | ✅ ported | retrieve all subscription policies → 200, contains the built-in `Unlimited` tier |

**Net:** the policy-list + default-tier-present check is ported.
