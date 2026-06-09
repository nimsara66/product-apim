# Integration V2 Parity Tracker

See the execution roadmap in [Coverage Execution Plan](coverage-execution-plan.md).
See the frozen baseline in [Coverage Baseline Snapshot](coverage-baseline-snapshot.md).
See the latest verification attempt in [Parity Baseline Verification Run 2026-06-04](parity-baseline-run-2026-06-04.md).

## Verification Status (2026-06-05)

> **The parity-baseline suite now passes 11/11**, confirmed across three consecutive clean runs
> (including a freshly booted container). Passing: system init, tenant init (×3), JWT token format,
> subscription throttling, DevPortal search/visibility, GraphQL baseline, WebSocket baseline, governance
> policy baseline, and system shutdown.
>
> Reaching green required repairing the committed `master-new-test-framework` HEAD (`906e6ff18`), which
> did not compile or run. Fixes (full detail: [parity-baseline-run-2026-06-04.md](parity-baseline-run-2026-06-04.md)):
> - Added the missing `Constants.TEST_GROUPS` / `Constants.TEST_DOMAINS` (compile break).
> - Fixed `SimpleHTTPClient` so its trust-all SSL context is actually applied (HttpClient 4.x ignores
>   `setSSLSocketFactory` when a connection manager is supplied).
> - Added a tag-scoped `@After("@cleanup")` teardown hook that deletes scenario-created APIs/applications,
>   eliminating the `409` "already exists" collision cascade on the shared suite server.
> - Reordered `testng-parity-baseline.xml` so `SystemShutdown` is the last class in a single `<test>`
>   (fixes the out-of-order/NPE shutdown under `ParallelMode.TESTS`).
> - Implemented the missing JWT password-grant + JWT-format step definitions.
> - Fixed the GraphQL create step to record its response, added DevPortal search retry for async indexing,
>   and reworked the throttling subscription-update to use the real subscription object.
> - Defaulted `docker.extra.hosts` and made the surefire suite selectable via `-Dsurefire.suite.xml`.
>
> The 2026-04-03 `409` collision and throttling notes are now **verified, root-caused, and fixed**.
> Run: `mvn clean install -pl tests-integration/cucumber-tests -am -Dsurefire.suite.xml=testng-parity-baseline.xml`.

> **The full default suite (`testng.xml`) now passes 391/391** (default H2 profile, no migration),
> verified end-to-end (~28 min). It was 20 failures in two runners before, both fixed:
> - `APIOtherCommonConfigurations` (16): the exact-string array comparison in `BaseSteps` broke when a
>   newer server build appended a field (`operationHubPolicies`) to operations. Replaced with a deep
>   **containment** comparison that verifies the expected config is reflected, tolerant of server-added
>   fields and key ordering.
> - `CreateApplication` (4): the "share application with organization" scenario needs application sharing
>   enabled; the basic `deployment.toml` overlay had it commented out (the migration overlay had it on).
>   Enabled `[apim.devportal] enable_application_sharing = true` / `application_sharing_type = "default"`.
>
> Note: `testng.xml` does not yet include the newer publisher runners (token flows, GraphQL, WebSocket,
> throttling, DevPortal search, governance, JWT) — those are exercised by the parity-baseline suite.
> Folding them into the default suite is the next increment.
> Run: `mvn clean install -pl tests-integration/cucumber-tests -am` (default suite).

## Phase 0 Metadata

- Last updated: 2026-06-07
- Last verification run: 2026-06-05 (parity-baseline 11/11 and full default suite 391/391; see Verification Status above)
- Last baseline snapshot: 2026-06-04
- Scope: Domain-level parity tracking for integration-v2 against legacy integration
- Evidence policy: Every non-planned domain must carry at least one feature path and one CI/report reference

## Coverage Summary

- Structural weighted parity: `42.6%`
  Method: `Yes = 100%`, `Partial = 50%`, `No = 0%` across the 27 tracked legacy domains.
- Domains with some v2 coverage: `74.1%` (`20/27`)
- Domains marked fully covered in tracker: `11.1%` (`3/27`)
- Effective verified parity: `49.1%`
  Method: same 27-domain baseline, but `Partial` domains with explicit green-run evidence in the tracker
  or verification reports are counted at `75%` instead of `50%`.

The effective verified view is intentionally stricter than simple asset counting and more optimistic than
the structural tracker percentage. It captures that several newer domains are now passing in the dedicated
parity-baseline suite even though they do not yet have enough breadth to be promoted from `Partial` to `Yes`.

## Status Legend

