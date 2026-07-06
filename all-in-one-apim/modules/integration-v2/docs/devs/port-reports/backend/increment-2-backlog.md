# Increment-2 backlog — deferred group1 items

Items consciously deferred while porting the group1 classes (the "⏭️ increment 2" markers in the per-class
reports). These are **real, intended-to-port** scenarios that were split out of their first increment because
they need an extra prerequisite (a second user/role, an API with the policy attached, a subscription, a
visibility-restricted artifact, etc.) rather than being pure single-resource CRUD. They are **not** dropped —
this is the tracking list. (Genuinely-not-portable cases are marked ⚪ SKIP in the per-class reports with a
reason and are NOT here; new-harness needs are Wave C.)

Grouped by shared **enabler** — building the enabler once unlocks the whole group.

Status: ⬜ pending · 🔨 in progress · ✔ done.

## A. Duplicate-name 409 (enabler: none — create-then-recreate-same-name)
Cheap, identical shape across the policy types (already done for key managers).
| Item | Source | Status |
|---|---|---|
| Application throttling policy — existing-name → 409 | [ApplicationThrottlingPolicyTestCase](group1/ApplicationThrottlingPolicyTestCase.md) #5 | ✔ done |
| Subscription throttling policy — existing-name → 409 | [SubscriptionThrottlingPolicyTestCase](group1/SubscriptionThrottlingPolicyTestCase.md) #7 | ✔ done |
| Advanced throttling policy — existing-name → 409 | [AdvancedThrottlingPolicyTestCase](group1/AdvancedThrottlingPolicyTestCase.md) #7 | ✔ done |
| Custom throttling policy — existing-name → 409 | [CustomThrottlingPolicyTestCase](group1/CustomThrottlingPolicyTestCase.md) #4 | ✔ done |

## B. Role / permission enforcement (enabler: a second role-scoped user + role-restricted artifact)
The heaviest enabler; several enforcement scenarios share it. Verify-first when built.
| Item | Source | Status |
|---|---|---|
| Key-manager permissions — DENY-role KM → key-gen 403. BLOCKED: a created wso2is KM with fake endpoints never becomes a usable devportal key-manager (list shows only Resident), so key-gen via it 500s (KM unusable) and never reaches the permission-403. Needs a REAL reachable KM/IS backend (legacy had one). → Wave C harness gap. Glue built + kept: KM-create-denying-role step, generate-keys 2xx-extract guard, generate-keys {{}} resolution. | [KeyManagersTestCase](group1/KeyManagersTestCase.md) | ⬜ (Wave C: needs real KM backend) |
| Subscription-policy restricted-tier by role — user outside the ALLOW-role refused subscribe (403), ×2 tenant | [SubscriptionThrottlingPolicyTestCase](group1/SubscriptionThrottlingPolicyTestCase.md) #4 | ✔ done |
| Gateway-environment permissions — env created with an ALLOW role permission persists (×2). CRUD half of the legacy (commented-out) testGatewayPermissions; deploy-enforcement half stays deferred (was disabled/unverified in legacy). | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done (CRUD; enforcement deferred) |
| Advanced-policy delete by a different admin — cross-admin delete (200) via a provisioned 2nd admin, ×2 tenant | [AdvancedThrottlingPolicyTestCase](group1/AdvancedThrottlingPolicyTestCase.md) #11 (in-use #12,13 ≈ C#5) | ✔ done |

## C. Policy↔API assignment (enabler: an API with the policy attached)
| Item | Source | Status |
|---|---|---|
| Delete an advanced policy assigned to an API (in-use) | [AdvancedThrottlingPolicyTestCase](group1/AdvancedThrottlingPolicyTestCase.md) #5 | ✔ done |
| Advanced policy operation-level ↔ API-level change | [AdvancedThrottlingPolicyTestCase](group1/AdvancedThrottlingPolicyTestCase.md) #9,10 | ✔ done |

