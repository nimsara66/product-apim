# Port report — `AdvancedConfigurationsTestCase` (group1, admin — tenant config)

Legacy: `.../tests/other/AdvancedConfigurationsTestCase.java` — Factory ×2 (super+tenant). 4 `@Test`.
Delivered: `admin/tenant_config.feature` (`AdminTenantConfigRunner`, IntegrationV2-Admin block), `@cap:admin @feat:tenants-orgs`. **Verified 8/8** (4 scenario definitions ×2 tenant).

## Endpoints
Admin tenant-configuration API: GET/PUT `/api/am/admin/v4/tenant-config` (application/json) + GET `/tenant-config-schema`. New `Utils.getTenantConfig/getTenantConfigSchema` URLs; steps in `ApplicationBaseSteps` (admin plane); reused/added generic payload steps (`I set the boolean field … in the payload …`).

## Method dispositions
| Legacy method | Disposition | Where / note |
|---|---|---|
| testGetTenantConfiguration | ✅ ported (×2) | GET config → 200, contains "RESTAPIScopes" (extended beyond legacy's not-null) |
| testGetTenantConfigurationSchema | ✅ ported (×2) | GET schema → 200, contains "properties" |
| testUpdateTenantConfiguration | ✅ ported + extended (×2) | **round-trip:** capture original → update with a modified copy (flip EnableMonetization) → 200 → GET 200 → **restore original** → 200. Legacy only PUT a fixture and asserted not-null; v2 exercises update AND leaves the shared container's config unchanged. |
| testUpdateTenantConfigurationWithInvalidJWT | ✅ ported (×2) | invalid-signature JWT → 401 |
| — (new negative) | ✅ added (×2) | a **non-admin** token (publisher scope, no apim:admin) update → 401 |

## Design notes
- **Round-trip instead of fixture** — capturing the server's own current config and PUTting a modified copy avoids shipping a full 480-line `tenant-conf.json` fixture (which would drift from the distribution) and, crucially, **restores the original in-scenario** so the shared Admin-block container's tenant config is not left mutated (isolation-safe; EnableMonetization is benign and touched by no sibling runner).
- **×2 tenant** for the config operations (each tenant has its own tenant config); invalid-JWT also ×2 (per user request).
- No `@cleanup` needed — nothing is registered; the update scenario self-restores.

## Net
Tenant-config get / schema / update ported and **extended** into a capture→modify→restore round-trip, plus two 401 negatives (invalid JWT + non-admin), ×2 tenant. Unblocks increment-2 items that need a tenant-config setting (e.g. mandatory custom properties).
