# Scoping — `ConsumerOrganizationVisibilityTestCase` (org visibility / B2B)

Legacy: `.../tests/organization/ConsumerOrganizationVisibilityTestCase.java` — super-tenant only. **7 `@Test`**.
Status: **SCOPED — reclassified from Wave A "port now" to a new-harness sub-project** (it is *not* pure-API: the
tests require org-scoped users, which need SOAP claim/user provisioning). No overlay needed (default config;
verified earlier). Not yet implemented.

## What the tests do (7 methods)
1. `testAddOrganization` — admin `POST/GET /organizations` (create 2 sub-orgs, assert count). **Pure REST.**
2. `testSetOrganizationVisibilityNoneToAPI` — API `visibleOrganizations` defaults to `none`; assert anonymous & sub-org users are **forbidden** (403 / not in list), parent-org user **can** see it.
3. `testSetOrganizationVisibilityForAnOrgToAPI` — set `visibleOrganizations=[subOrg1UUID]`; only subOrg1 users + parent see it; subOrg2 & anonymous forbidden.
4. `testSetOrganizationVisibilityForAllOrgsToAPI` — set `visibleOrganizations=[all]`; everyone incl. anonymous sees it.
5. `testKeymanagerVisibility` — a KM with org visibility is visible to the intended org's devportal users only.
6. `testApplicationSharingBetweenOrganizations` — an app shared to an org is 200 for that org's user, 403 for another.
7. `testOrgSpecificSubscriptionPolicies` — org-specific subscription policies.

## API surface
- **REST (have/easy):** admin `/organizations` (create/list/get/update/delete — `OrganizationsApi`); publisher API `visibleOrganizations` field (on `APIDTO`, set via update); admin key-manager `allowedOrganizations`; devportal API list/get, KM list, app-share.
- **SOAP provisioning (the gap):**
  - `ClaimMetadataManagementService.addLocalClaim` — register the `http://wso2.org/claims/organizationId` local claim (mapped to attribute `organizationId`). Done ONCE in setup. **No v2 client.**
  - `RemoteUserStoreManagerService.setUserClaimValue` — tag each user with `organization` + `organizationId` claims. **v2's `TenantUserProvisioner.addUser` sets roles only, not claims.**

## What v2 already has (reduces the lift)
- `SimpleHTTPClient.sendSoapRequest(...)` — the SOAP plumbing EXISTS.
- `TenantUserProvisioner.addUser(tenant, userKey, username, password, roles)` — SOAP `RemoteUserStoreManagerService.addUser` (roles only) + user listing already work via `Utils.getRemoteUserStoreManagerServiceURL`.
- So the harness gap is **incremental**: add two SOAP operations (register-org-claim, set-user-claim) + the org REST endpoints, not a from-scratch SOAP client.

## Harness pieces to build (Phase 1)
1. SOAP `addOrganizationLocalClaim` — new payload against `services/ClaimMetadataManagementService` via `sendSoapRequest` (+ `Utils` URL).
2. SOAP `setUserClaimValue` — extend `TenantUserProvisioner` (or a new step) to set `organization`/`organizationId` claims on a user.
3. Org-user provisioning helper — 6 users across parent + 2 sub-orgs, each with its `organizationId` claim, each usable as a distinct devportal actor.
4. Admin `/organizations` REST — `Utils` URLs + create/list steps + `OrganizationDTO` payload.
5. Anonymous devportal client — list/get an API with no auth.

## Tests (Phase 2, verify-first each)
Org CRUD; the visibleOrganizations none/specific/all matrix with per-actor (anonymous / parent / subOrg1 / subOrg2) visibility assertions; KM org visibility; cross-org app sharing (v2 has a `DevPortalApplicationSharingRunner` to build on); org-specific subscription policies. **Verify-first the visibility ENFORCEMENT** on 4.7.0 before asserting — prototype and keyless-invoke both showed 4.x behaviour can differ from legacy.

