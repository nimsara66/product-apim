# Port report — `KeyManagersTestCase` (group1, admin)

Legacy: `.../tests/restapi/admin/KeyManagersTestCase.java` — Factory ×2 (super+tenant). 38 `@Test`.
Delivered: `admin/key_manager_config.feature` (`AdminKeyManagerConfigRunner`, IntegrationV2-Admin block), `@cap:admin @feat:key-manager-config`. **Verified 26/26** (13 scenario definitions ×2 tenant).

## Consolidation
Legacy is a per-type matrix over 6 connector types {Auth0, WSO2-IS, KeyCloak, Okta, PingFederate, Forgerock} × {add / add-missing-mandatory / add-optional / get / update / delete}, plus existing-name + permissions. Collapsed to:
- **1 CRUD arc as a Scenario Outline over the 6 type payloads** (create 201 → get 200 → update-description 200 → delete **200** → get 404). 3 payloads carry only mandatory fields, 3 also carry optional `description` + JWKS `certificates`, so both the "mandatory-only" and "with-optional" legacy paths are covered by the one outline.
- **Missing-connector-config negative** as an outline over all 6 (strip `additionalProperties` → 400).
- **Duplicate-name negative** (409): create, then re-create with the same name (captured from the first response).

Payload fixtures: `artifacts/payloads/keymanagers/{auth0,wso2is,keycloak,okta,pingfederate,forgerock}.json`. Steps (in `ApplicationBaseSteps`, admin-plane): `I create a key manager from payload … as …`, `I attempt to create … without connector config`, `I attempt to create … with name …`, `I retrieve/delete the key manager …`, `I update the key manager … setting its description to …`, `I retrieve all key managers`. `Utils.getKeyManagers/KeyManagerById` URLs; `Constants.CREATED_KEY_MANAGER_IDS` + `ResourceCleanup` sweep (admin token).

## Method dispositions
| Method(s) | Disposition | Where / note |
|---|---|---|
| testAddKeyManagerWith{Type} (×6) + …WithOptionalParams (×6) | ✅ ported | CRUD-arc create row per type (3 mandatory-only + 3 with-optional payloads) |
| testGetKeyManagerWith{Type} (×6) | ✅ ported | CRUD-arc get row (asserts type echoed) |
| testUpdateKeyManagerWith{Type} (×6) | ✅ ported | CRUD-arc update-description row (GET→modify→PUT, 200, reflects description) |
| testDeleteKeyManagerWith{Type} (×6) + delete-missing (404) | ✅ ported | CRUD-arc delete row (200) + get-after-delete (404) |
| testAddKeyManagerWith{Type}WithoutMandatoryParam (×6) | ✅ ported | missing-connector-config negative outline → 400 |
| testAddKeyManagerWithExistingKeyManagerName | ✅ ported | duplicate-name negative → 409 |
| testKeyManagerPermissions | ⏭️ **increment 2** | DENY-role KM → a user in that role is refused key-generation with 403; needs a 2nd role/user + the store key-generation-with-key-manager flow |

## Findings (verify-first)
1. **Null Booleans → 500.** A payload omitting `enabled` / `enableTokenGeneration` / `enableMapOAuthConsumerApps` / `enableOAuthAppCreation` makes the server 500 (NPE unboxing the null flags), NOT 400. The legacy `DtoFactory` always sets these four true — added them to every payload.
2. **KeyCloak requires `revokeEndpoint`.** Without it, create fails **400 `901401` "Required Key Manager configuration missing"** — the KeyCloak connector treats revoke as mandatory (Auth0/others don't). Added it to the KeyCloak payload. (Confirms the connector-config validation is per-type — exactly what the missing-config negative asserts.)
3. **Pure config CRUD, no external connectivity** — the connector endpoints are stored, not contacted; create→201 is immediate (no deployment/propagation wait needed since the KM isn't used for token validation here).

## Decisions
- **×2 tenant** (super + tenant) for parity with the legacy Factory. KM CRUD is fast, so 26 scenarios still run in ~36 s.

## Net
Full KM config CRUD across all 6 connector types + missing-config (400) + duplicate-name (409) ported, consolidated from 38 methods into 13 scenario definitions (26 runs ×2 tenant). Permissions → increment 2.