## D. API Product variants — ✅ ALL DONE (6/6, ×2 tenant, verified live)
| Item | Source | Status |
|---|---|---|
| Product over a visibility-restricted API — **DONE (×2).** D1: product aggregating an API with `visibility=RESTRICTED`+`visibleRoles` is still **invocable (200)** through the product. **Correction:** legacy `invocationStatusCodes` is empty → all ops expected 200, NO 403 (the visibility restriction is devportal-discovery, not runtime). Payload `create_apim_restricted_visibility_api.json`; in `gateway/api_product_invocation.feature`. | [APIProductCreationTestCase](group1/APIProductCreationTestCase.md) #5 | ✔ done (×2) |
| Product with scopes + scoped token — **DONE (×2).** D2: a scope gated on the source API operation is enforced through the product — token WITH scope → **200**, WITHOUT (different scope) → **403**. Reuses `password grant with scope` + `configuration type "scopes"/"operations"` (mirrors graphql scope enforcement). In `gateway/api_product_invocation.feature`. | [APIProductCreationTestCase](group1/APIProductCreationTestCase.md) #7 | ✔ done (×2) |
| Product with request/response operation policies — **DONE (×2).** D3: a `jsonToXML` request operation policy on the source API is applied through the product — a JSON body is transformed to XML before the backend (which echoes it → response contains `<jsonObject><foo>bar</foo></jsonObject>`). Payload `create_apim_optransform_api.json` (policy baked into the operation); **new node backend route `/reflect-body`** (raw-body echo, any content-type — jsonToXML emits `application/xml` which the node parser didn't handle). In `gateway/api_product_invocation.feature`. | [APIProductCreationTestCase](group1/APIProductCreationTestCase.md) #8,9 | ✔ done (×2) |
| Product over an advertise-only API — **DONE (×2).** D4: a product aggregating an `advertiseInfo.advertised=true` API is **invocable (200)** through the product (the product provides gateway routing to the advertised external endpoint = node backend; the advertised API itself is only created, not deployed). Payload `create_apim_advertise_api.json`. In `gateway/api_product_invocation.feature`. Verified live — advertised-API-in-product routing works on 4.7.0. | [APIProductCreationTestCase](group1/APIProductCreationTestCase.md) #10 | ✔ done (×2) |
| Delete a DEPRECATED product that has an active subscription — **DONE (×2).** Published product + active subscription → delete rejected **409 "active subscriptions exist"**; then Deprecate → 200 (DEPRECATED). In `api_products.feature`. Verified live. | [APIProductLifecycleTest](group1/APIProductLifecycleTest.md) #4 | ✔ done (×2) |
| Restore a product revision predating a resource deletion (restore edge) — **DONE (×2).** API rev1 (original resources) → add resources → rev2 → product over enlarged set → deploy → restore API to rev2 **201**, restore to rev1 (missing product-referenced resources) **400**. Uses `configuration type "operations"` to enlarge the resource set + non-asserting `restore revision` on `apis`. In `api_products.feature`. Verified live. | [APIProductRevisionTestCase](group1/APIProductRevisionTestCase.md) #5 | ✔ done (×2) |

## E. Gateway-environment vhost & deploy variants — ✅ ALL DONE (×2 tenant, verified live)
**NOTE:** 5 of these 6 were **commented-out (`//@Test`) in legacy — they never ran in CI**; ported as NEW verified coverage (per user decision), each live-probed on 4.7.0. Only E6 was legacy-enabled. **All 6 run ×2 tenant** (`admin` + `admin@tenant1.com`), verified: 18/18 green. All in `admin/gateway_environments.feature` (`AdminGatewayEnvironmentsRunner`). New glue: multi-vhost create, single-vhost update (remove), literal-or-context env id for get-instances/delete, deploy-step now resolves `{{}}` context keys, undeploy-with-payload.
| Item | Source | Status |
|---|---|---|
| Multiple vhosts; remove-vhost update — **DONE (×2).** Create env with 2 vhosts (201); update to a single vhost (200, other removed). | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done (×2) |
| Negative: duplicate hostname — **DONE.** Two vhosts with the same hostname → **400**. **verify-first FINDING:** the legacy "special-char vhost hostname" case (`foods.com#$%?`) is NOT rejected on 4.7.0 — the product **accepts it (201)** — so that assertion (never run in legacy) was dropped, not ported. | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done (dup→400; special-char is 201, documented) |
| Deploy a revision to a vhost — **DONE.** Deploy an API revision to a custom env's vhost → **201** (verified: deploy to a custom env with no running gateway succeeds). | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done |
| Delete env with / after undeploying revisions — **DONE.** Delete env while a revision is deployed → **409** (`900515`); undeploy from the custom env → 201; delete → **200**. Plus: delete built-in `Default` → **400**; delete non-existent → **404**. | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done |
| Devportal swagger/endpoints for an env-deployed API — **DONE.** An API deployed to a custom env surfaces that env's vhost among its devportal endpoint URLs (poll-until-contains the vhost). | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done |
| Get gateway instances of the Default env — **DONE.** `GET /environments/Default/gateways` → **200** with `count`. (The one legacy-enabled item.) | [EnvironmentTestCase](group1/EnvironmentTestCase.md) | ✔ done |

## G. Deny (blocking-condition) policies — resource-dependent types (enabler: a deployed API / an application)
| Item | Source | Status |
|---|---|---|
| API-context deny policy — conditionType API, value = context/version | [DenyPolicyTestCase](group1/DenyPolicyTestCase.md) | ✔ done |
| APPLICATION deny policy (value owner:appName; owner extracted from the app response) | [DenyPolicyTestCase](group1/DenyPolicyTestCase.md) | ✔ done (×2) |
| Resource-dependent negatives (non-existing context/app) — VERIFIED still **500** on 4.7.0 (server wraps a real validation in Internal-server-error). NOT enshrined per the no-500 principle; documented in the feature. | [DenyPolicyTestCase](group1/DenyPolicyTestCase.md) | ✔ verified (500 server-bug, not ported) |

## H. Application / consumer-secret extras (enabler: sandbox key-gen; BYO OAuth client)
| Item | Source | Status |
|---|---|---|
| Consumer-secret **regenerate** (rotate) — **SUPERSEDED, do not port.** `regenerate-secret` is the **pre-4.7.0 single-consumer-secret** rotation. Per the official docs (`docs-apim` `generate-api-keys.md`: *"From 4.7.0 onwards, applications support multiple consumer secrets"*; single-secret is the *previous behaviour*, available only by disabling multi-secret mode), 4.7.0 **replaced** single-secret rotate with the **multiple-consumer-secrets** model (`generate-secret` 201 / `secrets` 200 / `revoke-secret` 204). This is exactly why the legacy `ApplicationConsumerSecretRegenerateTestCase` is **orphaned** (not in any `testng.xml`) — the product moved off it. The observed **500** is on this deprecated path in multi-secret-enabled (default) mode; it is NOT a coverage gap. **The rotation capability IS correctly covered** by `keymanager/multiple_client_secrets.feature` (generate→list→revoke, ×2, verified live) — which mirrors the *active* `ApplicationTestCase` secret-management methods. Optional product note: regenerate-secret returning 500 (vs a clean 400/409 "unsupported in multi-secret mode") is a minor robustness nit, not a test to port. | [ApplicationConsumerSecretTestCase](group1/ApplicationConsumerSecretTestCase.md) | ✔ correctly covered via multiple-secrets model (regenerate-secret superseded, not ported) |
| Map application keys (BYO OAuth client) + negative — **DONE (×2).** Ported `ApplicationTestCase#mapApplicationKeys / mapApplicationKeysNegative` as `keymanager/map_application_keys.feature`. The BYO OAuth client is created via the **DCR endpoint** (v2-native analogue of legacy's SOAP `OAuthAdminService`+`ServiceProvider`) → real OAuth2 consumer app in the **resident KM** (no external backend). Positive map→**200** (carries `consumerKey`); negative (generate keys first, then map same key type)→**409 "Key Mappings already exists"**. Verified live: `Tests run: 4, 0 failures` (2 scenarios ×2 tenant). New glue: `I register an OAuth client … as …`, `I map OAuth client … to application … via key manager …`, `Utils.getMapKeysURL`; new `@feat:map-application-keys`; runner `KeyManagerMapApplicationKeysRunner` in the KeyManager block. | [ApplicationConsumerSecretTestCase](group1/ApplicationConsumerSecretTestCase.md) (ApplicationTestCase) | ✔ done (×2) |
| Application **by-id lifecycle** — get / update / delete (`applications.feature` ×2), key-gen (oauth_keys / map-keys / multiple-secrets), subscribe + list-subs-of-app (`subscribe.feature`). **Verified covered.** (Correction: the earlier "groupId create / scope data-file" items were MISATTRIBUTED — no such tests exist in the legacy application package.) | ApplicationTestCase | ✔ covered |
| **Cleanup application registration** — `POST /oauth-keys/{keyMappingId}/clean-up`. **DONE (×2).** Added `Utils.getCleanupRegistrationURL` + `I clean up the key registration for application … with key mapping …` glue + a scenario in `oauth_keys.feature`. Verified live: 200. | ApplicationTestCase#testCleanupApplicationRegistrationById | ✔ done (×2) |
| **Custom application attributes** — **DONE (full, ×2 tenant × 2 token types).** Ported `ApplicationAttributesTestCase` as `devportal/application_attributes.feature`: a custom attribute (`External Reference Id`) declared via `[[apim.devportal.application_attributes]]` is verified **stored on the app** (GET) AND **surfaced in the backend JWT** `http://wso2.org/claims/applicationAttributes` claim. Config overlay `configFiles/applicationAttributes/deployment.toml` (`[apim.jwt] enable=true` + the attribute); a new `/reflect-headers` node backend route reflects the gateway-injected `X-JWT-Assertion` so the claim can be read (v2 analogue of legacy's `jwt_backend` echo); new glue `The reflected backend JWT should contain application attribute … with value …`. Block `IntegrationV2-ApplicationAttributes` (overlay + initBackend); new `@feat:application-attributes`. Verified live: JWT+OAUTH × 2 tenants all green. **Product bug found & documented (not enshrined):** creating an app WITHOUT the required attribute → server logs `GlobalThrowableMapper: Bad Request. Required application attribute not provided` but returns **500 (900967)** instead of 400 (exception mapper mishandles a BadRequest). Legacy never tested this negative, so it is documented here rather than asserted (no-500 principle). | ApplicationAttributesTestCase (2 methods) | ✔ done (×2 tenant × 2 token type) |

## I. Small publisher endpoints — ✅ ALL DONE (I1, I2, I3, I4a, I4c done ×2; I4b documented-500)
| Item | Source | Status |
|---|---|---|
| Empty CORS config shape — **DONE (×2).** CORS-disabled API → GET returns **empty arrays** (`[]`) not null for origins/headers. In `api_config.feature`. Verified live. | [SmallPublisherEndpoints](group1/SmallPublisherEndpoints.md) (CheckEmptyCORSConfigurations) | ✔ done (×2) |
| Validate role of user — HEAD `/roles/{base64url(role)}` — **DONE (×2).** APIM638 is active+green in legacy on 4.7.0 (publisher token → 200). The earlier v2 401 was OUR glue bug: a literal `=` base64 pad in the path segment broke the auth-filter resource match. Fixed via URL-safe base64 **without padding** (`Utils.getValidateRoleURL`) + publisher token. Ports both methods: existing role (`admin`, `Internal/publisher`) → **200**, non-existing → **404**. `scopes.feature`, ×2. Verified live: `Tests run: 8, 0 failures`. Reusable `SimpleHTTPClient.doHead` added. (Lesson: legacy-green ⇒ distrust the v2 401 and reproduce legacy's exact request — principle 4c.) | [SmallPublisherEndpoints](group1/SmallPublisherEndpoints.md) (APIM638) | ✔ done (×2) |
| Thumbnail set → update-without-thumbnail preserves it — **DONE (×2).** Upload a PNG (multipart **PUT** `/apis/{id}/thumbnail` — it's PUT `updateAPIThumbnail`, not POST → 201), then an API update (description) that omits the thumbnail leaves it intact (GET thumbnail → 200). PNG fixture `artifacts/images/thumbnail.png`; new glue `I upload thumbnail …`, `I retrieve the thumbnail …`, extension-preserving temp-file loader, `Utils.getThumbnailURL`. In `api_config.feature`. Verified live. | [SmallPublisherEndpoints](group1/SmallPublisherEndpoints.md) (APIMANAGER5872) | ✔ done (×2) |
| APIM18 **archive-import** — **DONE (I4a) + documented (I4b).** I4a: import an API from `swagger-archive.zip` (contains a remote `$ref` `datasetlist.json`) → **201**, and the resolved swagger contains `dataSetList`. New glue `I import api from archive …` (`.zip`-preserving multipart upload) + fixtures (`swagger/*.zip`, `archive_additional_properties.json`). In `definitions.feature`. **I4b:** the incorrect archive (misnamed master) → **500** `"Error occurred while validating API Definition"` (same as legacy; docs-apim has no error-path spec, no clean 4xx alternative) → documented, not enshrined (no-500 principle). Verified live. | group1 §1 (APIM18) | ✔ I4a done; I4b documented (500) |
| APIM18 **sandbox-only endpoints** + internal-key **keytype=SANDBOX** — **DONE (×2).** Sandbox-only API → generate internal API key → decode the JWT → `keytype` claim is SANDBOX. Payload `create_apim_sandbox_only_api.json`; new glue `The JWT stored as … should contain …`. In `keymanager/api_key.feature`. Verified live + banked. | group1 §1 (APIM18) | ✔ done (×2) |

## J. Prototype — runtime / mock / visibility — ✅ ALL DONE (10/10 ×2 tenant, verified live)
Ported as `publisher/prototype_api.feature` (`GatewayPrototypeApiRunner`, in the Gateway block — initBackend). New `@feat:prototype`; new glue: mock-script generate/retrieve steps + `Utils.getGenerateMockScriptsURL`/`getGeneratedMockScriptsURL`; new payload `create_apim_prototype_api.json`.
| Item | Source | Status |
|---|---|---|
| Gateway invocation of a prototyped API — **CORRECTION: never "keyless".** The legacy `testPrototypedAPIEndpoint` subscribes and invokes **WITH a token → 200**; the earlier "keyless → 401" reading was a misread (keyless was never the contract). Ported faithfully: deploy-as-prototype + implementation_status=prototyped + node backend, subscribe, token, invoke → **200**. Verified live ×2. | [PrototypedAPITestcase](group1/PrototypedAPITestcase.md) | ✔ done (×2) |
| Demote PROTOTYPED → CREATED + invoke → **401** (auth enforced after demote; action "Demote to Created"). Verified live ×2. | [PrototypedAPITestcase](group1/PrototypedAPITestcase.md) | ✔ done (×2) |
| Inline OAS2/OAS3 mock implementation generation — import OAS → deploy-as-prototype → POST `/generate-mock-scripts` (200) → GET `/generated-mock-scripts` (200, contains `/hello`). NOTE: generate (POST) and retrieve (GET) are **two distinct endpoints** (`generate-` vs `generated-`). Verified live: OAS2 + OAS3, ×2 tenant. | [PrototypedAPITestcase](group1/PrototypedAPITestcase.md) | ✔ done (OAS2+OAS3 ×2) |
| Devportal visibility of a prototyped API (APIM23/APIM24) — a deployed prototyped API is visible in the devportal with lifeCycleStatus **PROTOTYPED** (poll-until-contains). Verified live ×2. | [PrototypedAPITestcase](group1/PrototypedAPITestcase.md) | ✔ done (×2) |

## K. Organization visibility — residual (ALL DONE — methods 5–7 + the disallowed-tier negative)
| Item | Source | Status |
|---|---|---|
| Key-manager org visibility | [ConsumerOrganizationVisibilityTestCase](group1/ConsumerOrganizationVisibilityTestCase.md) | ✔ done |
| Cross-org application sharing | [ConsumerOrganizationVisibilityTestCase](group1/ConsumerOrganizationVisibilityTestCase.md) | ✔ done |
| Org-specific subscription policy — tier applied + subscribable | [ConsumerOrganizationVisibilityTestCase](group1/ConsumerOrganizationVisibilityTestCase.md) | ✔ done |
| Org-policy **subscribe-denial** (disallowed tier) — cleanly returns **403** ("Tier X is not allowed … Only [Bronze] Tiers are allowed"), matching legacy. The earlier "500 `900967`" was a **test artifact** (unresolved `{{placeholder}}` in the probe payload → server 500 on a garbage API id), NOT product behaviour. Re-verified live and **ported** into method 7. | [ConsumerOrganizationVisibilityTestCase](group1/ConsumerOrganizationVisibilityTestCase.md) | ✔ done |

## L. Wave A standalone tail — ✅ RESOLVED (L1/L4/L5 done ×2; L3 folded into H3; L2 correctly skipped)
All five triaged & closed. **Lesson (see memory): every one of my initial negative dispositions here was wrong except L2** — L1 "no name-search" (there IS a `name` param), L5 "redundant" (distinct feature), L3 "dup" (had an update delta) — re-verification flipped them. Only L2 (deprecated v0.16 endpoint, absent from v4) survived scrutiny.
| Item | Source | Status | Why not "easy" |
|---|---|---|---|
| Admin app-search by name/owner — **DONE.** (Earlier "parked" was WRONG on both premises: the v4 admin `/applications` endpoint DOES have a `name` param — I'd stopped reading the spec at `user`/`limit`/`offset` — and the class is active+green in legacy `testng.xml:330`; the earlier owner-`count:0` was a subscriberUser owner-string setup issue.) Ported `admin/application_search.feature` (`AdminApplicationSearchRunner`): admin searches an owned app by **name** (`?name=`) → 200 contains it, and by **owner** (`?user=`) → 200 contains it. New glue: `Utils.getAdminApplicationsByNameURL`/`ByOwnerURL`, `iSearchAdminApplicationsByName`/`ByOwner`, `@feat:admin/application-management`. Verified live: `Tests run: 1, 0 failures`. | ApplicationsSearchByNameOrOwnerTestCase | ✔ done |
| ApplicationScopeValidation — get application scope via token | ApplicationScopeValidationTestCase | ⏭️ **SKIP (user-approved)** | Disabled in legacy; uses the **deprecated v0.16** `/applications/scopes/{id}` endpoint (likely not in v4) — verify-first would confirm it's gone. Not ported. |
| ApplicationWithCustomAttributes — app with custom attributes | ApplicationWithCustomAttributesTestCase | ⏭️ **SKIP (user-approved) — ~duplicate of H3** | Disabled in legacy; ~duplicate of the H3 `devportal/application_attributes.feature` (create + enforce + JWT-claim). The only delta is attribute **update-mutation** → covered by adding ONE update scenario to that feature (see L3 task) rather than a full port. |
| MandatoryPropertiesTestWithRestart — mandatory custom-property enforcement — **DONE (×2).** TOML overlay `[[apim.publisher.custom_properties]] required=true` (own `IntegrationV2-MandatoryProperties` block); update with the required `additionalPropertiesMap` empty → **400**, with a value → **200**. In `publisher/mandatory_properties.feature`. Verified live + banked. | MandatoryPropertiesTestWithRestart | ✔ done (×2) |
| PluggableVersioningStrategy — **DONE (×2).** Ported into `gateway/rest_invocation.feature` (`@rule:version-first`): an API whose context uses a **version-first template** (`{version}/<ctx>`, matching legacy's `API_CONTEXT="{version}/api"`) deploys to `/{version}/<ctx>` and is invocable there (version FIRST, not appended) → 200. **verify-first resolved: no server config needed** — it's a standard 4.7.0 context-template feature. New payload `create_apim_version_first_api.json`; new glue `I replace … in context …` (the publisher returns the `{version}` template verbatim, so the invoke URL substitutes `{version}`→`1.0.0`). Verified live: `Tests run: 6, 0 failures` (×2 tenant incl. `/t/tenant1.com/1.0.0/<ctx>`). | PluggableVersioningStrategyTestCase | ✔ done (×2) |

**Dropped (not deferred):** `DocAPIParameterTampering` — ⚪ hollow garbage-input negative (tampered doc id → 401), consistent with the family-wide garbage-UUID skip.

## F. Misc
| Item | Source | Status |
|---|---|---|
| API context-mismatch across versions — same-name + different-context create → **400** (verified 4.7.0). ✔ ported into api_lifecycle.feature (×2). | [CreateValidationMatrix](group1/CreateValidationMatrix.md) | ✔ done |
| **URI-template encoding at the gateway** — ✔ **DONE** (faithful port). Ported into `rest_invocation.feature` (×2) with legacy's EXACT APIM shape: **uri-template resource `/{val}` + a `{uri.var.val}` templated endpoint** (`.../echo/sub{uri.var.val}`) → the gateway substitutes the path var AND appends the postfix, giving the doubled backend path `/echo/sub<val>/<val>` (exactly what legacy's bespoke Synapse backend hardcoded). Backend is a wildcard node route `GET /echo/*` (replaces legacy's hardcoded Synapse API). The encoded segment is sent via a **new raw-path invoke** — `SimpleHTTPClient.doGetRaw` (`RequestConfig.setNormalizeUri(false)`) + step `I invoke the API at raw gateway context … using access token … until …` — so `%28`/`%29` reach the gateway verbatim. Result: 200, backend receives `…/echo/subS2222-0496%2815%2927436-0/S2222-0496%2815%2927436-0` (encoding PRESERVED). **NO regression** — the gateway routes the encoded path fine with the legacy uri-template resource; the enabler is the uri-var endpoint, NOT a wildcard APIM resource. (My earlier `/*`-resource version was a divergent workaround — it made the framework's *decoding* client "pass" by matching literal parens — and was replaced.) The framework gap it exposed & fixed: the default invoke's Apache HttpClient decoded `%28` and collapsed `//`, so it could not send a raw-encoded path. **CI caveat:** the `/echo/*` route is in `node-customer-service/routes/customerRoutes.js` (source); the local node image was updated via an offline `docker build` overlay (FROM node-app-server:latest + COPY the one file) because a full image rebuild runs `npm install` which fails without network — CI rebuilds from source. | APIResourceWithTemplateTestCase #3 | ✔ done |

## M. Blocked by an upstream product regression (enabler: sync branch with upstream master)
Correct tests that fail on this branch's carbon-apimgt version because of a product bug already fixed upstream.
Do NOT work around these in the test (that would hide the regression) — they are parked until the branch is
synced with upstream master, then re-enabled and verified.
| Item | Source | Status |
|---|---|---|
| **Sync branch with upstream master (pull carbon-apimgt ≥ 9.33.147)** — enabler for the row below; general currency. | — | ⬜ pending |
| **MCP PROXY backend-endpoint update** — a correct update PUTs the backend back in full incl. its `definition`; for a proxy MCP server the definition is MCP-tools JSON, but this branch's carbon-apimgt wrongly re-validates it as OpenAPI → `900754 "attribute tools is unexpected"` (HTTP 400). Fixed upstream in **carbon-apimgt 9.33.147**. Scenario "Manage the backend endpoint of a proxied MCP server" is **commented out** in `gateway/mcp_proxy_invocation.feature`; re-enable after the sync. (from-OpenAPI backend-CRUD is DONE ×2 — unaffected, its definition is valid OpenAPI.) | `MCPServerTestCase` / `gateway/mcp_proxy_invocation` | ⬜ blocked (needs sync) |

## N. PARKED — NOT COVERED (needs external infrastructure the v2 harness does not provide)
These Wave C items are **consciously parked and NOT covered** in the v2 suite. They are not port gaps we can
close with more test code — they each require a **real WSO2 Identity Server (IS) container on the test network**
(the testcontainers harness runs only the APIM container + the node backend; there is no IS). A fake/stub KM with
unreachable endpoints does not substitute: it never becomes a usable key manager, so key generation 500s before
the behaviour under test is ever reached (proven — see §B). Recorded here so it is unambiguous that these were
**left uncovered on purpose**, with the exact enabler and re-enable criteria.

**Shared enabler for both:** stand up a real WSO2 IS container on `ContainerNetwork.SHARED_NETWORK` (like the node
backend), register it as a key manager / OAuth provider, and point the APIM container at it. Once that exists,
both items below become buildable (headless — no browser needed for the grant endpoints). Until then: PARKED.

| # | Item | Why not covered | Re-enable when |
|---|---|---|---|
| 8 | **Authorization-code / implicit grant** (`GrantTypeTokenGenerateTestCase`) | Needs a real reachable WSO2 IS serving `/authorize` + login + consent + `/token`. The flow itself is headless-drivable (HTTP, no browser), but there is **no IS container** in the v2 network to serve those endpoints. NOTE: the API-driven halves of this class (corrupted-credentials negative, no-callback create) are portable now and tracked separately; only the **authcode/implicit grant** legs are parked. | A real WSO2 IS container is added to the harness network. |
| 9 | **Key-manager deny-role key-gen 403** (`KeyManagersTestCase.testKeyManagerPermissions`) | Needs a real IS/KM that **issues keys AND enforces DENY roles**. Verified (§B): a created fake-endpoint `wso2is` KM never becomes a usable devportal key manager (the devportal lists only Resident), so key generation via it **500s before the permission-403** is reached. Glue was built and kept (KM-create-denying-role step, generate-keys 2xx-guard) — only the backend is missing. | A real IS/KM backend (reachable, key-issuing, role-enforcing) is available. |

**Also parked (different reason — for completeness of the "not covered" record):**
- **Remote-log-sink** (Wave C #6): needs a Carbon SOAP admin client + in-container `log4j2.properties`
  inspection (no REST API). Harness gap, not an IS dependency. Left uncovered.
- **MCP proxy backend-endpoint update** (§M): parked on an upstream product regression, not infra.
- **MCP-Hub mode**: niche config/restart mode, deprioritised.
- **WebSocket / streaming THROTTLING** (`WebSocketAPITestCase` throttling): PARKED — verify-first finding, not a
  test bug. WS throttling did NOT trip in the single-container v2 harness: **neither** the legacy API-level
  advanced request-count policy **nor** a subscription **event-count (async)** Business Plan throttled WS frames,
  even with **spaced** sends over ~30s (20 messages against an 8-event/min limit — ALL echoed, both mechanisms).
  REST request-count throttling works in the SAME harness (traffic manager / `_throttle_out_handler_` active), so
  the TM is up for REST but the **WS frame→event flow to the throttle stream is not enforced** in the all-in-one
  profile (likely needs a proper Traffic Manager / binary-throttle-event setup the embedded profile doesn't wire
  for streaming). **CONFIRMED BY A MANUAL STANDALONE TEST (not just the harness):** booted the `wso2am` all-in-one
  image directly via `docker run` (default config, NO `basic` overlay) + the node WS backend, created a WS API
  with an `EVENTCOUNTLIMIT` subscription plan (8 events/min), subscribed, and drove 20 spaced WS messages — **all
  20 echoed, no throttle**. As a control on the SAME standalone, a REST API on a 3-req/min plan throttled
  correctly (calls 1-3 → 200, calls 4+ → **429**). So the throttle engine works; **WS event/frame throttling
  specifically does not enforce on the all-in-one profile** — it is NOT a testcontainer artifact. **DECISION —
  PARK ACCEPTED:** not pursued further in this effort. The all-in-one profile (which the v2 harness uses) does not
  enforce WS throttling, so it is not testable here; the ONLY thing that could change this verdict is a
  full **distributed deployment with a real Traffic Manager** (the `wso2am-tm` image) — deliberately deferred as a
  separate investigation, out of scope for the single-container v2 suite. Scenario is **commented out**
  in `gateway/websocket_invocation.feature`; glue kept (the
  event-count subscription-policy step `…allowing N events per minute` and the WS multi-frame throttle-detection
  step `…sending N messages … expecting throttling …`). Re-enable when the WS throttle event flow is available
  (or a real TM node is added). Do NOT force it green.
### Distributed tracing / telemetry (CROSS-CUTTING — all API types) — PARKED
Tracing is **not a WebSocket concern** — it is a gateway-wide telemetry feature (the `open_tracing` Synapse
handler, `synapse_handlers.open_tracing.*` in `default.json`, class `APIMgtLatencySynapseHandler`) that applies to
**every API type** (REST, WebSocket, GraphQL, SOAP, …). It belongs in **one consolidated Tracing coverage area**,
not under any single protocol's tail.

- **Legacy state:** the ONLY legacy tracing touchpoint is `WebSocketAPIInvocationWithTracingTestCase` — and it is
  **assertion-free**: it enables a **tracing (telemetry) deployment.toml overlay** and then just re-runs the
  ordinary WS token invoke (header + query), asserting only that the echo still returns. It **never verifies a
  trace/span was produced**. There is no REST/GraphQL/SOAP tracing test. So porting the WS one faithfully would
  spin up a **dedicated container with a costly gateway-wide TOML overlay** (its own block) for **zero
  verification value beyond the core invoke we already cover ×2**. **Parked — do NOT port as-is.**
- **How to cover it properly (re-enable criteria):** build **one** tracing test area, not per-protocol — enable
  tracing/OpenTelemetry, export spans to an **inspectable sink** (an in-network Jaeger / OTel-collector container,
  or a file/log exporter), invoke an API of each relevant type through the gateway, and **assert the expected
  spans/trace are emitted** (operation name, API context, upgrade for WS, etc.). Only then does the overlay pay
  for itself. Until a span-inspection sink + real assertions exist: **left uncovered on purpose**, for ALL API
  types. (Same principle as [[feedback_suspicious_scenario_flag_dont_tweak]] applied to coverage value — don't
  enshrine an assertion-free legacy test just to claim parity.)

---

**When to drain this:** after Wave A's remaining first-increments land (so the enablers — role-scoped users,
policy-attached APIs, subscriptions — are built once and reused), sweep by enabler group B→E. Update the
Status column and the source report as each lands. Sections M and N stay parked until their external enabler
(upstream sync / a real IS container) is available.
