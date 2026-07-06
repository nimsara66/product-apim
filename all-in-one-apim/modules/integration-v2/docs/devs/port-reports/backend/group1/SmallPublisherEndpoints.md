# Port report — Small publisher endpoints (group1, publisher)

A grab-bag of small, self-contained publisher endpoints from §1/§8. Delivered by extending existing features
(`api_config.feature` via `PublisherConfigRunner`, `api_lifecycle.feature` via `PublisherLifecycleRunner`) —
**no new runner**. **Verified** (8 new scenarios ×2 tenant; both runners green, 87/87 in the combined run).

## Method dispositions
| Legacy class | Disposition | Where / note |
|---|---|---|
| `GetLinterCustomRulesThroughThePublisherRestAPITestCase` | ✅ ported (×2) | GET /linter-custom-rules → 200 (`api_config.feature`) |
| `APIM634GetAllTheThrottlingTiersFromThePublisherRestAPITestCase` + `APIMGetAllSubscriptionThrottlingPolicies` | ✅ ported (×2) | GET /throttling-policies/{subscription,api} → 200, contains `Unlimited` (`api_config.feature`) |
| `GIT_1638_UrlEncodedApiNameTestCase` | ✅ ported (×2) | create+deploy an API with a **hyphenated name** → retrieve (name round-trips) → publish → Published (`api_lifecycle.feature`) |
| `CheckEmptyCORSConfigurationsTestCase` | ⏭️ increment 2 | needs a CORS-disabled payload + a robust empty-array assertion on the nested corsConfiguration |
| `APIMANAGER5872UpdateAPIWithoutThumbnail…` | ⏭️ increment 2 | thumbnail set (multipart) → update-without-thumbnail → thumbnail persists |
| `APIM638ValidateRoleOfUser` (validate role) | ⏭️ increment 2 | GET /roles/{roleId} (roleId is base64 userstore/role) — encoding uncertain; verify-first |

## New glue
- `Utils.getLinterCustomRulesURL`, `Utils.getPublisherThrottlingPoliciesURL(level)`.
- Steps (`PublisherBaseSteps`): `I retrieve the linter custom rules`, `I retrieve the publisher {string} throttling policies`.
- Fixture `create_apim_hyphen_name_api.json` (hyphenated `${UNIQUE}` name/context).

## Net
The clean, self-contained small endpoints (linter rules, publisher throttling-policy read for subscription+api levels, url-encoded API name round-trip) ported ×2 tenant into existing features. The fixture/multipart/role-encoding-heavier ones (empty-CORS, thumbnail-preserve, validate-role) → increment 2.