## Down-payment option (if wanted before the full harness)
`testAddOrganization` (org CRUD create+list) is pure REST and portable standalone now. But it's low value alone — the visibility assertions (the point of the class) need the Phase-1 harness, so a down payment doesn't unlock much.

## Recommendation
Treat as a **standalone new-harness sub-project** (its own approval item, like the Wave-C harness gaps). Build Phase 1 (SOAP claim/user provisioning + org REST) once, verify-first the visibility semantics, then Phase 2. Roughly a full increment of harness work + a full increment of tests — larger than any single Wave-A item done so far.

## Phase 1a — DONE & VERIFIED (harness primitives)
Delivered in `OrganizationSteps` + `Utils` (`getOrganizations/OrganizationById/ClaimMetadataMgtService` URLs). Verified live on 4.7.0 via a throwaway probe (now removed):
- **`I register the organization local claim`** — SOAP `ClaimMetadataManagementService.addLocalClaim` registers `http://wso2.org/claims/organizationId`. **Finding:** the `addLocalClaim` wrapper element must be in namespace `http://org.apache.axis2/xsd` (not the service ns) — a SOAP fault ("namespace mismatch require http://org.apache.axis2/xsd") pinned it. DTO fields are in `http://dto.mgt.metadata.claim.identity.carbon.wso2.org/xsd`.
- **`I set the organization claim of user {u} to {orgId}`** — SOAP `RemoteUserStoreManagerService.setUserClaimValue` (ns `http://service.ws.um.carbon.wso2.org`).
- **`I create an organization {ext} with display name {dn} as {idKey}`** / **`I retrieve all organizations`** / **`I delete the organization {idKey}`** — admin `/organizations` REST.
- **Finding:** creating a sub-org returns **403 `901302` "User does not belong to any organization"** unless the acting admin has the `organizationId` claim set AND its token is minted AFTER the claim is set. The parent orgId is an arbitrary string (legacy uses `"123-456-789"`). Sequence that works: register-claim → set-admin-claim → mint admin token → create org.

