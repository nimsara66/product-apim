# Port report — `ApplicationTestCase` consumer-secret delta (group1, key-manager)

Legacy: `.../tests/application/ApplicationTestCase.java` (18 methods; the 10 consumer-secret / multiple-client-secret methods). Factory ×2.
Delivered: `keymanager/multiple_client_secrets.feature` (`KeyManagerMultipleClientSecretsRunner`, standard **IntegrationV2-KeyManager** block), `@cap:key-manager @feat:multiple-client-secrets`. **Verified 4/4** (2 scenario definitions ×2 tenant).

## Config — NO overlay needed (correction)
Initial assumption was that multiple-client-secrets mode is off by default (the legacy tests skip on **900916**), so a `[oauth.multiple_client_secrets] enable = true` overlay was added in a dedicated block. **This was verified wrong:** running the feature on the **default pack with no overlay passed 4/4**, and the distribution `conf/default.json` has `"oauth.multiple_client_secrets.enable": true` (enabled by default in 4.7.0). The overlay + dedicated block were removed; the runner folded into the standard KeyManager block. (The legacy 900916-skip only triggers on a runtime where the mode is disabled — not the 4.7.0 default.) Lesson: an overlay claim must be verified by running the DEFAULT pack without it, not just inferred from legacy skip logic.

## Method dispositions
| Legacy method(s) | Disposition | Where / note |
|---|---|---|
| testFetchKeyDetailsByKeyMappingID | ✅ ported | fetch key details by key-mapping id → 200, contains `consumerKey` |
| testGenerateConsumerSecretForKeyMappingId | ✅ ported | generate secret → **201**, contains `secretValue` |
| testGetConsumerSecretsForKeyMappingId | ✅ ported | list secrets → contains the generated `secretId` |
| testRevokeConsumerSecretForKeyMappingId | ✅ ported | revoke → **204**; list no longer contains it. (A helper secret is generated first — the IS refuses to revoke the most-recently-added secret.) |
| testGenerateMultipleSecretsForSameKeyMapping / testSecretListCountMatchesGenerated | ✅ ported | two secrets (target + helper) generated and listed in the lifecycle scenario |
| testGenerateSecretWithMinimalPayload | ✅ ported | generate with empty additionalProperties → 201, `secretValue` |
| testGenerateSecretAdditionalPropertiesReturnedInList | 🟡 partial | the description additional-property is sent on generate; the list-reflects-description assertion folds into the list check (not separately asserted) |
| testGenerateSecretForSandboxKeyMapping | ⏭️ increment 2 | secret CRUD on a SANDBOX key mapping (needs a sandbox key-gen + its keyMappingId captured) |

The non-secret parts of the legacy class (get/update/key-gen/subscription **by application id**, map-keys BYO OAuth, groupId) remain §12 EXTEND items tracked separately.

## Findings (verify-first)
- **No overlay required** — multiple-client-secrets is enabled by default in 4.7.0 (`default.json` + a no-overlay run passing 4/4). Runs in the standard KeyManager block.
- **Generate → 201, revoke → 204** (No Content) — observed live; the revoke assertion was corrected from 200 to 204.

## New glue
Steps (`ApplicationBaseSteps`, devportal): `I generate a consumer secret with description … for application … with key mapping … as …` (non-asserting, stores secretId on 2xx), `I retrieve the consumer secrets for application … with key mapping …`, `I revoke the consumer secret … for application … with key mapping …`, `I fetch the oauth key details for application … with key mapping …`. The consumer-secret `Utils` URLs already existed (scaffolded); this is their first use.

## Net
Consumer-secret (multiple-client-secrets) generate / list / revoke / fetch-key-details + minimal-payload ported ×2 tenant in the standard KeyManager block (no overlay — enabled by default). Sandbox-key-mapping secret CRUD → increment 2; the broader ApplicationTestCase by-id / map-keys / groupId deltas remain §12.
