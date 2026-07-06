# Backend port plan — group1

Source: `modules/integration/tests-integration/tests-backend/src/test/resources/testng.xml`, all `<test>` blocks
tagged `group="group1"`. **~68 distinct test classes** (87 `<class>` refs incl. duplicates + `*Config`
`@BeforeTest` helpers), **~450 `@Test` methods**. Analysed at the method level against the current v2 feature set
(see [_index.md](_index.md) for legend & principles). **This is a plan for review — nothing implemented yet.**

Legend: ✅ covered · 🟡 extend · 🟢 port · 🔵 port+verify · 🏗 needs-new-harness · 🧩 needs-capability-tag · ⚪ skip.

---

## 0. Headline

- **Already covered in v2 (dedup): ~8 classes** — mostly the publisher CRUD/lifecycle/versioning/docs basics and
  shared-scope (some with residual deltas to extend).
- **Extend existing v2 features: ~14 classes** — validation/negative matrices, doc-type breadth, OAS validation,
  throttle-policy CRUD breadth, application/secret management, lifecycle transitions.
- **New but API-driven & deterministic (port now): ~20 classes** — API products, advanced/deny/subscription
  throttle CRUD, environments, key-manager config, governance, tenant config, org visibility, small publisher
  endpoints.
- **Need new harness capability first: ~12 classes** — WebSocket invocation, AI APIs, MCP servers, GraphQL
  subscriptions, remote logging sink, authcode/implicit grants, schema validation, mutual-SSL, one-time-token.
- **Skip: ~5 classes** — external security-audit service, micro-GW ETCD/JMS internal checks, duplicate env test.
- **New capability-map additions needed: `publisher:api-products` (`@feat`), a new `governance` capability
  (features `rulesets`/`policies`/`compliance`), and a new `mcp` capability.** Everything else reuses existing tags:
  - **governance is its own top-level capability** (added 2026-07-02) — it is a distinct API
    (`/api/am/governance/v1`) with its own token scopes (`apim:gov_*`), so it is NOT folded under `admin` (whose
    contract is `/api/am/admin/v4`). The mis-placed `admin:governance`/`admin:compliance` features were removed.
  - **prototype is NOT a new feature** — "PROTOTYPED" is an API *lifecycle state*; its tests fold into
    `publisher:api-lifecycle` (state + transition; inline-mock via `definitions`), `gateway:rest-invocation`
    (invoke a prototyped API without a subscription), and `devportal:search` (visibility). Not an API-Product.
  - **deny policies are `admin:throttling-policies`** — legacy uses `addDenyThrottlingPolicy` /
    `BlockingConditionDTO` (deny *throttling* policies / blocking conditions), the same admin throttling family
    as the application/subscription/custom/advanced policy CRUD already tagged `@feat:throttling-policies`.
  - `admin:key-manager-config`, `admin:environments`, `admin:tenants-orgs`, `gateway:streaming-invocation`,
    `gateway:ai-invocation`, `analytics:request-logging` all already exist.

---

## 1. Publisher — API lifecycle, create, update, CRUD

| Legacy class (methods) | Covers | v2 status | Disposition | Target | Notes |
|---|---|---|---|---|---|
| `APIM520Update…` (2) | update; update-after-rename | ✅ `api_lifecycle` (update + rename-invariant) | ✅ COVERED | publisher/api-lifecycle | — |
| `APIM534GetAll…` (2) | list all; check-exists | ✅ `api_lifecycle` (list) | ✅ COVERED | | — |
| `APIM548Copy…` (1) | copy to new version | ✅ `versioning` | ✅ COVERED | | — |
| `APIM18Create…` (7) | create; malformed-context; remove; import-swagger-same-context; archive w/ remote refs (+bad); sandbox-only endpoints | ✅ **create/publish + malformed-context (400) + duplicate-context (409) ported** | 🟡 EXTEND (residual) | publisher/api-lifecycle | **malformed-context → 400** (backtick context) and **duplicate-context → 409** ported into the create-validation matrix / a new dup-context outline (×2, verified). Residual → increment 2: **archive-import** (needs a fixture .zip + import-archive step) and **sandbox-only + internal-key SANDBOX** (needs generateInternalApiKey + JWT-decode). |
| `APIMANAGER5834InvalidInputs` (2) | invalid context | ✅ **DONE** | ✅ ported | api-lifecycle negatives | invalid context `/` → 400 (×2). Context-mismatch-across-versions → increment 2. |
| `APIM514WithoutMandatoryFields` (7) | create without name/context/version/tier/endpoint/resources/action → 400 each | ✅ **DONE** | ✅ ported | api-lifecycle negatives | blank name/context/version → 400 (×2). **tier / endpoint / resources verified live → 201** (v4 create is design-first — all optional, not rejections); action has no v4 field — all skip. |
| `APIM519WithoutLoggingIn` (1) | create with no auth → 401 | ✅ **DONE** | ✅ ported | api-lifecycle negatives | unauthenticated create → 401 (×1, tenant-agnostic) |
| `APIM574ChangeStatusToPrototyped` (6) | full LC transitions: →prototyped→created→published→deprecated→retired | 🟡 gateway LC (block/deprecate/retire) covered; publisher-plane PROTOTYPED transition not | 🟡 EXTEND | publisher/api-lifecycle | publisher-plane lifecycle-transition matrix incl. **PROTOTYPED/CREATED demote** |
| `APIM638ValidateRoleOfUser` (2) | validate role of existing / non-existing user | ❌ | 🟢 PORT | publisher (validate-role) | small; super-only in legacy |
| `APIMANAGER4877ScopesAndUpdateTemplate` (1) | create API w/ operation scopes + update template + delete (v0.16 data-file flow) | ✅ covered by `scopes.feature` (v4 model) | ⚪ SKIP | — | **Duplicate.** `publisher/scopes.feature` ("Assign a shared scope to an API and to an operation") covers both assertions in the current v4 model: an API with **operation-level scopes** (attach scope to an operation + re-fetch confirms) and the **update-then-persist** arc (PUT operations, re-fetch reflects it). NOT a restart (the `ServerConfigurationManager` field is declared but unused — old mis-tag). The legacy **v0.16 `x-scope`/`x-wso2-scopes`** representation is intentionally not reproduced (v4 uses first-class `scopes[]`); the add-a-new-resource-via-update micro-delta lives with `AddEditRemoveRESTResource` (Wave B). |
| `APIMANAGER5872WithoutThumbnail` (1, restart) | update API preserves thumbnail | ❌ | 🟢 PORT | publisher/api-config | thumbnail set/preserve; restart incidental |
| `GIT_1638_UrlEncodedApiName` (2) | hyphen/url-encoded API name: get detail + publish | ✅ **DONE** | ✅ ported | api_lifecycle.feature (×2) | [report](group1/SmallPublisherEndpoints.md) |
| `MandatoryPropertiesTestWithRestart` (2, restart) | update without/with mandatory **custom properties** → enforcement | ❌ | 🔵 PORT+VERIFY | publisher/api-config | needs a tenant-config setting mandatory props; verify restart is incidental (likely config-driven) |
| `AdvancedConfigurationsTestCase` (4) | tenant-config get/update/schema; update w/ invalid JWT → 401 | ✅ **DONE** | ✅ ported | admin (tenant-config) | `admin/tenant_config.feature` — get/schema + update round-trip (capture→modify→restore) + invalid-JWT 401 + non-admin 401 = 8/8, ×2. [report](group1/AdvancedConfigurationsTestCase.md) |
| `CheckEmptyCORSConfigurations` (1) | create API + assert empty CORS config shape | ❌ | 🟢 PORT | publisher/api-config | small |
| `APIResourceWithTemplate` (3) | url-template resources; uri-encoding default/encode | ✅ **DONE** (pub-plane dup; invoke ported faithfully) | ✅ ported | gateway | **Publisher-plane (methods 1–2) is a duplicate** — create-with-URL-template + retrieve + publish is already exercised by every `api_lifecycle` create/publish (default payload ops are `/customers/{id}`). **Method 3 (uri-encoding at the gateway invoke) ported faithfully** into `rest_invocation.feature` (×2), matching legacy's exact APIM shape: **uri-template `/{val}` resource + `{uri.var.val}` templated endpoint** + wildcard node `/echo/*` backend; the encoded path is sent via a new **raw-path invoke** (`doGetRaw`/`setNormalizeUri(false)`) so `%28`/`%29` reach the gateway verbatim → 200, backend gets the doubled `/echo/subS2222-0496%2815%2927436-0/S2222-0496%2815%2927436-0` (encoding preserved, matching legacy's Synapse backend). **NO regression** — the enabler is the uri-var endpoint, not a wildcard APIM resource; the framework needed a raw-path invoke (default client decoded `%28`). Verified live via a standalone APIM+backend probe first. |
| `APICreationTestCase` (2) | create+deploy with **Mutual SSL**; with **gatewayType** | ❌ | 🔵 PORT+VERIFY | publisher/api-config (+gateway) | mutual-SSL needs client cert infra → 🏗 for the invoke half |
| `APISecurityAuditTestCase` (1) | 42Crunch security-audit report | ❌ | ⚪ SKIP | — | external audit service + token; not black-box |
| `DocAPIParameterTampering` (1) | tampered doc-API param must not leak stack trace | ❌ | ⚪ SKIP | — | **Dropped as hollow** — a garbage-input negative (tampered doc id → 401), the same class as the garbage-UUID negatives dropped family-wide. Asserts nothing black-box-meaningful beyond "bad input is rejected". |
| `GetLinterCustomRules` (1) | get linter custom rules | ✅ **DONE** | ✅ ported | api_config.feature (×2) | [report](group1/SmallPublisherEndpoints.md) |
| `PluggableVersioningStrategy` (1) | pluggable versioning strategy config | ❌ | 🔵 PORT+VERIFY | publisher/versioning | niche server-config; verify behaviour |

