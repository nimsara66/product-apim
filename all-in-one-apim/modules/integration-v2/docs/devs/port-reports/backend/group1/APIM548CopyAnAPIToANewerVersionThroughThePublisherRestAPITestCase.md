# Port report — `APIM548CopyAnAPIToANewerVersionThroughThePublisherRestAPITestCase` (group1)

Legacy source: `modules/integration/tests-integration/tests-backend/src/test/java/org/wso2/am/integration/tests/publisher/APIM548CopyAnAPIToANewerVersionThroughThePublisherRestAPITestCase.java`
Factory modes: `SUPER_TENANT_ADMIN`, `TENANT_ADMIN` (×2 tenant). Restart: no.

## 1. What the legacy test does
| # | Method | Flow / assertion |
|---|--------|------------------|
| 1 | `testCopyAnAPIToANewerVersionThroughThePublisherRest` | create API → `getAllAPIs()` → `copyAPI(newVersion, apiId, defaultVersion=true)` → `getAllAPIs()` → assert both the original and the copied new version exist |

Concern: **copy an API to a new version** (the publisher "create new version" / copy operation), with the new
version set as default.

## 2. v2 coverage
`publisher/versioning.feature` — `Scenario Outline: Create, version and publish an API as <actor>`
(`@legacy:APIVersioningTestCase`, ×2 tenant): `I create a new version "2.0.0" … with default version "true"`
(the same `copyAPI`/create-new-version operation) → asserts the new version reflects `isDefaultVersion=true` and
the original flipped to `false` → deploy → publish. Both versions existing after the copy is asserted.

## 3. Disposition — ✅ COVERED (no port)
Fully redundant with `versioning.feature` — `copyAPI` is the create-new-version operation, and v2 asserts the
new-version copy exists with the default flag flipped (stronger than the legacy's mere presence-in-list check).
No residual delta.

## 4. Coverage summary
| Legacy behaviour | Covered in v2? | Where |
|---|---|---|
| Copy API to a newer version (default=true) | ✅ | `versioning.feature` (create new version, default flag) |
| Both original + copy exist afterwards | ✅ | `versioning.feature` (new-version reflects default; original flipped) |
| ×2 tenant | ✅ | `versioning` Scenario Outline |
