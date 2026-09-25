# TEST-PLAN template (Phase 3)

Present the plan in two sections. Every integration item must be INDIVIDUALLY selectable (the dev picks which to
build). Write it to `TEST-PLAN.md` in the working area and also summarize inline.

```markdown
# Test plan — <persona> — <change ref>

## Summary
- Change: <one line>
- Coverage intent: <unit N, integration M; opportunistic K (quarantined)>

## Unit tests (batch-approve)
Target class: <Class>  (repo: carbon-apimgt)
- method/branch: <what> → assertion intent: <exact expectation>
- ...
Regression proof (patch): fails on parent <sha>, passes on fix <sha>.

## Integration tests (select individually)

### [ ] I1 — <one-line behavior>
- @cap:<x> @feat:<y> @rule:<z>  @type:<smoke|negative|regression>
- Placement: EXTEND `features/<area>/<file>.feature` (or NEW file — reason: ...)
- Block: existing `IntegrationV2-<Block>` (or NEW block — LEAD APPROVAL, reason: ...)
- Topology mapping: all-in-one `<testng-v2.xml block>` | distributed `<testng-v2_distributed.xml block>`
- Reuses steps: <step 1>, <step 2>  | New steps: <none | describe>
- Asserts (exact): <status/field = exact value>
- Tenant: ×2 (super + tenant1)  | New infra: <none | describe>
- Focused runtime verification before PR (Phase 6): all-in-one <suite/result> | distributed <suite/result>
- Source: <ledger row>  | Coverage verdict: <absent | partial>

### [ ] I2 — ...

## Opportunistic (NOT from your diff — off by default, max 3)
### [ ] O1 — <adjacent product gap> — <why worth it>  (verdict: absent)
```

## Rules
- One `@cap` per scenario. If you can't pick one, the scenario does too much — split it.
- Mark each integration item's coverage verdict (absent/partial) from the gap report.
- For product integration tests, identify matching runner/block registrations in both topology suites; list
  topology-specific setup differences and ensure the scenario is included in both.
- Before PR, run and report focused runtime suites in both topologies. Preserve their actual block configuration
  and verify the intended tests executed. If one topology cannot run the scenario, document a justified scoped
  exception rather than implying parity.
- Flag every lead-approval trigger (new `@cap`/`@feat`/block) prominently.
- Keep opportunistic items ≤3, adjacency-limited, default-unchecked.
- Prefer "EXTEND existing file/block" wording over "NEW" — call out NEW only with justification.
