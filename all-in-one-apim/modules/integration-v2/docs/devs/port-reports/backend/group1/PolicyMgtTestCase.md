# Port report — `PolicyMgtTestCase` (group1, governance)

Legacy: `.../tests/apimGovernance/PolicyMgtTestCase.java` — Factory ×2 (super+tenant). 4 `@Test` (chained via `dependsOnMethods`).
Delivered: `governance/policies.feature` (`GovernancePoliciesRunner`, IntegrationV2-Governance block), `@cap:governance @feat:policies`. **Verified 6/6** (3 scenario definitions ×2 tenant).

## Method dispositions
| Method(s) | Disposition | Where / note |
|---|---|---|
| testDefaultPolicyRetrieval | ✅ ported | smoke (×2): list contains the default policy "WSO2 API Management Best Practices" |
| testValidGlobalPolicyCreation + testValidGlobalPolicyUpdate + testValidGlobalPolicyDeletion | ✅ ported | consolidated into one create→update→delete lifecycle scenario (×2): create a policy attaching a fresh ruleset (201) → update description in place (GET→modify→PUT, 200, reflects the new description) → delete (204) |
| (from RulesetMgtTestCase) testInvalidRulesetDelAttachedToPolicy | ✅ ported here | negative (×2): create ruleset → create policy attaching it → delete ruleset → 409, `990101` — self-contained (no reliance on default policy/ruleset wiring) |

## Design notes
- **Policy is JSON** (not multipart): `{name, description, rulesets:[…], governableStates:["API_UPDATE"], labels:["global"]}` via `doPost`/`doPut`. Steps: `I create a governance policy … attaching ruleset … as …`, `I update the governance policy … setting its description to …` (GET current → replace description → strip server-managed read-only fields → PUT), `I retrieve/delete the governance policy …`, `I retrieve all governance policies`.
- `Constants.CREATED_GOVERNANCE_POLICY_IDS` + `ResourceCleanup` sweep — **policies deleted before rulesets** (a policy references its rulesets; deleting an attached ruleset is a 409 by design, which the negative asserts).
- The legacy update re-PUT the full DTO unchanged; v2 makes the update meaningful (changes + asserts the description) — extends coverage while staying faithful (still a valid full-shape PUT → 200).

## Net
Full policy CRUD (create/update/delete/list) ported **×2 tenant**, plus the ruleset↔policy integrity negative (409/`990101`) placed here where a policy exists to attach the ruleset.