## Phase 1b — DONE (org-scoped users + tokens + anonymous client)
Delivered in `OrganizationSteps`:
- `I provision organization user {key} with roles {roles} in organization {orgId}` — SOAP `addUser` (via `TenantUserProvisioner`, registers the key as a resolvable actor) + `setUserClaimValue` to tag the org. Mint the user's token AFTER (`I have valid devportal access token as {key}`) — org membership must precede token issue.
- `I set the visible organizations of API {idKey} to {none|all|UUID}` — publisher GET→modify→PUT.
- `I retrieve the devportal API {idKey}` (acting actor's devportal token) and `… anonymously` (no auth) — the per-vantage-point visibility checks.
- The `create-organization` step stores both the internal UUID (for `visibleOrganizations`) and the external id under `<idKey>External` (for tagging users).

## Phase 2 — DONE (core, methods 1–4), enforcement VERIFIED
`admin/organization_visibility.feature` (`AdminOrganizationVisibilityRunner`, own IntegrationV2-OrganizationVisibility block), `@cap:admin @feat:tenants-orgs`. **Verified 1/1** (one comprehensive scenario). **The enforcement holds on 4.7.0** (verify-first probe → confirmed, unlike prototype):
- **none** → parent-org user 200; sub-org user 403; anonymous 403.
- **specific (subOrg1)** → subOrg1 user 200; parent 200; subOrg2 403.
- **all** → sub-org user 200; anonymous 200.
- Plus org CRUD: create ×2 + list (both present) + delete ×2 (200).

## ×2 tenant (carbon.super + tenant1.com)
All 4 scenarios run as `Scenario Outline`s over `| tenant | suffix |` — the harness is tenant-parameterized
(`... in tenant "<tenant>"` variants of register-claim / set-claim / provision-user resolve the row's tenant
admin creds; org creation / tokens / checks use the row's `admin<suffix>` and `<user><suffix>` actors).
Verified 8/8. B2B orgs work inside a sub-tenant on 4.7.0 (probed first). **Finding:** anonymous DevPortal access
to a **tenant** API needs the tenant context — a plain anonymous GET returns **404** (not 403/200); fixed by
sending the `X-WSO2-Tenant: <tenant>` header on the anonymous check.

## Finding — DevPortal visibility is eventually consistent (flake fix)
The first full-suite run failed this scenario intermittently (a `visibleOrganizations` check returned a stale 403/200; the failing step differed run-to-run). DevPortal API visibility propagates asynchronously after a `visibleOrganizations` change — a single GET can catch stale state (legacy did `waitForAPIDeployment()` after each change). Fixed by making the checks **poll until the expected status** (`I retrieve the devportal API … until the response status code becomes N within S seconds`, + an anonymous variant). Confirmed non-flaky over repeated runs.

## Methods 5–7 — DONE (Group K)
All ported into `admin/organization_visibility.feature` (4 scenarios total, verified together):
- **5. Key-manager org visibility** — a KM with `allowedOrganizations=[subOrg1]` is visible in the DevPortal key-manager list to subOrg1 users only; setting `["none"]` hides it from all. New glue: KM-create/update-with-`allowedOrganizations`, devportal KM-list poll steps.
- **6. Cross-org application sharing** — an app created `SHARED_WITH_ORG` by subOrg1-user-1 is 200 for subOrg1-user-2, 403 for a subOrg2 user; setting `PRIVATE` → 403 for the same-org user. New glue: app-create-with-visibility, get-app-by-id-as-actor, set-app-visibility.
- **7. Org-specific subscription policies (positive)** — an org policy (`organizationPolicies=[{subOrg1,[Bronze]}]`) is applied (subOrg1's DevPortal API shows the Bronze tier) and a Bronze subscription by a subOrg1 user succeeds (201).

### Finding — org-policy disallowed-tier subscribe returns **403** (negative PORTED; earlier "500" was a test artifact)
The legacy negative (a subOrg1 user subscribing with a tier NOT in its org policy, e.g. Unlimited) asserts **403**, and legacy CI was green on it. An earlier v2 probe recorded **500 `900967`** and the negative was wrongly deferred as a "server error". **On re-investigation (2026-07-03) that 500 was a test artifact, not product behaviour.** A faithful legacy-mirror probe reproduced the 500 only because the subscribe payload used unresolved `{{placeholder}}` tokens that the `I attempt to subscribe …` step did not substitute — the literal string `{{apiId}}` was sent as the API id and the server 500'd looking it up (log: *"Failed to retrieve the API {{probeApiId}} …"*). With the payload fixed to the tokens that step substitutes (`{{apiId}}`/`{{applicationId}}`), the disallowed-tier subscribe returns a clean **403** and the server logs the correct reason:

> `Tier Unlimited is not allowed for API/API Product … Only [Bronze] Tiers are allowed.`

So the product behaviour matches legacy exactly — no regression, no JIRA. Actions taken:
- **Negative ported** into method 7 of `organization_visibility.feature` (Bronze subscribe 201 → unsubscribe → disallowed Unlimited attempt → **403**), ×2 tenant.
- **Hardened `iAttemptToSubscribeToApi`** (`ApplicationBaseSteps`) to call `Utils.resolveContextPlaceholders` after the fixed-token replaces, mirroring the positive `iSubscribeToApi`. This makes a typo'd placeholder **fail fast** (the resolver throws on an unknown key) instead of being sent verbatim and masquerading as a genuine 500 — the exact trap that produced the false finding.
- **Lesson:** a 500 with a generic code (`900967`) is not self-explanatory — always read the server-side stacktrace/reason before recording a "product returns 500" disposition; here it revealed a garbage-input path, not a tier-denial path.

A gotcha per steps that also require resetting the acting actor to `admin` before an admin-token call after minting org-user devportal tokens (`Identity.adminToken()` resolves the ACTING actor).