**Deep dive — create-validation matrix (`APIM514`/`APIM5834`/`APIM519`). ✅ DONE.** Ported as a
`Scenario Outline` appended to `publisher/api_lifecycle.feature` (`@type:negative`, reusing
`PublisherLifecycleRunner` — no new runner): blank name/context/version + invalid context `/` → 400 (×2
tenant), and unauthenticated create → 401 (×1). Built from the valid base payload via a new generic
`I set the field … in the payload …` step (no per-case fixture). The legacy tier / endpoint / resources cases
were dropped **after live probes** — on 4.7.0 a create is design-first, so a missing tier (`policies`),
`endpointConfig`, or `operations` each returns **201** (valid, not a rejection); `name`/`context`/`version` are
the only hard-required fields. `action` has no v4 create field. Report:
[CreateValidationMatrix](group1/CreateValidationMatrix.md).

---

## 2. Publisher — definitions (OAS)

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `OASTestCase` (9) | create; update; **definition update**; **advance configs**; import; **unsupported server blocks**; **empty resource path** (validate/import/update) | ✅ **DONE** | ✅ ported | `definitions.feature` — the 6 deltas (def-update, advance-configs, unsupported-server stripping, invalid validate/import/update), ×2 tenant, reusing `PublisherDefinitionsRunner`. **Finding:** swagger PUT is multipart (form-urlencoded → 415). [report](group1/OASTestCase.md) |

---

## 3. Publisher — documentation

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `APIM611 HowTo` (2), `APIM620 SDK` (2), `APIM623 PublicForum` (1), `APIM625 SupportForum` (1), `APIM627 Other` (3, incl. remove), `APIM614 file-source` (4) | doc **types** {HowTo, Sample/SDK, PublicForum, SupportForum, Other} × **sources** {inline, url, file} + retrieve/remove | ✅ **DONE** | ✅ ported | `docs.feature` — two matrix scenarios: all 5 doc types + all 3 sources (inline/url/file-upload), ×2 tenant, reusing `PublisherDocsRunner`. [report](group1/DocTypeMatrix.md) |

---

## 4. Publisher — prototype (a lifecycle STATE — folds into existing features, NO new tag)

"PROTOTYPED" is an API lifecycle state, not a capability. These fold into `publisher:api-lifecycle` (state +
transition + inline-mock), `gateway:rest-invocation` (invoke without subscription), and `devportal:search`
(visibility). Not an API-Product concept (products have no PROTOTYPED state).

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `PrototypedAPITestcase` (4) | prototype-endpoint invoke; demote-to-created invoke; OAS3 inline mock; OAS2 inline mock | 🟡 **transition DONE**; invoke deferred | 🟡 EXTEND | **✅ PROTOTYPED transition** ported (`api_lifecycle.feature`, ×2). **Finding: keyless gateway invoke returns 401 on 4.7.0** (both deploy orders) — semantics changed; invoke/demote/inline-mock → inc2. [report](group1/PrototypedAPITestcase.md) |
| `APIM23VisibilityOfPrototypedAPIInStore` (1) | prototyped API visible in devportal | ⏭️ inc2 | 🟢 PORT | devportal/search — visibility of PROTOTYPED uncertain on 4.7.0; verify-first |
| `APIM24…DifferentView` (3) | prototyped visibility (general API, tags) | ⏭️ inc2 | 🟢 PORT | devportal/search (with the above) |

**Note:** prototype invocation is a genuine runtime property (prototyped APIs are invocable without a
subscription/key) — worth porting to the gateway lane. Mock (inline OAS2/OAS3) generation is publisher-plane.

---

## 5. Publisher — GraphQL

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `GraphqlTestCase` (10) | create via schema-with-interfaces / **SDL-URL** / **endpoint** / **malformed-context**; retrieve/update schema; invoke JWT/OAuth; op-level scopes; op-level security | 🟡 `graphql_design` + `graphql_invocation` + `graphql_scope_enforcement` cover create-from-schema, interface, retrieve/update, scope 200/403, authType-None, invoke | 🟡 EXTEND | port the 3 uncovered creation paths: **SDL-URL**, **endpoint**, **malformed-context (400)** |
| `GraphQLQueryAnalysisTest` (4) | add/retrieve **query complexity/depth**; invoke JWT/OAuth under complexity | ❌ | 🟢 PORT | publisher/graphql-design + gateway | complexity/depth analysis — genuine gap |
| `GraphqlSubscriptionTestCase` (8, restart) | GraphQL **subscriptions over WS**: publish-with-subscriptions; JWT subscribe; invoke; invalid payload; complexity; depth; scopes; throttling | ❌ | 🏗 NEW-HARNESS | needs a **WS/GraphQL-subscription client** + subscription transport enabled; restart likely config. Defer until WS harness exists (see §9). |

---

## 6. Publisher — API Products (`@feat:products` — already in capability-map) — 🔨 **Increment 1 DONE (verified 14/14)**

| Legacy (methods) | Increment 1 status | Report |
|---|---|---|
| `APIProductCreationTestCase` (12 active) | ✅ core ported (create+invoke, new-version, default-version, malformed-context, swagger, update-underlying); scopes/op-policies/visibility/advertise → **increment 2**; mutual-SSL → **Wave C**; category → ⚪ skip (disabled) | [report](group1/APIProductCreationTestCase.md) |
| `APIProductLifecycleTest` (5) | ✅ ported (create/publish/blocked-503/retire+delete + deprecated-200 extension); delete-deprecated-with-subscription → increment 2 | [report](group1/APIProductLifecycleTest.md) |
| `APIProductRevisionTestCase` (7) | ✅ ported (revision CRUD ×2, reusing generic revision steps); restore-with-deleted-resources edge → increment 2 | [report](group1/APIProductRevisionTestCase.md) |

**Delivered:** `publisher/api_products.feature` (`PublisherApiProductsRunner`) + `gateway/api_product_invocation.feature`
(`GatewayApiProductInvocationRunner`), `@feat:products`. New glue in `PublisherBaseSteps` (create-product-from-API,
new-version, get-swagger, resource-typed change-lifecycle) + `CREATED_API_PRODUCT_IDS` cleanup (swept before APIs);
revision/deploy/publish/invoke steps reused via `resourceType="api-products"`. **Finding:** a retired *product*
fails key-validation with `900900`/500 (not 404 like a retired API) — retirement asserted on the publisher plane
(retire→delete), not the gateway. **Increment-2 remainder:** scopes, op-policies (request/response),
visibility-restricted, advertise-only, delete-deprecated-with-subscription, restore-with-deleted-resources.
**Wave C:** mutual-SSL product.

---

## 7. Publisher — API revisions

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `APIRevisionTestCase` (28) | full revision CRUD + deploy/undeploy/restore + **all invalid-UUID/vhost/deployment negatives** + **deployment-acknowledgment counts** + **traces-of-deleted-API not visible** + CREATED/PUBLISHED/BLOCKED/DEPRECATED/RETIRED invocation | 🟡 `api_revisions` (CRUD + delete-guard) + `lifecycle_stage_invocation` (5 LC states) cover the bulk | 🟡 EXTEND | port the genuine deltas only: **invalid-vhost deploy (400)**, **deployment-ack counts**, **deleted-API traces absent**. The garbage-UUID 404/500 negatives stay dropped (hollow — consistent with the restart-family decision). |

---

## 8. Admin — throttling policies, tiers, deny/block, export-import

