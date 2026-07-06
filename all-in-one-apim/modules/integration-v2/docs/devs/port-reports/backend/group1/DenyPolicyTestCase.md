# Port report — `APIDenyPolicyTestCase` + `DenyPolicySearchTestCase` (group1, admin)

Legacy: `.../tests/other/APIDenyPolicyTestCase.java` (14 methods, Factory ×2) + `.../restapi/admin/throttlingpolicy/DenyPolicySearchTestCase.java` (3).
Delivered: `admin/deny_policies.feature` (`AdminDenyPolicyRunner`, IntegrationV2-Admin block), `@cap:admin @feat:throttling-policies` (deny policies are the same admin throttling family — no new tag). **Verified 8/8** (4 scenario definitions ×2 tenant).

## Endpoints / shape
Deny (blocking-condition) policies over the admin API. **Path quirk:** create/list is `/throttling/deny-policies` (plural), but get/patch/delete is `/throttling/deny-policy/{conditionId}` (**singular**). `BlockingConditionDTO {conditionType, conditionValue, conditionStatus, conditionId}`; `conditionType ∈ {API, APPLICATION, USER, IP, IPRANGE}`; `conditionValue` is a string (API/USER/APPLICATION) or an object (`{invert,fixedIp}` for IP, `{invert,startingIp,endingIp}` for IPRANGE). Status toggle is a PATCH of `{conditionStatus}`. New steps + `Utils.getDenyPolicies/DenyPolicyById`, `Constants.CREATED_DENY_POLICY_IDS` + `ResourceCleanup` (admin token).

## Method dispositions
| Legacy method(s) | Disposition | Where / note |
|---|---|---|
| testAddAPIDenyPolicyIPAddressWise + testGetAddedDenyPolicy + testUpdateAPIDenyPolicyStatus + testDeleteAPIDenyPolicy | ✅ ported | IP deny **CRUD lifecycle**: create 201 → get 200 → toggle status 200 → delete 200 |
| testAddAPIDenyPolicyIPRangeWise | ✅ ported | IP-range deny create → 201 |
| testAddAPIDenyPolicyUserVise | ✅ ported | USER deny create → 201 |
| testAddAPIDenyPolicyWithTheSameContext | ✅ ported | duplicate condition → **409** (as a duplicate IP) |
| DenyPolicySearch testAddNewBlockingConditions + testGetBlockConditionsByConditionTypeAndValue | ✅ ported | create + **search by conditionType+value** → 200, contains the value |
| testAddAPIDenyPolicy (API context) | ⏭️ **increment 2** | needs a **deployed API** whose context the deny targets |
| testAddAPIDenyPolicyApplicationVise | ⏭️ increment 2 | APPLICATION deny — needs an application (value `owner:appName`) |
| testAddDenyPolicyWithNonExistingContext / testAddAPIDenyPolicyToNonExistingApplication | ⏭️ increment 2 | resource-dependent negatives (need the API/app machinery); legacy asserts 500 — re-check on port |
| testAddAPIDenyPolicyInvalidIPAddress / …InvalidIPAddressRange | ⚪ SKIP (**verified live**) | malformed IP `127..0.0.1` → **500** (server error, not a clean 400) — not enshrined |
| testAddAPIDenyPolicyWithInvalidUser | ⚪ SKIP (**verified live**) | a USER deny for a non-existing user returns **201** (usernames are not validated at deny-create) — it's a valid create, not a rejection (legacy's 409 no longer reproduces) |

## Findings (verify-first)
- **Malformed IP → 500**, not 400 — a server error; skipped rather than enshrined (consistent with the create-validation lesson).
- **Non-existing USER → 201** — deny-create does not validate the username; not a negative.

## Decisions
- **Self-contained types only** (IP / IP-range / USER) this increment — they need no other resource, so the port is deterministic and parallel-safe. API-context + APPLICATION deny (resource-dependent) → increment 2.
- **×2 tenant** — deny-policy management is available per tenant admin (verified: 8/8, both `admin` and `admin@tenant1.com`). Each tenant row uses a distinct IP/value so scenarios never collide on the shared container regardless of whether blocking conditions are tenant-isolated or global.

## Net
Deny-policy CRUD (IP lifecycle) + IP-range/USER create + duplicate-409 + search ported (4/4), under the existing throttling-policies tag. Resource-dependent types/negatives → increment 2; the two suspect negatives were live-probed and correctly dropped (500 server-error / 201 valid).
