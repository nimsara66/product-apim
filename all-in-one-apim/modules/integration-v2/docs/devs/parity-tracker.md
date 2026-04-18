# Integration V2 Parity Tracker

See the execution roadmap in [Coverage Execution Plan](coverage-execution-plan.md).
See the frozen baseline in [Coverage Baseline Snapshot](coverage-baseline-snapshot.md).

## Phase 0 Metadata

- Last updated: 2026-04-03
- Last baseline snapshot: 2026-04-03
- Scope: Domain-level parity tracking for integration-v2 against legacy integration
- Evidence policy: Every non-planned domain must carry at least one feature path and one CI/report reference

## Status Legend

- `done`: domain is fully covered in v2 for supported scope
- `in-progress`: migration has started but legacy still carries meaningful coverage
- `planned`: domain is acknowledged but not yet implemented in v2
- `review`: waiting on validation, CI enablement, or legacy retirement decision

## Coverage Domains

| Domain | Legacy Coverage | V2 Coverage | Status | Owner | Target Release | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Common bootstrap and tenant setup | Yes | Yes | in-progress | Framework team | 4.7.0 | features/common, local `SystemInitializationRunner` Maven verification on 2026-04-03 with Colima Docker env vars | System initialization now runs locally when `DOCKER_HOST` is set to Colima socket; keep stabilizing shared primitives for all lanes |
| Publisher core flows | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher, testng suite report | Present in v2, needs broader CRUD/subscription parity |
| Application management | Yes | Partial | in-progress | Application domain owner | 4.7.0 | features/publisher/create_new_application.feature | Present in v2, includes key generation and subscription block/unblock coverage |
| API invocation/runtime basics | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/publisher/api_key_invocation.feature, features/publisher/invalid_token_invocation.feature | Needs broader gateway behavior and edge-case parity |
| OAS import and API definition | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher/import_OAS_definition.feature, features/migration/migrated_api_definition.feature | Present in publisher and migration scenarios |
| Documents | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher/api_documents.feature, features/migration/migrated_api_documentation.feature | Needs full legacy scope comparison |
| API versioning and revisions | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher/api_versioning.feature, features/migration/migrated_api_revisioning.feature | Needs restart and persistence parity |
| Migration validation | Yes | Yes | in-progress | Migration domain owner | 4.7.0 | features/migration, migration nightly report | Strong v2 area, completeness review pending |
| Header/custom auth behavior | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/header/custom_authorization_header.feature | Present for custom auth header slice |
| Search and visibility | Yes | Partial | in-progress | Publisher domain owner | 4.8.0 | features/migration/migrated_api_search.feature, features/publisher/devportal_search_visibility.feature, local parity-baseline suite run on 2026-04-03 | Executed in parity baseline suite; current failure is `409 API already exists` due shared payload/name collision |
| API products | Yes | Partial | in-progress | Publisher domain owner | 4.8.0 | features/migration/migrated_api_product.feature | Broader parity pending |
| Shared scopes and policies | Yes | Partial | in-progress | Governance domain owner | 4.8.0 | features/migration/migrated_shared_scopes.feature, features/migration/api_policies.feature | Admin/runtime parity pending |
| Tokens and OAuth flows | Yes | Yes | in-progress | Runtime domain owner | 4.7.0 | features/publisher/refresh_token.feature, features/publisher/revoke_token.feature, features/publisher/sandbox_token.feature, features/publisher/openid_token.feature, features/publisher/invalid_token_invocation.feature, features/publisher/subscription_blocking.feature, local restart profile run on 2026-04-03 | Core OAuth/token flows are implemented and locally verified; JWT/external IDP scope still tracked separately |
| API keys and key validation | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/migration/api_key.feature, features/publisher/api_key_invocation.feature | Broader policy and restriction parity pending |
| JWT and external IDP | Yes | Partial | in-progress | Security domain owner | 4.8.0 | features/publisher/jwt_token_format.feature, features/publisher/openid_token.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | JWT token-format baseline executed successfully in local parity suite; external IDP parity remains pending |
| Throttling | Yes | Partial | in-progress | Governance domain owner | 4.8.0 | features/publisher/subscription_throttling_policy.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | Executed in parity baseline suite; current failure is `400 Bad Request` (`Request must contain status of the subscription`); API/JWT throttling breadth still pending |
| GraphQL | Yes | Partial | in-progress | API types domain owner | 4.8.0 | features/publisher/graphql_api_baseline.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | Executed in parity baseline suite; current failure is `409 API already exists` due shared payload/name collision; broader parity pending |
| WebSocket and streaming APIs | Yes | Partial | in-progress | API types domain owner | 4.8.0 | features/publisher/websocket_api_baseline.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | WebSocket create/publish baseline executed successfully in parity suite; SSE/WebSub/async parity pending |
| Governance and operation policies | Yes | Partial | in-progress | Governance domain owner | 4.8.0 | features/publisher/governance_policy_baseline.feature, features/migration/api_policies.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | Executed in parity baseline suite; current failure is `409 API already exists` due shared payload/name collision; broader enforcement parity pending |
| Workflows | Yes | No | planned | Workflow domain owner | 4.8.0 | legacy reference only | API/app/subscription workflow approvals |
| Analytics and logging | Yes | No | planned | Observability domain owner | 4.8.0 | legacy reference only | Correlation logs, API logs, ELK analytics |
| Admin and organization features | Yes | No | planned | Admin domain owner | 4.8.0 | legacy reference only | Admin REST coverage and organization visibility |
| Schema validation | Yes | No | planned | Runtime domain owner | 4.8.0 | legacy reference only | Request/response schema validation scenarios |
| SOAP/WSDL and import-export | Yes | No | planned | API types domain owner | 4.8.0 | legacy reference only | WSDL, SOAP-to-REST, import/export |
| Restart persistence | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/restart/token_persistence_restart.feature, local `-Prestart` Maven verification on 2026-04-03 | Token persistence and revoked-token restart behavior are now verified locally; broader restart domains remain |
| Benchmark/load | Yes | No | planned | Performance domain owner | 4.8.0 | legacy reference only | Decide whether to retain in v2 or separate suite |
| UI/E2E | Yes | No | planned | E2E domain owner | 4.8.0 | legacy reference only | Prefer separate modern E2E module |

## Lane Adoption

| Lane | Purpose | Current State |
| --- | --- | --- |
| `smoke` | Fast PR validation of a minimal healthy flow | introduced |
| `core` | Main functional regression lane | introduced |
| `extended` | Broader non-migration validation | introduced |
| `migration` | Migration-specific validation | existing and now formalized |

## Next Domains To Implement

1. API invocation vertical slice completion
2. throttling framework support
3. application + subscription lifecycle edge cases
4. GraphQL and streaming API primitives
5. JWT and external IDP coverage

## Phase 0 Completion Checklist

- [x] Owner and target release fields added for each domain
- [x] Evidence field added for each domain
- [x] Frozen baseline snapshot published for 2026-04-03
