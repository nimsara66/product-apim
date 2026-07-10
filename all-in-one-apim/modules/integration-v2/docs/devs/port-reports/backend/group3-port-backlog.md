# Group3 port — live backlog (RE-ANALYZED 2026-07-09 against group4-inclusive coverage)

Branch **`test-framework-v2_backend-port-G3`**, cut off the **group4 branch tip** — inherits all 49 group4 commits +
harnesses (node routes /reflect-headers|/reflect-body|/echo, node-soap WSDL, sequence-backend, Location routes,
op-policy attach + export/import, gateway:cors, SOAP admin harnesses in TenantUserProvisioner
[setUserClaimValue, addOidcExternalClaim, updateOidcScopeClaims, SP requested-claims round-trip], api-key/JWT/
backend-JWT harnesses, mutual-SSL, token-scope additions, ResourceCleanup.deregister).

> **IMPORTANT:** the original `group3.md` map (mapping branch) predates the group4 port, so its dispositions were
> STALE. This backlog is the RE-ANALYSIS against CURRENT (group4-inclusive) coverage — three read-only Explore
> passes (one per legacy block). Use `group3.md` + `group3/*.md` only for "what each class tests"; use THIS file
> for the current disposition. Cadence = group4's (verify-first, ×2 tenant, dedup, focused runs w/ `<listeners>`,
> commit per increment, no full-suite without consent, Copyright (c) 2026).

## Current disposition (re-analyzed)
| Disposition | ~Count | Meaning |
|---|---|---|
| ✅ COVERED now (dedup, no work) | ~15 | group4/group1 already landed it |
| 🟡 PARTIAL (extend a small delta) | ~24 | core covered; a specific sub-assertion missing |
| 🟢 TO-PORT (genuinely new) | ~25 | no v2 coverage |
| 🏗 INFRA-BLOCKED | ~7 | needs infra v2 lacks even after group4 |
| ⚪ SKIP | ~3 | dup / out-of-group |

**Biggest change vs the stale map:** COVERED jumped 6→~15 — group4 landed token-issuance (JWT/OpenID/refresh/
sandbox), backend-JWT + app-attributes, multiple-client-secrets, docs, app CRUD, versioning, lifecycle-stage
invocation (BLOCKED→503 / DEPRECATED→200 / RETIRED→404). So JWTGrant, TokenAPI, OpenIDToken, ApplicationTestCase,
MultipleClientSecrets, ApplicationAttributes, APIM678, APIM714, CopyNewVersion, NewCopyWithDefaultVersion,
AccessibilityOfBlock/Retire, DevPortalSearch are **now COVERED** (were PORT/EXTEND). Several stale NEW-HARNESS
items are still blocked (endpoint-cert, secondary-userstore).

## ✅ COVERED now — dedup, DO NOT re-port
JWTGrantTestCase, TokenAPITestCase, OpenIDTokenAPITestCase, ApplicationTestCase, MultipleClientSecretsTokenTestCase,
ApplicationAttributesTestCase, APIM678ApplicationCreationTestCase, APIM714GetAllDocumentationTestCase,
CopyNewVersionTestCase, NewCopyWithDefaultVersion, AccessibilityOfBlockAPITestCase, AccessibilityOfRetireAPITestCase,
APIMANAGER3965TestCase, DevPortalSearchTest, APIPublishingAndVisibilityInStoreTestCase (visibility-on-publish arc).

