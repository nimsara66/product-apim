# Coverage Baseline Snapshot

## Snapshot Date

- Date: 2026-06-07
- Previous snapshot: 2026-04-03
- Scope: Legacy integration versus integration-v2 test assets
- Tracker sync: aligned with `parity-tracker.md` as of 2026-06-07

## Parity Summary

- Structural weighted parity: `42.6%`
  Method: `Yes = 100%`, `Partial = 50%`, `No = 0%` across the 27 tracked legacy domains in
  [parity-tracker.md](parity-tracker.md).
- Effective verified parity: `49.1%`
  Method: same 27-domain baseline, but `Partial` domains with explicit green-run evidence in the tracker
  or verification reports are counted at `75%` instead of `50%`.
- Domains with some v2 coverage: `74.1%` (`20/27`)
- Domains marked fully covered in tracker: `11.1%` (`3/27`)
- Raw publisher/backend asset ratio: `17.0%`
  Method: `44` v2 feature files versus `259` legacy backend `*TestCase.java` files.

## Baseline Counts

### Legacy Integration

- tests-backend Java tests: 331
- tests-restart Java tests: 20
- tests-benchmark Java tests: 5
- tests-config Java tests: 1

### Integration-v2

- cucumber-tests Java files: 58
- feature files total: 44
- feature files common: 5
- feature files header: 1
- feature files migration: 16
- feature files publisher: 21
- feature files restart: 1

## Delta Since Previous Snapshot (2026-04-03)

- Legacy tests-backend grew from 327 to 331 Java tests; legacy is still actively receiving coverage.
- V2 cucumber-tests grew from 52 to 58 Java files.
- V2 feature files grew from 38 to 44, all in the publisher group (15 to 21):
  - `publisher/devportal_search_visibility.feature`
  - `publisher/governance_policy_baseline.feature`
  - `publisher/graphql_api_baseline.feature`
  - `publisher/jwt_token_format.feature`
  - `publisher/subscription_throttling_policy.feature`
  - `publisher/websocket_api_baseline.feature`
- These additions open baseline coverage for search/visibility, governance policy, GraphQL, JWT token format, throttling, and WebSocket domains.
- Restart, header, and migration groups are unchanged since the previous snapshot.

## Interpretation

- Legacy still holds broader long-tail regression coverage by domain breadth.
- V2 has meaningful scenario density in migration and publisher areas.
- The structural parity figure (`42.6%`) is intentionally conservative and reflects tracker breadth, not pass rate.
- The effective verified parity figure (`49.1%`) reflects the stronger confidence from the green parity-baseline
  and default-suite runs now recorded in the tracker.
- Restart and header lanes remain narrow (one feature file each) and should be expanded in upcoming phases.
- Workflows, analytics/logging, admin, schema validation, SOAP/WSDL, benchmark, and config domains still have no v2 assets.

## Collection Method

The snapshot was collected from repository file-system counts using find and wc against:

- modules/integration/tests-integration (all `.java` files under each module's `src/test`)
- modules/integration-v2/tests-integration/cucumber-tests (all `.java` files and all `.feature` files)

## Next Snapshot

- Planned cadence: once per sprint
- Next target date: end of next sprint after Phase 2 deliveries