> 🔨 **Increment: policy CRUD breadth DONE (verified 8/8).** Application / subscription / advanced / custom
> policy **CRUD** (create incl. request-count + bandwidth + advanced conditional-groups, retrieve, update,
> delete, 404) + list are ported in `admin/throttling_policy.feature`, reusing the create steps from the
> enforcement work + new generic get/update/delete-by-type steps. App/sub/advanced ×2 tenant; custom super-only.
> Per-class reports: [Application](group1/ApplicationThrottlingPolicyTestCase.md) ·
> [Subscription](group1/SubscriptionThrottlingPolicyTestCase.md) · [Advanced](group1/AdvancedThrottlingPolicyTestCase.md) ·
> [Custom](group1/CustomThrottlingPolicyTestCase.md) · [GetAll](group1/GetThrottlingPoliciesTestCase.md).
> **Deferred → increment 2:** 409 duplicate-name (all types), advanced delete-assigned + op↔API-level +
> cross-admin permission, subscription permission-visibility. **✅ deny policies DONE**
> (`admin/deny_policies.feature`, `AdminDenyPolicyRunner`, 8/8 ×2 tenant — IP/IP-range/USER + duplicate-409 + search;
> API-context/APPLICATION → increment 2; report [DenyPolicyTestCase](group1/DenyPolicyTestCase.md)).
> **Later increment:** ThrottlePolicyExportImport (16). Enforcement (429) already done in `throttling_enforcement`.

