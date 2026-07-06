# Port report — Create-validation matrix (group1, publisher)

Legacy: `APIM514CreateAnAPIWithoutProvidingMandatoryFieldsTestCase` (7 methods, Factory ×2), `APIMANAGER5834APICreationWithInvalidInputsTestCase` (2), `APIM519CreateAnAPIThroughTheRestAPIWithoutLoggingInTestCase` (1).
Delivered: appended to `publisher/api_lifecycle.feature` (`PublisherLifecycleRunner`, Publisher block — **no new runner**), `@cap:publisher @feat:api-lifecycle @type:negative`. **Verified** (9 new scenarios: 4 field cases ×2 tenant = 8 → 400, + no-auth ×1 → 401; full runner 13/13).

## Design
The negatives are built from the valid base payload (`create_apim_test_api.json`) by blanking/invalidating one field per row, via a new generic step `I set the field {string} to {string} in the payload {string}` — so no per-case fixture is needed. Reused the existing `I attempt to create an {string} resource with payload {string}` (non-asserting); added `I attempt to create an {string} resource with payload {string} without authentication` for the tokenless case.

## Method dispositions
| Legacy method(s) | Disposition | Where / note |
|---|---|---|
| APIM514 without-name / without-context / without-version | ✅ ported (×2) | blank `name` / `context` / `version` → 400 |
| APIMANAGER5834 testAPICreationWithInvalidContext | ✅ ported (×2) | context `"/"` → 400 |
| APIM519 testCreateAnAPIThroughThePublisherRest (no login) | ✅ ported (×1) | no Authorization header → 401 (auth-layer, tenant-agnostic — the tokenless request can't resolve a tenant, so ×1) |
| APIM514 without-tier | ⚪ SKIP (**verified live**) | Probed on 4.7.0: creating an API with `policies` **absent OR `[]`** returns **201** — the current product accepts a tier-less API, so there is no rejection to assert (the legacy 500 no longer reproduces). Not a negative. |
| APIM514 without-action | ⚪ SKIP | "action" is a legacy publish-lifecycle bean concept with **no v4 create-field equivalent** — nothing to POST |
| APIM514 without-endpoint / without-resources | ⚪ SKIP (**verified live**) | Probed on 4.7.0: create with `endpointConfig` removed, `operations` removed, or `operations: []` all return **201** — v4 create is design-first (endpoint/operations optional). Not rejections. (Legacy disabled these for test-harness reasons — a null-URL client error and a 2015 JIRA — unrelated to server behaviour.) |
| APIMANAGER5834 testContextMatchesPreviousAPIVersions | ⏭️ increment 2 | context-mismatch across versions ("API Context does not exist") — needs a first API created; niche |

## Net
The clean, deterministic create-validation rejections — blank mandatory fields (name/context/version) and invalid context → 400, and unauthenticated create → 401 — ported as one `Scenario Outline` (+ one no-auth scenario), ×2 tenant where meaningful. The legacy tier / endpoint / resources cases were all dropped **after live probes** — on 4.7.0 a create is design-first, so missing tier (`policies`), `endpointConfig`, or `operations` each returns 201 (valid, not a rejection). `name`/`context`/`version` are the only hard-required fields. `action` has no v4 create field. Reusable payload-mutation steps added: `I set the field … to … in the payload …`, `I remove the field … from the payload …`, `I set the field … to an empty array in the payload …`.
