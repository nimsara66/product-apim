# Port report — `APIProductCreationTestCase` (group1)

Legacy source: `.../tests/apiproduct/APIProductCreationTestCase.java` — Factory `SUPER_TENANT_ADMIN` + `TENANT_ADMIN` (×2). 12 active `@Test` + 1 commented.

Delivered v2: `publisher/api_products.feature` (`PublisherApiProductsRunner`, Publisher block) +
`gateway/api_product_invocation.feature` (`GatewayApiProductInvocationRunner`, Gateway block). `@cap:publisher
@feat:products` / `@cap:gateway @feat:rest-invocation`. **Verified in isolation: BUILD SUCCESS 14/14** (with
`APIProductRevisionTestCase` + `APIProductLifecycleTest`). New glue in `PublisherBaseSteps`: create-product-from-API,
attempt-create (negative), new-version, get-swagger, resource-typed change-lifecycle; `CREATED_API_PRODUCT_IDS`
cleanup (products swept **before** their APIs). Revision/deploy/publish/retrieve/invoke steps reused via
`resourceType="api-products"`.

## Method dispositions
| # | Method | Disposition | Where |
|---|--------|-------------|-------|
| 1 | testCreateAndInvokeApiProduct | ✅ **ported** | `api_products` (create + retrieve echoes apiId) + `api_product_invocation` (invoke → 200, ×2) |
| 2 | testAPIProductNewVersionCreation | ✅ ported | `api_products` (new version 2.0.0, ×2) |
| 3 | testAPIProductNewVersionCreationWithDefaultVersion | ✅ ported | `api_products` (new version as default; reflect isDefaultVersion=true) |
| 4 | testCreateApiProductWithMalformedContext | ✅ ported | `api_products` (attempt malformed context → 400) |
| 12 | testAPIProductSwaggerDefinition | ✅ ported | `api_products` (retrieve product swagger; contains `/customers/{id}`) |
| 13 | testUpdateUnderlyingAPIofAPIProduct | ✅ ported | `api_products` (update underlying API → product still references it + operations, ×2) |
| 5 | testCreateAndInvokeApiProductWithVisibilityRestrictedApi | ⏭️ **deferred → increment 2** | needs a visibility-restricted API + restricted role/user |
| 7 | testCreateAndInvokeApiProductWithScopes | ⏭️ deferred → increment 2 | product with scopes + scoped token (v2 has scope machinery) |
| 8 | testCreateAndInvokeApiProductWithOperationPoliciesInRequestApi | ⏭️ deferred → increment 2 | product with request op-policy |
| 9 | testCreateAndInvokeApiProductWithOperationPoliciesInResponseApi | ⏭️ deferred → increment 2 | product with response op-policy |
| 10 | testCreateApiProductWithAdvertiseOnlyApi | ⏭️ deferred → increment 2 | product over an advertise-only API |
| 11 | testCreateAndDeployApiProductWithMutualSSLEnabled | 🏗 **Wave C** | needs mutual-SSL client-cert harness |
| 6 | testCreateAndInvokeApiProductWithAPICategoryAdded | ⚪ **skip** | `@Test` is **commented out / disabled** in the legacy |

## Net
Core API-Product creation, versioning (+default), malformed-context, swagger, underlying-API tracking, and
gateway invocation are ported ×2 and verified. The setup-dependent variants (scopes / op-policies /
visibility-restricted / advertise-only) are **increment 2**; mutual-SSL is **Wave C**; the category test is
skipped (disabled in legacy).
