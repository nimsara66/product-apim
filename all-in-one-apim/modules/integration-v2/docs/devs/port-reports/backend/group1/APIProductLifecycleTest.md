# Port report — `APIProductLifecycleTest` (group1)

Legacy source: `.../tests/apiproduct/lifecycle/APIProductLifecycleTest.java` — Factory over several user modes
(super/tenant admin + email/userstore users). 5 `@Test`.

Delivered v2: `publisher/api_products.feature` (lifecycle + delete) + `gateway/api_product_invocation.feature`
(gateway response per state). **Verified 14/14.**

## Method dispositions
| # | Method | Disposition | Where / note |
|---|--------|-------------|--------------|
| 1 | testCreateAPIProduct | ✅ ported | product create (all creation scenarios) |
| 2 | testPublishAPIProduct | ✅ ported | `api_products` lifecycle scenario (publish → PUBLISHED) + `api_product_invocation` (publish + invoke 200) |
| 3 | testChangeAPIProductLifecycleStateToBlockedState | ✅ ported | `api_product_invocation` — **Block → invoke → 503** (gateway), ×… (super) |
| 5 | testDeleteRetiredAPIProducts | ✅ ported | `api_products` lifecycle scenario — publish→deprecate→retire→**delete → 200** |
| 4 | testDeleteDeprecatedAPIProductsWithSubscription | ⏭️ **deferred → increment 2** | delete a DEPRECATED product that has an active subscription — needs the subscription setup + the specific allow/deny assertion |

**Note on lifecycle states:** the v2 lifecycle coverage also adds **DEPRECATED → still invocable (200)** at the
gateway (a legitimate runtime property the legacy didn't assert). RETIRED is handled on the **publisher plane**
(retire → deletable) rather than asserted at the gateway: unlike a retired API (404), a retired *product* fails
key validation with `900900`/500 — a quirk the legacy never asserted, so it is deliberately not enshrined
(see the gateway feature comment).

## Net
Create/publish/blocked-invoke(503)/retire-and-delete are ported (+ a deprecated-invoke-200 extension). Only the
delete-deprecated-**with-subscription** case (#4) is deferred to increment 2. The multi-userstore factory modes
(email/userstore users) collapse to the standard ×2-tenant actors — those user *variants* are a users/userstore
concern, not a product concern.
