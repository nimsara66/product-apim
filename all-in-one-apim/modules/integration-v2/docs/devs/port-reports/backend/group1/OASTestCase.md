# Port report — `OASTestCase` (group1, publisher — definitions)

Legacy: `.../tests/oas/OASTestCase.java` — Factory ×2 oasVersion (v2 + v3). 9 `@Test`.
Delivered: extended `publisher/definitions.feature` (`PublisherDefinitionsRunner`, Publisher block — **no new runner**), `@cap:publisher @feat:definitions`. **Verified** (6 new scenario definitions ×2 tenant; full definitions runner 20/20).

## Gap closed
v2 `definitions.feature` already covered create-via-import + publish (OAS 2/3/3.1). Added the six uncovered OAS operations, all ×2 tenant (super + tenant):
| Scenario | Legacy method |
|---|---|
| Update an API's OpenAPI definition (PUT /swagger) → 200, retrieve reflects it | testAPIDefinitionUpdate |
| Advance endpoint configs applied via a definition update → API endpointConfig contains `circuitBreakers` | testAddAdvanceConfigsToAPIDefinition |
| Unsupported OpenAPI `servers` blocks stripped on import → retrieved swagger does **not** contain the unsupported URL | testAPIDefinitionWithUnsupportedServerBlocksImport |
| Validate an invalid definition (empty resource paths) → 200, `"isValid":false` | testValidateAPIDefinitionWithEmptyResourcePath |
| Import an invalid definition → **400** | testAPIDefinitionImportWithEmptyResourcePath |
| Update with an invalid definition → **400** | testAPIDefinitionUpdateWithEmptyResourcePath |

testNewAPI / testAPIUpdate / testAPIDefinitionImport (create + import+publish) were already covered by the existing import scenario.

## New glue + fixtures
- Steps (`PublisherBaseSteps`): `I update the swagger of {resourceType} resource {id} from file {file}` (multipart, field `apiDefinition`), `I retrieve the swagger of {resourceType} resource {id}`, `I validate the openapi definition from file {file}` (POST /validate-openapi, multipart `file`), `I attempt to import openapi definition from {file} with additional properties from {file}` (non-asserting, for the 400 import).
- `Utils.getValidateOpenAPIURL`. Reused `getSwaggerURL`, `getAPIDefinitionURL`, and the existing `OAS3AdditionalProperties.json` (`${UNIQUE}` name/context) for imports.
- Fixtures copied to `artifacts/payloads/OAS/`: `oas_v3_update_definition.json`, `oas_v3_advance_configs.json`, `oas_v3_unsupported_servers.json`, `oas_v3_invalid.json`, `oas_v3_invalid_update.json` (v3 set).

## Finding (verify-first)
- **Swagger update is `multipart/form-data`, not form-urlencoded.** A form-urlencoded PUT returned **415**; switching the `apiDefinition` field to a multipart text field fixed all update scenarios. (Caught by running — the generated client declares `multipart/form-data` for the swagger PUT.)

## Decisions
- **v3 fixtures** used throughout (the unsupported-servers case is v3-only; the others are version-agnostic in behaviour). The legacy v2 oasVersion variant is deferred — trivial to add a v2 fixture set if wanted.

## Net
The six OAS definition operations (update, advance-configs, unsupported-server stripping, and the invalid validate/import/update trio) ported ×2 tenant, reusing the definitions runner. Swagger-update media-type gotcha resolved.
