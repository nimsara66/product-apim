# Port report — `APIM520UpdateAnAPIThroughThePublisherRestAPITestCase` (group1)

Legacy source: `modules/integration/tests-integration/tests-backend/src/test/java/org/wso2/am/integration/tests/publisher/APIM520UpdateAnAPIThroughThePublisherRestAPITestCase.java`
Factory modes: `SUPER_TENANT_ADMIN`, `TENANT_ADMIN` (×2 tenant). Restart: no.

## 1. What the legacy test does
| # | Method | Flow / assertion |
|---|--------|------------------|
| 1 | `testUpdateAnAPIThroughThePublisherRest` | create API → GET → `updateAPI` (change fields) → GET → assert the update was persisted |
| 2 | `testUpdateAnAPIThroughThePublisherRestAfterRename` | create API → GET → `updateAPI` after attempting to change the name → GET → assert (update applies; name is not renamed) |

Concern: **publisher-plane API update**, including the invariant that an update does not rename the API.

## 2. v2 coverage
`publisher/api_lifecycle.feature` — `Scenario Outline: Create, update, publish and list a REST API as <actor>`
(`@legacy:APIMANAGERPublisherTestCase`, ×2 tenant):
- updates description + tier collection and re-retrieves to confirm (⇒ method 1);
- **"Updating must not rename the API"** — PUTs a rename payload and asserts the name is unchanged (⇒ method 2).

Both methods' assertions are reproduced, and v2 runs it ×2 tenant as the legacy did.

## 3. Disposition — ✅ COVERED (no port)
Fully redundant with `api_lifecycle.feature`. No residual delta. Add `@legacy:APIM520UpdateAnAPIThroughThePublisherRestAPITestCase`
to that scenario's tags when convenient (parity bookkeeping) — optional, no behaviour change.

## 4. Coverage summary
| Legacy behaviour | Covered in v2? | Where |
|---|---|---|
| Update an API and persist changes | ✅ | `api_lifecycle.feature` (update description + tiers) |
| Update-does-not-rename invariant | ✅ | `api_lifecycle.feature` ("Updating must not rename the API") |
| ×2 tenant | ✅ | `api_lifecycle` Scenario Outline (super + tenant1) |
