# Port report — `APIProductRevisionTestCase` (group1)

Legacy source: `.../tests/api/revision/APIProductRevisionTestCase.java` — 7 `@Test`. No factory (super). No restart.

Delivered v2: `publisher/api_products.feature` — `Scenario Outline: API product revision lifecycle …`
(`@legacy:APIProductRevisionTestCase`, ×2 tenant), reusing the **generic revision steps** with
`resourceType="api-products"` (create / list / deploy / undeploy / restore / delete). **Verified 14/14.**

## Method dispositions
| # | Method | Disposition | Where / note |
|---|--------|-------------|--------------|
| 1 | testAddingAPIProductRevision | ✅ ported | create revision → 201 |
| 2 | testGetAPIProductRevisions | ✅ ported | list revisions → 200 |
| 3 | testDeployAPIProductRevisions | ✅ ported | deploy revision → 201 + wait-until-deployed |
| 4 | testUnDeployAPIProductRevisions | ✅ ported | undeploy → 201 |
| 6 | testRestoreAPIProductRevision | ✅ ported | restore → 201 |
| 7 | testDeleteAPIProductRevision | ✅ ported | delete → 200 |
| 5 | testRestoreAPIWithDeletedResourcesInProduct | ⏭️ **deferred → increment 2** | restore edge: a revision that lacks resources later removed from the underlying API — a narrower nuance than the core restore |

## Net
The API-product revision **lifecycle CRUD** is fully ported ×2 (reusing existing revision glue). Only the
narrow "restore a revision that predates a resource deletion" edge (#5) is deferred to increment 2. The
garbage-UUID revision negatives are intentionally not ported (hollow — consistent with the `api_revisions`
decision).
