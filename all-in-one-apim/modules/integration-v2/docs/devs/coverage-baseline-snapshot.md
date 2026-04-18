# Coverage Baseline Snapshot

## Snapshot Date

- Date: 2026-04-03
- Scope: Legacy integration versus integration-v2 test assets

## Baseline Counts

### Legacy Integration

- tests-backend Java tests: 327
- tests-restart Java tests: 20
- tests-benchmark Java tests: 5
- tests-config Java tests: 1

### Integration-v2

- cucumber-tests Java files: 52
- feature files total: 38
- feature files common: 5
- feature files header: 1
- feature files migration: 16
- feature files publisher: 15
- feature files restart: 1

## Interpretation

- Legacy still holds broader long-tail regression coverage by domain breadth.
- V2 has meaningful scenario density in migration and publisher areas.
- Restart, header, and specialized domains remain narrow and should be expanded in upcoming phases.

## Collection Method

The snapshot was collected from repository file-system counts using find and wc against:

- modules/integration/tests-integration
- modules/integration-v2/tests-integration/cucumber-tests

## Next Snapshot

- Planned cadence: once per sprint
- Next target date: end of next sprint after Phase 1 deliveries