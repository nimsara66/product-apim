# Port report — `APIM534GetAllTheAPIsCreatedThroughThePublisherRestAPITestCase` (group1)

Legacy source: `modules/integration/tests-integration/tests-backend/src/test/java/org/wso2/am/integration/tests/publisher/APIM534GetAllTheAPIsCreatedThroughThePublisherRestAPITestCase.java`
Factory modes: `SUPER_TENANT_ADMIN`, `TENANT_ADMIN` (×2 tenant). Restart: no.

## 1. What the legacy test does
| # | Method | Flow / assertion |
|---|--------|------------------|
| 1 | `testGetAllTheAPICreatedThroughThePublisherRestAPI` (dataProvider) | create API(s) → GET by id → `getAllAPIs()` → assert the created API is present in the list |
| 2 | `testCheckIfAnAPIExistsThroughThePublisherRestAPI` | `addAPI` then validate the API's existence via the publisher API |

Concern: **list all publisher APIs** and confirm a created API appears; plus an **existence check**.

## 2. v2 coverage
`publisher/api_lifecycle.feature` — `Scenario Outline: Create, update, publish and list a REST API`
(`@legacy:APIMANAGERPublisherTestCase`, ×2 tenant): after publish it does
`I retrieve all APIs created through the Publisher REST API` then `The API with id "createdApiId" should be in
the list of all APIS` (⇒ method 1, the get-all + presence assertion).

## 3. Disposition — ✅ COVERED (no port) · one minor residual noted
- **Method 1 (get-all + presence): fully covered.**
- **Method 2 (explicit "does this API exist" check):** the legacy validates existence via the publisher
  API-availability endpoint; v2 proves existence indirectly (retrieve-by-id 200 + presence in the list) but has
  **no dedicated exists-endpoint step**. This is a **negligible residual** — the existence guarantee is already
  asserted two other ways. If we ever want literal parity, add a one-line "API exists" check to the same
  scenario; **not recommended** as a standalone port (hollow).

## 4. Coverage summary
| Legacy behaviour | Covered in v2? | Where / note |
|---|---|---|
| Get all APIs; created API present | ✅ | `api_lifecycle.feature` (retrieve-all + in-list assertion) |
| API existence check (dedicated endpoint) | ◑ | proven indirectly (retrieve-by-id 200 + in-list); dedicated exists-endpoint not ported (negligible) |
| ×2 tenant | ✅ | `api_lifecycle` Scenario Outline |