- `done`: domain is fully covered in v2 for supported scope
- `in-progress`: migration has started but legacy still carries meaningful coverage
- `planned`: domain is acknowledged but not yet implemented in v2
- `review`: waiting on validation, CI enablement, or legacy retirement decision

## Coverage Domains

| Domain | Legacy Coverage | V2 Coverage | Status | Owner | Target Release | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Common bootstrap and tenant setup | Yes | Yes | done | Framework team | 4.7.0 | features/common, local `SystemInitializationRunner` Maven verification on 2026-04-03 with Colima Docker env vars, parity-baseline suite 11/11 on 2026-06-05 | Supported bootstrap scope is now covered and repeatedly verified; future work is incremental hardening rather than gap-closing |
| Publisher core flows | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher, testng suite report | Present in v2, needs broader CRUD/subscription parity |
| Application management | Yes | Partial | in-progress | Application domain owner | 4.7.0 | features/publisher/create_new_application.feature | Present in v2, includes key generation and subscription block/unblock coverage |
| API invocation/runtime basics | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/publisher/api_key_invocation.feature, features/publisher/invalid_token_invocation.feature | Needs broader gateway behavior and edge-case parity |
| OAS import and API definition | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher/import_OAS_definition.feature, features/migration/migrated_api_definition.feature | Present in publisher and migration scenarios |
| Documents | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher/api_documents.feature, features/migration/migrated_api_documentation.feature | Needs full legacy scope comparison |
| API versioning and revisions | Yes | Partial | in-progress | Publisher domain owner | 4.7.0 | features/publisher/api_versioning.feature, features/migration/migrated_api_revisioning.feature | Needs restart and persistence parity |
| Migration validation | Yes | Yes | in-progress | Migration domain owner | 4.7.0 | features/migration, migration nightly report | Strong v2 area, completeness review pending |
| Header/custom auth behavior | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/header/custom_authorization_header.feature | Present for custom auth header slice |
| Search and visibility | Yes | Partial | in-progress | Publisher domain owner | 4.8.0 | features/migration/migrated_api_search.feature, features/publisher/devportal_search_visibility.feature, local parity-baseline suite run on 2026-06-05 | Passing in parity-baseline suite (2026-06-05) after teardown + async-index retry fixes; broader search/visibility parity pending |
| API products | Yes | Partial | in-progress | Publisher domain owner | 4.8.0 | features/migration/migrated_api_product.feature | Broader parity pending |
| Shared scopes and policies | Yes | Partial | in-progress | Governance domain owner | 4.8.0 | features/migration/migrated_shared_scopes.feature, features/migration/api_policies.feature | Admin/runtime parity pending |
| Tokens and OAuth flows | Yes | Yes | done | Runtime domain owner | 4.7.0 | features/publisher/refresh_token.feature, features/publisher/revoke_token.feature, features/publisher/sandbox_token.feature, features/publisher/openid_token.feature, features/publisher/invalid_token_invocation.feature, features/publisher/subscription_blocking.feature, local restart profile run on 2026-04-03 | Core OAuth/token flows are implemented and verified for the supported scope; JWT/external IDP breadth remains tracked separately |
| API keys and key validation | Yes | Partial | in-progress | Runtime domain owner | 4.7.0 | features/migration/api_key.feature, features/publisher/api_key_invocation.feature | Broader policy and restriction parity pending |
| JWT and external IDP | Yes | Partial | in-progress | Security domain owner | 4.8.0 | features/publisher/jwt_token_format.feature, features/publisher/openid_token.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | JWT token-format baseline executed successfully in local parity suite; external IDP parity remains pending |
| Throttling | Yes | Partial | in-progress | Governance domain owner | 4.8.0 | features/publisher/subscription_throttling_policy.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-06-05 | Passing in parity-baseline suite (2026-06-05): subscription-plan update now uses the real subscription object; API/JWT throttling breadth still pending |
| GraphQL | Yes | Partial | in-progress | API types domain owner | 4.8.0 | features/publisher/graphql_api_baseline.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-06-05 | Passing in parity-baseline suite (2026-06-05) after create-step response fix + teardown; broader parity pending |
| WebSocket and streaming APIs | Yes | Partial | in-progress | API types domain owner | 4.8.0 | features/publisher/websocket_api_baseline.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-04-03 | WebSocket create/publish baseline executed successfully in parity suite; SSE/WebSub/async parity pending |
| Governance and operation policies | Yes | Partial | in-progress | Governance domain owner | 4.8.0 | features/publisher/governance_policy_baseline.feature, features/migration/api_policies.feature, src/test/resources/testng.xml, local parity-baseline suite run on 2026-06-05 | Passing in parity-baseline suite (2026-06-05) after teardown fix; broader enforcement parity pending |
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