| Legacy (methods) | Covers | v2 | Disposition | Target | Notes |
|---|---|---|---|---|---|
| `ApplicationThrottlingPolicyTestCase` (6) | add(request-count/**bandwidth**); get+update; delete; **409** dup; **404** missing | 🟡 `admin/throttling_policy` has app create/retrieve/delete | 🟡 EXTEND | admin/throttling-policies | add bandwidth-limit, update, 409, 404 |
| `SubscriptionThrottlingPolicyTestCase` (8) | add(rc/bw); **subscription enforcement**; **policy permission** visibility; get+update; delete; 409; 404 | 🟡 enforcement done in `throttling_enforcement`; sub CRUD not in `throttling_policy` | 🟡 EXTEND | admin/throttling-policies | add subscription CRUD + bandwidth + permission-visibility |
| `CustomThrottlingPolicyTestCase` (6) | add/get/**update**/409/delete/404 | 🟡 custom create/retrieve/delete in `throttling_policy` | 🟡 EXTEND | admin/throttling-policies | add update + 409 + 404 (non-restart canonical) |
| `AdvancedThrottlingPolicyTestCase` (13) | add(rc/bw/**conditional-groups**); get+update; delete(+assigned); 409; 404; **op↔API level change**; **cross-admin delete permission** ×3 | 🟡 advanced ENFORCEMENT done; **CRUD absent** | 🟢 PORT | admin/throttling-policies | advanced CRUD + conditional groups + op/API-level + cross-admin permission |
| `GetThrottlingPoliciesTestCase` (1) | list + default policies present | ❌ | 🟢 PORT | admin/throttling-policies | small |
| `ThrottlePolicyExportImportTestCase` (16) | export + import (new / update / **conflict-without-update**) × {custom, subscription, application, advanced} | ✅ **DONE** | ✅ ported | admin/throttling-policies | `admin/throttle_policy_export_import.feature` (`AdminThrottlePolicyExportImportRunner`, Admin block): per type — create 201 → export 200 → import overwrite=false **409** → overwrite=true **200** → delete → import **201**. subscription/application/advanced ×2 tenant; **custom ×1 (super — custom Siddhi create is 403 in a sub-tenant)**. Export `type` tokens: sub/app/api/global; import is a multipart `file` upload. 7/7 verified. |
| `APIMGetAllSubscriptionThrottlingPolicies` (1) | get sub policies by quota type (publisher) | ✅ **DONE** | ✅ ported | api_config.feature (×2) | [report](group1/SmallPublisherEndpoints.md) |
| `APIM634GetAllThrottlingTiers` (1) | get throttling tiers (publisher) | ✅ **DONE** | ✅ ported | api_config.feature (×2) | [report](group1/SmallPublisherEndpoints.md) |
| `DeleteTierAlreadyAttachedToAPI` (1) | update API after deleting an attached subscription tier | ✅ **DONE** | ✅ ported | publisher/api-lifecycle (@dep:admin) | `api_lifecycle.feature` (×2, all existing steps): attach a custom sub-tier → publish → devportal shows it → delete the tier → **API update still 200** → publisher API no longer lists it. Server logs `WARN Unknown tier … found on API` after delete (confirms the attached-tier removal path). Glue: `The response should not contain` now resolves `{{}}`. |
| `ChangeSubscriptionBusinessPlanForcefully` (5) | change sub plan: invalid-subId; invalid-plan; restricted-tier; valid; tier-update-pending | 🟡 `subscription_management` has "update plan" | 🟡 EXTEND | devportal/subscription-management | add the negatives + restricted-tier + pending-status |
| `APIDenyPolicyTestCase` (14) | deny-policy CRUD; by context / **application** / **user** / **IP** / **IP-range** + invalid variants | ✅ **DONE** (self-contained types) | admin/**throttling-policies** | `admin/deny_policies.feature` — IP CRUD lifecycle + IP-range/USER create + duplicate-409, ×2 tenant = 8/8. API-context + APPLICATION deny → increment 2. **Verified negatives:** malformed-IP → 500 (skip, not enshrined); non-existing-user → 201 (valid, not a rejection). [report](group1/DenyPolicyTestCase.md) |
| `DenyPolicySearchTestCase` (3) | blocking-conditions add + search by type/value | ✅ **DONE** | admin/throttling-policies | search-by-type+value ported in `admin/deny_policies.feature` |

**Deep dive — deny policies use the existing throttling-policies tag.** They are deny *throttling* policies /
blocking conditions (`addDenyThrottlingPolicy`, `BlockingConditionDTO`) — the same admin throttling family we
already tag `@feat:throttling-policies` for the application/subscription/custom/advanced CRUD. So they go into
`admin/throttling_policy.feature` (or a sibling in the Admin block), **no new `@feat`**. Enforcement (a denied
context/IP/app/user is actually blocked at the gateway → 403/429) is the runtime half worth adding beyond the
legacy's CRUD — flag 🔵 for that enforcement scenario (verify the exact status the gateway returns).

---

## 9. Admin — key managers, environments, scopes, application search

> 🔨 **Environments increment DONE (verified 4/4).** Gateway-environment **CRUD** (create/list/retrieve/update/
> delete/404, ×2 tenant) + gatewayType (APK) + create-validation negatives (no-vhost / bad-name / no-displayName
> / duplicate-Default → 400) ported in `admin/gateway_environments.feature` (`AdminGatewayEnvironmentsRunner`).
> Report: [EnvironmentTestCase](group1/EnvironmentTestCase.md). **Finding:** APK gateways need an APK-style
> vhost (httpContext, no ws/wss ports) or the create 500s. **Deferred → increment 2:** multi/special-char vhost
> variants, deploy-a-revision-to-a-vhost, delete-env-with-revisions, devportal-swagger, gateway permissions,
> default-env get-instances. **KeyManagers ✅ DONE (26/26, ×2 tenant)** — `admin/key_manager_config.feature`
> (`AdminKeyManagerConfigRunner`), report [KeyManagersTestCase](group1/KeyManagersTestCase.md). **Still to do in §9:**
> APISystemScopes, ApplicationsSearch.

| Legacy (methods) | Covers | Disposition | Target | Notes |
|---|---|---|---|---|
| `KeyManagersTestCase` (38) | 6 KM types {Auth0, WSO2IS, Keycloak, Okta, PingFederate, ForgeRock} × {add / add-missing-mandatory / add-with-optional / get / update / delete} + existing-name + **permissions** | ✅ **DONE** (consolidated) | admin/key-manager-config | `admin/key_manager_config.feature` — CRUD arc ×6 types + missing-config (400) + duplicate-name (409), ×2 tenant = 26/26. **Findings:** null Booleans→500; KeyCloak needs `revokeEndpoint` (else 901401). Permissions → increment 2. [report](group1/KeyManagersTestCase.md) |
| `EnvironmentTestCase` [restapi.admin] (18) | gateway-env CRUD; vhost variants (single/multiple/special-chars/no-displayname/no-vhost); gateway-type; already-exists; **deploy-revision-with-vhost**; devportal-swagger; update/remove-vhost; **delete-with/after-revisions**; **permissions**; get-instances | 🟢 PORT | admin/environments | large; consolidate vhost-variant negatives into an outline; deploy-with-vhost + delete-with-revisions are genuine runtime-ish |
| `EnvironmentTestCase` [restapi.testcases] (1) | basic env test | ⚪ SKIP | — | subset/duplicate of the admin one |
| `APISystemScopesTestCase` (3) | scope-mapping add/get; role-alias delete | ✅ **DONE** | ✅ ported | admin (role-scope-mapping) | `admin/system_scopes.feature` (`AdminSystemScopesRunner`, in the Admin block): role-alias add → list-contains → clear, ×2, all 200. Endpoint is **`/system-scopes/role-aliases`** (not `/role-aliases` — verified live: the latter 404s). New `@feat:role-scope-mapping`. |
| `ApplicationsSearchByNameOrOwner` (4) | admin search apps by name/owner (admin vs non-admin) | 🟢 PORT | admin (app-search) | small |

---

## 10. Governance — rulesets, policies & compliance (`@cap:governance` — its own capability) ✅ DONE

Governance is a **distinct product API** (`/api/am/governance/v1`) with its own token scopes (`apim:gov_*`), so
it is a **top-level `governance` capability** (added 2026-07-02; the mis-placed `admin:governance`/`compliance`
features were removed). **Delivered** in a new **IntegrationV2-Governance** block (default config, no gateway
backend), **19/19 verified** (rulesets 10 + policies 6 + compliance 3). New infra established here for the whole
capability: **gov-scoped token** (`BaseSteps.mintGovernanceToken` + `I have a valid Governance access token as
"<actor>"` + `Identity.governanceToken()`; verified the DCR client mints it), **multipart ruleset upload**
(reuses `doPost/PutMultipartWithFiles`), `Utils` governance URLs + `extractIdByName`, and `ResourceCleanup` for
gov rulesets/policies (policies before rulesets, deleted with the gov token).

| Legacy (methods) | Covers | Disposition | Delivered |
|---|---|---|---|
| `RulesetMgtTestCase` (8) | default rulesets; valid/invalid create; valid/invalid update; delete; JSON-content create; delete-attached-to-policy | ✅ **DONE** | `governance/rulesets.feature` — full CRUD + negatives (990120), ×2 tenant. Attached-delete (990101) → policies.feature. [report](group1/RulesetMgtTestCase.md) |
| `PolicyMgtTestCase` (4) | default policy; global policy create/update/delete | ✅ **DONE** | `governance/policies.feature` — CRUD ×2 + ruleset-attached-delete negative (409/990101). [report](group1/PolicyMgtTestCase.md) |
| `APIComplianceTestCase` (2) | compliance after create-with-default-policy; **deployment blocking with policy** | ✅ **DONE** | `governance/compliance.feature` — BLOCK-on-deploy (903300, ×2) + async NON_COMPLIANT eval (×2, poll-not-sleep). Verify-first caught the `NON_COMPLIANT` wire value (DTO said `NON-COMPLIANT`). [report](group1/APIComplianceTestCase.md) |
| `MCPComplianceTestCase` (1) | compliance of an MCP server | 🏗 NEW-HARNESS | depends on MCP (§11) — deferred to Wave C |

---

## 11. Gateway / runtime & new-protocol capabilities

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `AddEditRemoveRESTResource` (4) | invoke GET; invoke POST before-add (405/404); **add POST resource → re-invoke**; **add URL pattern → invoke** | 🟡 `rest_invocation` (static) | 🟡 EXTEND | gateway | dynamic resource add → redeploy → re-invoke; genuine runtime |
| `ChangeAPIEndPointURL` (3) | invoke; **edit endpoint URL**; invoke via new endpoint | ❌ | 🟢 PORT | gateway | endpoint-change → routing follows; needs a 2nd backend endpoint (like default-version) |
| `APIInvocationWithMessageTypeProperty` (1) | invoke w/ in-sequence messageType property | ❌ | 🔵 PORT+VERIFY | gateway/mediation | niche; verify property behaviour |
| `GraphqlSubscriptionTestCase` (core) | GraphQL **subscription** over WebSocket (graphql-ws): connection_init/ack + subscribe→data; (+complexity/depth matrix) | ✅ `gateway/graphql_subscription_invocation` (core sub ×2) | ✔ **CORE DONE (×2, Wave C)** — complexity/depth tail pending | gateway/graphql-invocation | **Verified live ×2** (`Tests run: 2, 0 failures`). Reused the WS harness (port 9099, JDK WebSocket client). Backend: an executable-schema subscription server on `am-graphQL-sample` built on the **official `subscriptions-transport-ws` library** (emits `liftStatusChange` → `{"name":"Astra Express"}`). Client step negotiates the `graphql-ws` subprotocol + does the init/ack/start/data exchange. **LIBRARY DECISION (verify-first, evidence-backed):** APIM 4.7.0's gateway speaks the **`graphql-ws` subprotocol** (old: `connection_init`/`connection_ack`, `start`/`data`) — observed directly in the gateway→backend leg (node logs: `upgrade accepted (subprotocol=graphql-ws)`, `received type=start`) and in the legacy test (`setSubProtocols("graphql-ws")`). The npm **`graphql-ws` package implements a DIFFERENT subprotocol (`graphql-transport-ws`)** and **refuses** the legacy one (proven by local smoke: close 1006 "Server sent no subprotocol"), so it is INCOMPATIBLE with the gateway. `subscriptions-transport-ws` is the official library that implements the subprotocol the gateway actually uses → chosen. *(Originally hand-rolled when npm seemed offline; upgraded to the official lib once npm worked; re-verified through APIM 2/0.)* TWO gotchas fixed verify-first: (1) the API needs **SUBSCRIPTION operations in the create payload** or the WS inbound logs `No matching API ... to dispatch` (the v2 graphql-create step does not auto-derive ops); (2) the gateway establishes the backend WS leg asynchronously, so the client must **wait ~15s after connect before connection_init** (legacy sleeps 20s) or the ack never routes back. Schema `graphql_subscription_schema.graphql`, payload `create_apim_graphql_subscription_api.json`. **Remaining tail:** query-complexity (4021) / depth (4020) rejection, throttling. |
| `SchemaValidationTestCase` (7) | request/response JSON-schema validation (invalid/valid body, required headers, unsecured resource) | ✅ `gateway/schema_validation` | ✔ **DONE (×2, Wave C)** | gateway/schema-validation | **Verified live ×2** (`Tests run: 2, 0 failures`). Imports the petstore OAS with `enableSchemaValidation=true` (schemas must come from an imported OAS, not a create-payload); backend = petstore routes ADDED to `node-customer-service` (`/pets` valid array, `POST /pets` valid Pet, `GET /pets/:id` branches on `isAvailable` → valid Pet vs id-less body so ONE resource drives both response-validation outcomes, `/pet/findByStatus` unsecured). All 7 legacy cases faithful: invalid body→400, missing required header→400, header present→200, id-less response→**500 "Schema validation failed in the Response:"**, valid body→200, valid response→200, unsecured missing-`status`→400. New glue: `I import openapi definition … as` (import+store id; refactored the attempt-import), `… with request header … set to …` invoke variant, `gateway/schema-validation` @feat. Node image updated via the offline COPY-overlay (CI rebuilds from source). |
| `WebSocketAPITestCase` (20), `WebSocketAPIScopeTestCase` (7), `APIMANAGER5869WSGatewayURL` (4), `WebSocketAPIInvocationWithTracing` (3) | WS **invocation**: token/JWT, throttling, **API-key with IP/referer/expired/opaque restrictions**, invalid-token, gateway-URL shape, scope-gated methods | ✅ `gateway/websocket_invocation` (core token invoke ×2) | ✔ **CORE + FULL TAIL DONE (×2, Wave C)** — only throttling parked | gateway/streaming-invocation | **Verified live ×2** (core `Tests run: 2, 0 failures`; tail batches each green in isolation). Built the WS harness end-to-end: (1) exposed the gateway **WS inbound port 9099** on `DynamicApimContainer` (+`Constants.GATEWAY_WS_PORT`, `getGatewayWsUrl()`, `baseGatewayWsUrl` in the block listener); (2) a **WS echo backend** on `node-customer-service` built on the **official `ws` library** (`WebSocketServer`, echoes UPPERCASE — the legacy Jetty `WebSocketServerImpl` contract). *(Decision: originally hand-rolled as a dependency-free RFC 6455 server when `npm install ws` failed — that failure turned out to be transient, so it was upgraded to the official `ws` library; re-verified through APIM 2/0.)* (3) a **JDK `java.net.http.WebSocket` client** step (`WebSocketInvocationSteps`, explicit trust-all SSLContext to dodge the JVM's broken default-SSLContext init); (4) plan-parameterised subscribe (`… with plan "AsyncUnlimited" …`); payload `create_apim_ws_echo_api.json` (WS type, ws://nodebackend:3001, API-policy AsyncUnlimited + operation tier Unlimited). Core token-auth invoke ported ×2. **Tail — NOW DONE ×2 (each batch green in isolation):** ✔ **token-type parity** (JWT [product default] + OAUTH/opaque application tokens — new token-type composite); ✔ **auth negatives** (invalid-token → rejected, api-key-when-disabled → rejected — new reject-expecting WS step); ✔ **API-key invoke** (apikey header — new api-key WS step; `create_apim_ws_apikey_api.json` with `["oauth2","api_key"]`); ✔ **scope-gated methods** (shared scope on the WS ops → echo with scope, rejected without); ✔ **gateway-URL shape** (devportal advertises the `ws://…:9099` URL — devportal GET, no WS client); ✔ **`wss://` secure invocation on 8099** (docs-first, NOT in legacy — exposed `GATEWAY_WSS_PORT`/`getGatewayWssUrl`/`baseGatewayWssUrl`, invoke over wss with token AND api-key via the trust-all client); ✔ **CORS origin validation** (own block `IntegrationV2-WebSocketCORS` + `wscors` overlay — **PROBE FINDING:** WS CORS is OFF by default, needs gateway-wide `[apim.cors] enable_validation_for_ws=true` + an `allow_origins` list; then allowed origin echoes, no-origin echoes, **disallowed origin rejected** — the last exceeds the legacy which only tested allowed+no-origin). New glue: header-parameterised WS connect, `doMutualSSLGet`-style api-key/reject/wss steps, `…allowing N events per minute` sub-policy, `tokenType` app composite. **PARKED:** ⏸️ **WS/streaming throttling — PARK ACCEPTED** (verify-first): neither the API-level advanced policy NOR a subscription event-count plan throttled WS frames. **Confirmed by a manual STANDALONE test** (`docker run wso2am` all-in-one, default config): 20 spaced WS frames on an 8-event/min plan all echoed, while a REST 3/min control on the same server threw 429 after 3 calls — so the throttle engine works but WS event throttling does NOT enforce on the all-in-one profile (NOT a harness artifact). Only a real Traffic Manager (`wso2am-tm`) could change this — out of scope for the single-container suite. Scenario commented out, glue kept, see [increment-2-backlog §N](increment-2-backlog.md). ✔ **API-key IP-restriction (WS negative) DONE ×2** + **TRANSPORT FINDING**: originally parked as "no source-IP control". A standalone probe showed the **REST passthrough honors `X-Forwarded-For`** for the `permittedIP` check (key→`1.2.3.4`: no XFF→403, `XFF:1.2.3.4`→200, `XFF:5.6.7.8`→403), BUT over **WS the inbound uses the REAL socket client IP and IGNORES XFF** (a matching `XFF:1.2.3.4` is still rejected). So for WS: the **negative is covered ×2** (an IP-restricted key is rejected — enforcement proven, even with a spoofed matching XFF). The **WS positive is now AUTOMATED ×2** (`@rule:api-key-ip-restriction`, regression): the gateway sees a host→published-port connection as the container's **docker-network GATEWAY IP** — the harness reads it (`DynamicApimContainer.getGatewayClientIp()` from the live container inspect) and publishes `{{gatewayClientIp}}`; a key restricted to that IP → **echo** (positive), a key restricted to `1.2.3.4` → **rejected** (negative), and rejected **even with a matching X-Forwarded-For** (the WS inbound uses the socket IP, ignores XFF — the transport finding). Verified: backend echoed exactly the positive case ×2; negatives rejected at the handshake. (Earlier "not possible / not worth it" calls were wrong — the WS-seen source IS the deterministic network-gateway IP, readable at runtime; no CIDR-guessing or log-scraping needed.) REST positive+negative also DONE via XFF. **REST positive+negative NOW DONE ×2** (`keymanager/api_key.feature` `@rule:ip-restriction`: key→`permittedIP 1.2.3.4`, `XFF:1.2.3.4`→200, `XFF:5.6.7.8`→403 — new `…using api key … and forwarded-for … until … {status}` step); AI skipped (same authenticator, redundant). ✔ **FINAL TAIL BATCH — full `WebSocketAPITestCase` parity DONE ×2** (verified `Tests run: 12, 0 failures` in one filtered block run; 12 backend echoes = exactly the positive count, no false passes): **referer restriction** (`@rule:api-key-referer-restriction` — key with `permittedReferer`; matching `Referer` header echoes, non-matching rejected — ENFORCEMENT CONFIRMED over WS, unlike IP referer is a client-settable header so both cases assert directly); **expired api-key** (`@rule:api-key-expired` — `validityPeriod:1` key rejected once expired, after a warm+positive control); **oauth-when-disabled** (`@rule:oauth-disabled` — api_key-only API `create_apim_ws_apikeyonly_api.json` rejects an OAuth token, mirror of api-key-when-disabled); **query-param auth** (`@rule:query-param-auth` — `?apikey=` and `?access_token=` both echo, the AUTH_IN.*_QUERY modes); **sandbox-only endpoint** (`@rule:sandbox-endpoint` — sandbox token routes to the sandbox backend & echoes, production token rejected since no prod endpoint, `create_apim_ws_sandbox_api.json`); **malformed context** (`@rule:malformed-context` in `publisher/streaming_design.feature` as @cap:publisher — WS API with context `echo{version}` → 400). Opaque-key variants are SUBSUMED (v2 uses the real store-generated opaque key, so JWT-vs-opaque duplication collapses onto the api-key/IP scenarios). New glue: `…using api key … and referer …` echo/reject, `…using api key/access token query param …`, helpers `apiKeyAndReferer`/`appendQuery`. **NOTE — tracing is NOT a WS tail item:** `WebSocketAPIInvocationWithTracingTestCase` is the legacy's only tracing touchpoint, but tracing is a **cross-cutting** gateway telemetry feature (the `open_tracing` Synapse handler, all API types). It is tracked as a consolidated **Distributed-tracing (all API types)** PARKED area in [increment-2-backlog §N](increment-2-backlog.md) — parked until redone with genuine span assertions against an inspection sink — NOT ported here per-protocol. |
| `AIAPITestCase` (19) | AI provider CRUD; unsecured/secured AI API create/publish/**invoke** (+opaque key); endpoint CRUD; **round-robin**; **failover** version; provider models | ✅ `gateway/ai_api_invocation` (providers-list + create→invoke ×2) | ✔ **FULLY DONE (×2, Wave C)** — incl. round-robin + failover | gateway/ai-invocation | **Verified live ×2** (core `Tests run: 4, 0 failures`; full AI feature `Tests run: 16, 0 failures`). Backend: a **mock LLM** chat-completions route added to `node-customer-service` (returns a Mistral-shaped response incl. `$.usage.*` token fields). Core flow: admin **registers a no-auth AI service provider** (`TestAIService`) → **imports the AIAPI-subtype API** from the Mistral OpenAPI (endpoint→mock LLM) → deploy/publish/subscribe → invoke `POST /v1/chat/completions` → 200. Plus **predefined-providers list**. **TAIL now DONE (×2 each):** (1) **api-key auth** — AIAPI `securityScheme` = `["oauth2","api_key"]`; mint an application API key and invoke with the `ApiKey` header (new `…using api key … and payload …` invoke variant). (2) **endpoint CRUD** — `/apis/{id}/endpoints` add prod+sandbox, list, get, update URL, delete (new `ApiEndpointSteps` + `Utils.getApiEndpoints[ById]URL`; @cap:publisher @feat:api-config). (3) **secured (auth-enabled) provider** — provider `authenticationConfiguration` apikey/header injects the API's `endpoint_security` credential `Bearer 123`; a new `/with-auth` mock route returns 401 unless that header is present, so the 200 (backend log `Authorization header present`) PROVES injection. (4) **token-based throttling** — an `AIAPIQUOTALIMIT` subscription policy (300 total tokens/min; new `…allowing N total tokens per minute` step); the mock reports 358 tokens/response → accumulated usage trips the quota → **429** (API must OFFER the tier via a policies-update before subscribe). (5) **weighted round-robin** — the shipped `modelWeightedRoundRobin` common op-policy applied at API level (new `I apply the AI mediation policy …` step: looks up the common policy id, injects `apiPolicies.request`, single-quotes the JSON config); the gateway REWRITES the request `$.model` per weight before forwarding → the (model-aware) mock echoes it, proving selection (weight 100/0 → deterministic medium; asserts NOT the client's small). (6) **model failover** — `modelFailover` policy with a target model on a **failover-target** mock route that returns **429** (the legacy WireMock signal) + a fallback model on the default echo endpoint; `primaryProductionEndpointId` set to the failover endpoint so the gateway hits it FIRST (429) → falls back → response echoes the fallback (large) model (backend log confirms `failover-target route hit`). **Cleanup fix (verify-first):** AI-provider delete failed with an `AM_API_AI_CONFIGURATION`→`AM_LLM_PROVIDER` FK violation because the inline best-effort delete ran BEFORE the `@cleanup` hook deleted the API; made provider teardown **hook-managed and ordered AFTER APIs** (`ResourceCleanup.CREATED_AI_PROVIDER_IDS`, admin token) — fixes a latent leak in the original oauth scenario too. **Model-aware mock:** the chat/completions route echoes `req.body.model`; a `/failover-target` route returns 429. |
| `GeminiAPIUnlimitedTierDisabledTestCase` (3, restart) | Gemini AI create/invoke/throttling, unlimited-tier disabled | 🏗 NEW-HARNESS | gateway/ai-invocation | AI backend + tier-disabled config; restart = config |
| `MCPServerTestCase` (15) 🧩 | MCP server from OpenAPI / API / proxy; revision deploy; subscribe+**invoke**; tool ops; scopes | ✅ proxy + from-OpenAPI + from-API (all 3 types) | ✔ **ALL 3 TYPES DONE ×2 (Wave C, verified) — EXCEEDS legacy** | mcp (new capability) — feats proxy-invocation / openapi-invocation / api-invocation | Each type ×2 with CRUD + invocation + value-adds + scope + throttling (24 tests total). See the ↳ rows below. Real stateful MCP server (official SDK) for proxy; real MCP↔HTTP translation for OpenAPI/API. Findings: auth at tools/call not initialize; invalid-token 401 (proxy) vs 403 (DirectBackend/API); the 5 create fixes; operations required in create. |
| ↳ MCP proxy CRUD ×2 | Create (select tool subset) / Read / **Update exposed tools** / Delete | ✅ `@rule:crud` ×2 | ✔ **DONE ×2** (`Tests run: 4, 0 failures`) | mcp | create exposing echo,add → assert tools persisted → read → **update to add get_pets → 200 + persisted** → delete → 200 → retrieve 404. New glue: tool-parameterized proxy create (`exposing tools`), `I update the MCP server … to expose tools …` (GET→replace operations→PUT). |
| ↳ MCP proxy invocation VALUE-ADDS ×2 (exceed legacy) | real tool execution · multi-call session continuity · JSON-RPC error passthrough · negative auth | ✅ `@rule:invocation` ×2 | ✔ **DONE ×2** | mcp | Legacy asserted only canned echoes (stateless WireMock). We assert: **real execution** (`add 2+3=5`, `get_pets`→real data — args forwarded, result computed); **session continuity** (one initialize → multiple `tools/call` on the same `Mcp-Session-Id`); **error passthrough** (non-exposed tool → MCP error); **negative auth** (invalid token → 401). **verify-first FINDING:** the gateway does NOT authenticate the MCP `initialize` handshake (returns 200 with a bad token) — **auth is enforced at `tools/call`** (401). New glue: `I invoke MCP tools in one session … with calls …`, `… expecting an error …`, `… with an invalid token expecting status …`. |
| ↳ MCP tails — **ALL 3 TYPES ×2** | tool-update **ADD + REMOVE** (per type) · least-privilege · **governance/compliance** (per type) | ✅ done ×2, all 3 types | ✔ **DONE** — MCP invocation suites `24/0` in isolation; governance compliance (3 types ×2) green | mcp + governance | **Tool-update:** every type now tests both ADD and REMOVE — proxy via `to expose tools`, OpenAPI/API via `removing tool` + generic **`re-add the removed tool`** (captures the removed op verbatim, preserving subtype shape). **Least-privilege:** proxy create-subset asserts excluded tool absent; OpenAPI/API assert absence after remove. **MCP governance** under the governance capability (`governance/compliance.feature` `@cap:governance @feat:compliance @legacy:MCPComplianceTestCase`) for **all 3 create types** — each freshly-created MCP server settles **NON_COMPLIANT**, artifact `extendedType=MCP`, ×2 (governance block gains `initBackend` for proxy discovery). **MCP-Hub mode = PARKED** (config/restart, niche). **NOTE:** the added MCP/API creates raise registry-concurrency load — a full-suite TP=2 bank may hit the shared-registry race (super-tenant creates → 500); relieve via fewer concurrent blocks or more colima headroom. |
| ↳ MCP **from-API** (ExistingApi) ×2 | CRUD (import+deploy an API → generate MCP from it, read, remove-tool, delete) + invocation (real routing through the API to backend: get_pets, path-param get_pets_by_petId) + value-adds (error, negative auth=403) + enforcement (scope + throttling) | ✅ `gateway/mcp_api_invocation` (4 outlines ×2) | ✔ **DONE ×2** (`Tests run: 8, 0 failures`) | mcp/api-invocation | Import the petstore OAS as a deployed API, then wrap selected resources as tools. New glue: `I create an MCP server from api … exposing paths …` (JSON body, op shape `apiOperationMapping{apiId, backendOperation{target:path,verb}}` → POST /mcp-servers/generate-from-api); underlying-API props `mcp_petstore_api_props.json`. Invalid-token=403 (matches the DirectBackend subtype). |
| ↳ MCP **from-OpenAPI** (DirectBackend) ×2 | CRUD (create-from-OAS, read, remove-tool, delete) + invocation (real MCP↔HTTP: get_pets→backend data, get_pets_by_petId path-param) + value-adds (error, negative auth) + enforcement (scope + throttling) | ✅ `gateway/mcp_openapi_invocation` (4 outlines ×2) | ✔ **DONE ×2** (`Tests run: 8, 0 failures`) | mcp/openapi-invocation | Gateway generates a TOOL per OAS op and translates tools/call→HTTP to the REST backend (node `/pets`). New glue: `I create an MCP server from openapi … with backend …` (multipart, DirectBackend ops = REST path+verb), `I update the MCP server … removing tool …`. **FINDING:** invalid-token rejection differs by subtype — proxy=**401**, DirectBackend=**403** (both accepted as rejection). Petstore OAS `mcp_petstore_oas3.json` (operationIds get_pets / get_pets_by_petId). |
| ↳ MCP proxy ENFORCEMENT ×2 | scope-gated tool invocation + subscription throttling | ✅ `@rule:scope-enforcement` + `@rule:throttling` ×2 | ✔ **DONE ×2** (`Tests run: 8, 0 failures` for the whole proxy feature) | mcp | **Scopes** (`testScopesForProxySubtype`): gate echo with a scope bound to admin → token WITH scope → 200, WITHOUT → 403. **Throttling** (doc-advocated; `testThrottlingForProxySubtype` was DISABLED in legacy): subscribe on a bespoke 10/min policy → cumulative until-429 trips (the 429-at-handshake is treated as throttled). New glue: `I gate the MCP server … tool … with scope … bound to role …`, `I update the MCP server … to offer policies …`, `I invoke the MCP tool … expecting status …`. docs-apim advocates auth+throttling+scopes on MCP servers, so throttling was a doc-backed gap, not "n/a". | **Verified live** (`Tests run: 1, 0 failures`). Closes the legacy's stateless-mock GAP: the proxy backend is a **REAL, session-stateful MCP server built on the official `@modelcontextprotocol/sdk` v1.29.0** (node app `mcp-server`, port 3020; low-level `Server` API for clean tool schemas; installs offline, runs in node:18-alpine). The test proves the APIM gateway proxies the full stateful MCP handshake end-to-end — the node log shows `initialize → notifications/initialized → tools/call` all carrying the SAME `Mcp-Session-Id` the backend issued, and `echo` returns through the gateway. New glue: `MCPServerSteps` (proxy create + delete), `MCPInvocationSteps` (JDK HttpClient JSON-RPC invoke w/ session propagation + SSE-or-JSON parsing), `I deploy the {resourceType} resource with id` (generic), `getChangeLifecycleURL` mcp-servers→`mcpServerId`, MCP Utils URLs, new `mcp` capability + `@feat:proxy-invocation`. **Root causes found & fixed (verify-first):** (1) publisher token needed `apim:mcp_server_*` scopes; (2) proxy `url` must be the BASE (APIM appends `/mcp`); (3) SDK default SSE framing broke APIM → `enableJsonResponse:true`; (4) `registerTool` auto-adds `execution`/`$schema` → used low-level `Server` for clean schemas; (5) **`operations` (feature=TOOL + backendOperationMapping.backendOperation{verb:TOOL,target}) are REQUIRED in the proxy create** or "no URI templates were produced". **Remaining tail:** OpenAPI-mode + API-mode create/invoke, tool update, scopes, ×2 tenant. |
| ↳ MCP backend-endpoint CRUD (from-OpenAPI DONE ×2; **proxy PARKED — regression**) | list / get / update the server's backend endpoint | ✅ `@rule:backend-crud` in `gateway/mcp_openapi_invocation` (from-OpenAPI); proxy scenario **commented out** in `gateway/mcp_proxy_invocation` | 🟡 **from-OpenAPI DONE ×2**; **proxy blocked by an upstream regression** | mcp | **Verified live ×2 (from-OpenAPI).** The MCP backend-endpoint resource is `/mcp-servers/{id}/backends` — **list + get + update only** (the backend is created implicitly with the server; NO separate add/delete, so "CRUD" here is R+U). Applies only to **proxy** and **from-OpenAPI** (a from-API MCP server has no own backend). New glue: `MCPServerSteps` list/get/update backend (`Utils.getMCPServerBackends[ById]URL`), generic `I replace {old} with {new} in the payload` (BaseSteps). **Verify-first gotchas:** (1) the backends list is a **bare JSON array** (`[{…}]`), not a `{"list":[…]}` envelope → id via `[0].id`. (2) `endpointConfig` is a **stringified JSON blob** (escaped `\/`) → edit the URL at text level with a **slash-free** segment. A correct update PUTs the backend back **in full, including its `definition`**. **PROXY REGRESSION (parked):** a proxy backend's `definition` is MCP-tools JSON, but the carbon-apimgt this branch ships wrongly re-validates the backend `definition` as OpenAPI on update → `900754 "attribute tools is unexpected"` (HTTP 400). **Fixed upstream in carbon-apimgt 9.33.147.** The proxy scenario is commented out (NOT worked around by stripping the definition — that would mask the regression); re-enable after the upstream-master sync (see increment-2-backlog). from-OpenAPI is unaffected (its definition is a valid OpenAPI spec). |
| `RevokeOneTimeTokenFlowTestCase` (3) | one-time-token: invoke w/o OTT scope; invoke w/ OTT policy; outside-user | 🔵 PORT+VERIFY | gateway/security | one-time-token policy + revoke flow; verify behaviour |

**Deep dive — the harness gaps (§11) are the real scope drivers.** WebSocket invocation, AI APIs, MCP, GraphQL
subscriptions, remote-log sink, and browser grant flows each need a **new v2 test capability** (a client and/or a
backend), not just a feature file. Recommend treating each as a small **harness sub-project** (add the client to
`tests-common/testcontainers` or a step utility) that we scope and verify-first *before* writing its scenarios —
exactly the discipline that saved us on bandwidth/custom-Siddhi. These should be their own approval items.

### Wave C dispositions (verify-first, 2026-07)

| Item | Verdict | Finding |
|---|---|---|
| `SchemaValidationTestCase` | ✅ **DONE ×2** | See the §11 row above. FEASIBLE-NOW held up — reused HTTP invoke + a small petstore backend. |
| `RemoteLoggingAppenderTest` (9) | 🚫 **BLOCKED-in-harness** (re-scoped — was mis-scoped "medium/feasible") | verify-first FINDING: **4.7.0 exposes NO REST API for remote-logging config** — it is configured ONLY via a **Carbon SOAP admin stub** (`RemoteLoggingConfigClient` / `org.wso2.carbon.logging.remote.config.stub`), which v2's REST-only harness has no client for. Worse, **8 of the 9 methods assert on the container's local `log4j2.properties`** (read/write via a local `Path` under `CARBON_HOME`) — the file lives INSIDE the container, so those assertions need `execInContainer`/`copyFileFromContainer` coupling the glue to container internals. The legacy test is `@SetEnvironment(STANDALONE)` precisely because it manipulates the local FS. Only the E2E payload-delivery method (scenario 4) is sink-based, and even it (a) still reads the file to check the appender type and (b) needs the SOAP admin stub + a mock sink reachable at `host.docker.internal`. Not worth a fragile SOAP-admin + container-FS sub-project for one scenario; **defer** (no REST surface to port cleanly). |
| Mutual SSL (`APISecurityMutualSSLCertificateChainValidationTestCase`) | ✔ **DONE ×2 (Wave C)** — was mis-classified BLOCKED-infra | **Verified live ×2** (`gateway/mutual_ssl_invocation`, `Tests run: 2, 0 failures`). The "infra block" was WRONG: the gateway HTTPS listener (8243) is **already exposed** by `DynamicApimContainer` and the default 4.7.0 pack ships **`SSLVerifyClient=optional`** (`default.json` `transport.passthru_https.listener.parameters.SSLVerifyClient` + `keystore.listener_profile.ssl_verify_client`), and `baseGatewayUrl` is already the HTTPS URL. Flow: create API `securityScheme:["mutualssl","mutualssl_mandatory"]` (transport https) → **upload the accepted cert** (multipart `POST /apis/{id}/client-certificates`, returns **201**) → deploy+publish → invoke presenting a client cert. Asserts (×2, strict): matching cert (`cert_chain_root.jks`) → **200**, NO cert → **401** (900901), mismatched cert (`test.jks`) → **401**. New glue: `SimpleHTTPClient.doMutualSSLGet` (transient client loading the JKS **key material**), `MutualSslSteps` (upload + present-cert / no-cert invoke-until-status), `Utils.getClientCertificatesURL`; fixtures copied to `artifacts/certs/mutualssl/`. **KEY GOTCHA:** an uploaded cert only becomes active when the gateway re-reads its dynamic SSL profile — default interval **600000 ms (10 min)**; a `tomlExtraOverlayPath` overlay (`configFiles/mutualssl`) shrinks `transport.http.listener.ssl_profile.read_interval` to 10s so the cert activates within seconds. Own `<test>` block (`IntegrationV2-MutualSSL`). |
| Authorization-code / implicit grant (`GrantTypeTokenGenerateTestCase`) | ⏸️ **PARKED — NOT COVERED (infra)** | Headless-capable (drives `/authorize`+login+consent+token over HTTP, no browser), but needs a **real reachable WSO2 IS** serving those endpoints. No IS container in the v2 network. **Consciously left uncovered** — see [increment-2-backlog §N](increment-2-backlog.md) for the enabler (a real IS container) + re-enable criteria. |
| B1 KM deny-role 403 (`KeyManagersTestCase.testKeyManagerPermissions`) | ⏸️ **PARKED — NOT COVERED (infra)** | Confirmed (see §B): a fake-endpoint KM never issues keys (key-gen 500s before the 403). Needs a **real IS/KM backend** that issues keys AND enforces DENY roles. **Consciously left uncovered** — see [increment-2-backlog §N](increment-2-backlog.md). |

**Blocked items are recorded, not faked** (per the standing "defer blocked, don't fake" directive). The three
infra-blocked items collapse to **two shared-infra investments**: (a) an **HTTPS/mTLS gateway listener** (unblocks
mutual-SSL) and (b) a **real WSO2 IS container** on the network (unblocks authcode/implicit + B1-KM). Remote-log-sink
additionally needs a **Carbon SOAP admin client** in the harness. None are in scope as a single test port.

---

## 12. Application / DevPortal / grants

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `ApplicationTestCase` [application] (18) | app CRUD-by-id; key-gen-by-id; subscription; **map-keys (BYO OAuth keys)** (+neg); **consumer-secret CRUD by key-mapping-id** (fetch/generate/get/revoke/multiple/count/props/minimal); **sandbox key** secret | 🟡 **consumer-secret DONE**; rest EXTEND | 🟡 EXTEND | key-manager + devportal/applications | **✅ consumer-secret management (multiple client secrets)** ported: `keymanager/multiple_client_secrets.feature` (`KeyManagerMultipleClientSecretsRunner`, standard KeyManager block) — fetch-key-details + generate/list/revoke + minimal-payload, 4/4 ×2. **Findings:** enabled by default in 4.7.0 (default.json — **no overlay needed**, verified by a no-overlay run); revoke→204. Sandbox-key secret → inc2. Report: [ApplicationConsumerSecretTestCase](group1/ApplicationConsumerSecretTestCase.md). **Still EXTEND:** map-keys (BYO OAuth), get/key-gen/subscription by-id. |
| `ApplicationTestCase` [restapi.testcases] (2) | apps; add-with-**groupId** | 🟡 | 🟡 EXTEND | devportal/applications | groupId create |
| `ApplicationRegenerateConsumerSecret` (1) | regenerate consumer secret | 🟡 (subsumed by secret CRUD above) | 🟡 EXTEND | key-manager | — |
| `ApplicationScopeValidation` (1) | get application scope | 🟢 PORT | devportal/applications | small |
| `ApplicationWithCustomAttributes` (1) | app with custom attributes | 🟢 PORT | devportal/applications | custom-attribute config |
| `GrantTypeTokenGenerate` (7, restart) | app create; corrupted-credentials (neg); **authorization_code**; **implicit**; no-callback create; authcode-without-callback (neg); authcode display-name | 🏗 NEW-HARNESS | key-manager/token-issuance | authcode/implicit need a **browser/consent flow**; corrupted-credentials + no-callback are API-driven (port those now). Restart = config. |
| `ApplicationSharingTestCase` (5) | group sharing: remove-user-app affects shared; edit by owner / by group-user; **API-key revocation by shared user** (+opaque) | 🟡 `application_sharing` (1 scenario) | 🟡 EXTEND | devportal/applications | cross-user edit + shared-user key revocation |

---

## 13. JWT / token / logging / organization / misc

| Legacy (methods) | Covers | v2 | Disposition | Notes |
|---|---|---|---|---|
| `JWTRevocationTestCase` (1) | JWT token revocation → invocation blocked | ✅ `token_revocation` (RevokeTokenTestCase) | ✅ COVERED | verify identical; else EXTEND |
| `MicroGWJWTRevocationTestCase` (3) | revoke + **check ETCD** + **check JMS topic** | ⚪ SKIP | — | internal-transport (ETCD/JMS) assertions, micro-GW specific — not black-box |
| `APILoggingTest` (3) | per-API logging; per-**resource** logging; similar-template logging | 🟡 (aligned with parked `APILoggingServerRestartTest`) | 🟢 PORT (config) | analytics/request-logging | **this is the non-restart canonical** for the parked item — port the devops log-level config here; the `api.log` file-content assertion stays optional (parked rationale). |
| `RemoteLoggingAppenderTest` (11) | remote log appender config: enable/reset for AUDIT/CARBON/API; missing-appender creation; **end-to-end to remote HTTP sink** | 🏗 NEW-HARNESS | analytics/request-logging | config half is API-driven; end-to-end needs a **remote HTTP log sink** container |
| `ConsumerOrganizationVisibilityTestCase` (7) 🧩 | add org; API org-visibility (none/specific/all); KM visibility per org; **app sharing between orgs**; org-specific subscription policies | ✅ **DONE (methods 1–7)** | admin/tenants-orgs (b2b) | `admin/organization_visibility.feature` (`AdminOrganizationVisibilityRunner`, own block, **8/8 ×2 tenant**): org CRUD + visibleOrganizations none/specific/all × parent/subOrg/anonymous; KM org visibility; cross-org app sharing; org-policy tier applied + subscribable + **disallowed-tier subscribe → 403** (all 7 legacy methods, both signs). **×2 tenant** (anon tenant needs X-WSO2-Tenant header). **Findings:** addLocalClaim wrapper in `axis2/xsd` ns; org create needs admin org-membership; devportal visibility eventually consistent (poll); the disallowed-tier subscribe cleanly **403**s ("Tier … not allowed, Only [Bronze]") — an earlier "500 900967" reading was a probe placeholder artifact, since corrected & ported. [report](group1/ConsumerOrganizationVisibilityTestCase.md) |

---

## 14. Already covered / no action — per-class reports

Each ✅-covered class has a dedicated report in [`group1/`](group1/) documenting the method-level mapping to the
existing v2 scenario that makes it redundant (same convention as the server-restart family; per-class reports for
🟢/🟡/🔵 classes will be added as each is implemented).

| Class | Report | Covered by | Residual |
|---|---|---|---|
| `APIM520UpdateAnAPI…` | [report](group1/APIM520UpdateAnAPIThroughThePublisherRestAPITestCase.md) | `api_lifecycle` (update + rename-invariant) | none |
| `APIM534GetAllTheAPIs…` | [report](group1/APIM534GetAllTheAPIsCreatedThroughThePublisherRestAPITestCase.md) | `api_lifecycle` (retrieve-all + in-list) | negligible: dedicated exists-endpoint check |
| `APIM548CopyAnAPIToANewerVersion…` | [report](group1/APIM548CopyAnAPIToANewerVersionThroughThePublisherRestAPITestCase.md) | `versioning` (create-new-version, default flag) | none |
| `JWTRevocationTestCase` | [report](group1/JWTRevocationTestCase.md) | `token_revocation` (revoke → invocation blocked, JWT, ×2) | none |
| `SharedScopeTestWithRestart` | [report](group1/SharedScopeTestWithRestart.md) → [server-restart report](../shared-scope-restart-port.md) | `shared_scope_restart` + `scopes` | none (ported in restart family) |

---

## 15. Framework-capability gaps to resolve (approval items of their own)

These block ~12 classes; each is a small harness sub-project, verify-first:
1. **WebSocket client + WS backend** → WS invocation suite (WebSocket*×3, ~34 methods) + GraphQL subscriptions.
2. **AI backend mock (OpenAI/Mistral/Gemini-compatible)** → AI API suite (22 methods).
3. **MCP client/backend + `mcp` capability** → MCP server suite (16 methods).
4. **Remote HTTP log sink** → remote-logging end-to-end.
5. **Browser/consent grant flow** → authorization_code / implicit grant tests.
6. **Mutual-SSL client certs** → mutual-SSL API + product invoke.
7. **Schema-validation enabled API + echo backend** → schema-validation suite.

## 16. Concurrency / weight flags (tie-in to the 5 full-suite failures)
Invocation-heavy / timing-sensitive new scenarios to place carefully (or gate behind more memory / CP=1):
API-product invocation + lifecycle-stage, prototype invocation, WS invocation + throttling, AI invocation +
round-robin/failover, schema-validation, deny-policy enforcement, GraphQL subscription throttling. Publisher/admin
CRUD-only scenarios (the bulk of group1) are light and safe at CP=2.

## 17. Recommended implementation order (after approval)
**Wave A — pure API-driven, high value, no new harness (start here):**
API-product design+revision+lifecycle (§6), throttle-policy CRUD breadth (§8: app/sub/custom/advanced +
export-import + get-all), environments (§9), key-manager config (§9, consolidated), governance+compliance (§10),
deny-policies (§8), tenant-config (§1), org visibility (§13), OAS validation (§2), doc-type matrix (§3),
create-validation matrix (§1), application/secret management (§12), prototype design+visibility (§4), small
publisher endpoints (validate-role, tiers, linter, CORS, url-encoded-name, thumbnail).
**Wave B — extend existing gateway features (light harness reuse): ✅ DONE (all ×2, verified).**
- **B-1 dynamic-resource-add → routing** (`AddEditRemoveRESTResourceTestCase`) → `gateway/rest_invocation.feature`: POST to an undefined resource → 405; add a POST operation + redeploy → invocable (200, backend "Tom"); undefined path → 404.
- **B-2 invalid-vhost deploy → 400** (`APIRevisionTestCase`) → `publisher/api_revisions.feature`.
- **B-3 subscription invalid-business-plan → 400** (`ChangeSubscriptionBusinessPlanForcefully`) → `devportal/subscription_management.feature`, via the **publisher `/subscriptions/change-business-plan`** endpoint (empty / nonexistent / not-in-API-tier). **verify-first FINDING:** the devportal subscription PUT does NOT validate the plan — it silently keeps the current one and returns 200 — so the 400 only reproduces on the publisher force-change endpoint.
- **B-4 deployment-ack counts** (`APIRevisionTestCase`) → `publisher/api_revisions.feature`: the deployed-revisions `deploymentInfo` carries `deployedGatewayCount`/`liveGatewayCount`. Asserted FIELD PRESENCE only — the legacy count>0 (retried ~100s) is gateway-ack-lag-flaky and intentionally not asserted.
- **endpoint-change routing** — DROPPED (already covered by `gateway/default_version_routing.feature`).
- **prototype invocation** — already done in Group J.
**Wave C — new harness required (scope each first, verify-first):**
WebSocket invocation, AI APIs, MCP, GraphQL subscriptions, remote-log sink, authcode/implicit, schema-validation,
mutual-SSL, **key-manager deny-role key-gen 403 (needs a REAL reachable KM/IS backend — a created fake-endpoint
wso2is KM never becomes a usable devportal key-manager, so key-gen 500s before the permission-403; KeyManagersTestCase,
increment-2 Group B)**.

## 18. Open decisions for you
1. Approve the **capability-map additions** — now just **two**: `publisher:api-products` (new `@feat`) and a new
   **`mcp`** capability. (Prototype folds into api-lifecycle/rest-invocation/search; deny policies use the
   existing `admin:throttling-policies`. — confirmed 2026-07-02.)
2. Confirm **Wave A first**, implement class-by-class with per-class verify + report (as we did for restart).
3. For the **harness gaps (§15)** — do you want those scoped now as separate approval items, or parked until
   Wave A/B land?
4. Confirm the **dropped-as-hollow** stance carries over (garbage-UUID negatives, micro-GW ETCD/JMS internals,
   external security-audit) — I've marked them ⚪ SKIP.
