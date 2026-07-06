# Port report — `RulesetMgtTestCase` (group1, governance)

Legacy: `.../tests/apimGovernance/RulesetMgtTestCase.java` — Factory ×2 (super+tenant). 8 `@Test` (chained via `dependsOnMethods`).
Delivered: `governance/rulesets.feature` (`GovernanceRulesetsRunner`, new **IntegrationV2-Governance** block), `@cap:governance @feat:rulesets`. **Verified 10/10** (5 scenario definitions ×2 tenant).

## New capability infrastructure (first governance port)
- **Governance token path** (verified first): governance is its own product API (`/api/am/governance/v1`) with its own `apim:gov_*` scopes — NOT reachable with the admin token. Added `BaseSteps.mintGovernanceToken` + step `I have a valid Governance access token as "<actor>"` (mints from the DCR client via password grant, scopes `apim:gov_rule_read/manage apim:gov_policy_read/manage apim:gov_result_read openid`) and `Identity.governanceToken()`. Probe confirmed: DCR client mints the gov token, `GET /rulesets` → 200.
- `Constants.DEFAULT_APIM_GOVERNANCE` + `Utils.getGovernanceRulesets/RulesetById/RulesetContent/Policies/PolicyById/ApiCompliance` URLs.
- `Constants.CREATED_GOVERNANCE_RULESET_IDS` + `ResourceCleanup` sweep (deleted with the governance token; **policies before rulesets**).
- `Utils.extractIdByName` (find an id by `name` in a `{"list":[…]}` payload — e.g. a default ruleset).
- Fixtures copied to `artifacts/apim-governance/`: `simple-spectral-ruleset.yaml`, `invalid-spectral-ruleset.yaml`, `simple-spectral-ruleset.json`.
- Ruleset create/update use the existing `SimpleHTTPClient.doPost/PutMultipartWithFiles` (multipart: file `rulesetContent`; text `name/description/ruleCategory=SPECTRAL/ruleType=API_DEFINITION/artifactType=REST_API/documentationLink/provider=admin`).

## Method dispositions
| Method(s) | Disposition | Where / note |
|---|---|---|
| testDefaultRulesetRetrieval | ✅ ported | smoke (×2): list contains the 3 defaults (WSO2 API Management Guidelines, WSO2 REST API Design Guidelines, OWASP Top 10) |
| testValidRulesetCreation + testValidRulesetUpdate + testValidRulesetDeletion | ✅ ported | consolidated into one create→update→delete lifecycle scenario (×2): 201 → 200 (reflects updated description + documentation link) → 204 |
| testInvalidRulesetCreation | ✅ ported | negative (×2): invalid ruleset → 400, `990120` |
| testInvalidValidRulesetUpdate | ✅ ported | negative (×2): invalid content on update → 400, `990120` |
| testValidRulesetCreationWithJsonContent | ✅ ported | regression (×2): create from `.json` content → 201 → delete 204 |
| testInvalidRulesetDelAttachedToPolicy | ✅ ported → **policies.feature** | 409 `990101` needs a policy to attach the ruleset; placed with policy CRUD (self-contained: create ruleset → create policy attaching it → delete ruleset → 409) rather than relying on the default policy |

## Findings
- **Multipart ruleset upload** works via the shared client; `ruleCategory/ruleType/artifactType/provider` are fixed to the spectral REST-API-definition shape the fixtures target.
- Error codes confirmed live: invalid ruleset (create & update) → **990120**; ruleset-attached-to-policy delete → **990101**.

## Net
Full ruleset CRUD (create/update/delete/list/JSON-content) + both invalid-content negatives ported **×2 tenant**. The attached-delete integrity case moved to `policies.feature` (needs a policy). Governance token + multipart + cleanup infra established for the whole capability.