## 🟢 TO-PORT — genuinely new (priority queue)
**A. Visibility family (BIGGEST new chunk — ZERO v2 coverage; org-visibility B2B is a DIFFERENT axis, confirmed):**
- [ ] APIVisibilityByRoleTestCase (RESTRICTED-by-role matrix, x2)
- [ ] APIVisibilityByPublicTestCase (PUBLIC + domain restriction)
- [ ] APIVisibilityByDomainTestCase (PRIVATE/domain-scoped)
- [ ] PublisherAccessControlTestCase (accessControl RESTRICTED -> 403)
- [ ] APIVisibilityWithDirectURLTestCase (restricted direct-URL: role->200 / anon->404)
- [ ] APITagVisibilityByRoleTestCase (restricted API's tag hidden from anon)
- [ ] DevPortalVisibilityTestCase (devportal role-gated API/doc/swagger access)
  -> new `publisher/visibility.feature` (+ capability-map `publisher:visibility` if needed). Provision role-scoped
     users (TenantUserProvisioner). DEDUP each row vs admin/organization_visibility (org != role/public/domain).
**B. Comments (ZERO coverage):**
- [ ] DevPortalCommentTest + PublisherCommentTest (consolidate) -> new `devportal/comments.feature`
**C. Gateway / security:**
- [ ] APIResourceWithSpecialCharactersInvocation (`,-._~` resource -> 200) -> gateway/rest_invocation (reuse /echo)
- [ ] ChangeAuthTypeOfResourceTestCase (per-resource auth-type matrix App&User/App/User/None)
- [ ] AudienceValidationTestCase (audience claim mismatch -> 403 900914)
- [ ] AllowedScopesTestCase (scope whitelist -> 200/403; PORT+VERIFY the overlay on 4.7.0)
**D. Store / admin plane:**
- [ ] LoadBalancedEndPointTestCase (round-robin prod/sandbox routing)
- [ ] ChangeApiProviderTestCase (provider change; SOAP/REST/GraphQL)
- [ ] OAuthApplicationOwnerUpdateTestCase (ownership transfer + negatives)
- [ ] APIMANAGER4373BrokenAPIInStoreTestCase (broken-API store visibility on role change)
- [ ] APIMANAGER5326CustomStatusMsgTestCase (custom error status message)
- [ ] SubscriptionValidationDisableTestCase (invoke w/o subscription when validation disabled - config overlay)
- [ ] PkceEnabledApplicationTestCase (PKCE authcode) - VERIFY not infra-blocked (needs authcode flow)
- [ ] CAPIMGT12CallBackURLOverwriteTestCase, APIM720GetAllEndPointsTestCase, ServiceCatalogRestAPITestCase,
      GatewayRestAPITestCase (verify-first: still a product REST API?)
**E. Discovery / misc:**
- [ ] ContentSearchTestCase (content search by description/doc-content + ACL) - delta over search.feature
- [ ] UsersAndDocsInAPIOverviewTestCase (multi-user subscription count)

## 🟡 PARTIAL — extend a small delta (lower priority; core already covered)
Accessibility{Deprecated,WithoutReSub,WithReSub,PublishedOldAndCopy} (deprecate-on-publish / re-sub-403 /
both-visible deltas), NewVersionUpdate (multi-version-latest count), DynamicAPIContext (search-by-templated-context),
CORSHeaders (preflight OPTIONS + swagger x-wso2-cors), CORSBackendTrafficRoute (backend CORS passthrough),
APIResourceModification (per-op auth-type+tier), TagsRating (rating add/delete), APIScope (REST scope-gated invoke),
EditAPIAndCheckUpdatedInformation + EditAPIContextAndCheckAccessibility (routing-follows-context), UpdateAPINullPointer
(null securityScheme/endpoint -> 200), APISecurity (mSSL+OAuth optional/mandatory combo residual),
APIInvocationWithSimilarResourcesAndDifferentVerbs, APIM710AllSubscriptionsByApplication (query-by-app/api),
SameVersionAPI, ConsumerAppBasedJWTRevocation (revoke-by-app-deletion cascade), ErrorResponseCheck (error body shape),
APIEndpointCertificateUsage (usage query + pagination - cert-upload harness exists).

## 🏗 INFRA-BLOCKED
AddNewHandlerAndInvoke (Synapse handler injection + log sink), APIEndpointCertificate (HTTPS backend + endpoint-cert
SSL-reload), SecondaryUserStoreCaseInsensitive + ChangeApiProviderSecondaryUserStore (real secondary userstore),
APIMANAGER5327KeyGenerationWithPGSQL (PostgreSQL container), APIMANAGER5417PrototypedAPIsInMonetized (monetization o.o.s),
APIMANAGER4731StoreStatisticsWhenTokenEncrypted (token-encryption at-rest; class not found in legacy - verify),
SDKGeneration (SDK-gen tooling - low value).

## ⚪ SKIP
ChangeApplicationTierAndTestInvoking (dup of throttling enforcement), ChangeEndPointSecurityOfAPI (group2
endpoint-security), AllowedScopesTestWithCorsDisabled (dup of AllowedScopes; CORS toggle orthogonal).

## Verify-first flags before porting
- DefaultEndpointTestCase lives in `tests/sequence/` not lifecycle - inspect before disposition.
- Visibility family: probe actual store/publisher visibility codes on 4.7.0 (role vs anon vs other-domain) - don't
  trust legacy 403/404 blindly (principle 4a).
- GatewayRestAPI / ServiceCatalog: confirm the REST API still exists in 4.7.0 before porting.

## Progress
(none yet - re-analysis complete; ready to start porting from the TO-PORT queue, visibility family first)
