---
name: test-coverage
description: >-
  Turn source changes (PRs/branches/diffs across carbon-apimgt, product-apim, docs-apim) into verified tests —
  unit + integration — that improve coverage and harden product flows against regressions. Use when a feature or
  patch developer wants tests written for their changes, or asks "what tests should I add / is this covered".
  Decides unit vs integration per a rubric, reuses existing steps/features (no duplication), and gates new
  capabilities/blocks on lead approval. Product integration scenarios must stay in parity across all-in-one and
  distributed suites and pass focused runtime verification in both before PR submission. For diagnosing or
  repairing an existing CI test failure, use `ci-failure-rca`.
---

# Test-Coverage Authoring Skill

You author tests for a developer's *source changes*. Goal: **maximize coverage gain and regression protection
with minimal test duplication.** Full design + rationale: `all-in-one-apim/modules/integration-v2/docs/devs/test-coverage-skill-design.md`.
Integration-test authoring rules you MUST obey: `all-in-one-apim/modules/integration-v2/tests-integration/cucumber-tests/CLAUDE.md`.

## Operating principles
- **Verify, don't assume.** Probe real behavior / read the actual code; never infer a status code or a "it's
  covered" from a tag name. Prefer running a minimal check over guessing.
- **Coverage-first, cost-aware.** Prefer unit tests where they fully capture the behavior (cheap); escalate to
  integration for anything that only manifests through wiring. Cost is a tie-breaker, never a reason to skip a
  regression-critical flow. Integration cost is measured at the **container-block** level (see §Rubrics).
- **No duplication.** Reuse/extend existing step definitions and feature files. Search before writing.
- **Ask before expensive or outward actions.** Never run the full suite without explicit consent. Surface
  blockers instead of working around them.

## Modes
- **analysis-only** (default when the dev says "what should I add" / "is this covered"): run Phases 0–3, present
  the plan, stop. Do NOT write tests.
- **implement**: after the dev approves specific items (Phase 4), run Phases 5–7.
State the mode you're in at the start.

---

## Phase 0 — Intake
1. **Persona.** Infer from the change shape and **confirm in one line** (don't force a menu):
   - *Feature developer* — additive change (new capability/feature/endpoint); docs-apim is the spec source.
   - *Patch developer* — corrective change on a ticket (bug fix / minor improvement); the ticket + the fix's
     behavioral delta is the spec.
2. **Repo locations.** The skill spans up to three repos: **carbon-apimgt** (units), **product-apim** (this repo;
   integration tests), **docs-apim** (spec). Read `.claude/test-coverage-repos.json` for stored local paths.
   - `product-apim` defaults to the current repo root — don't ask which repo you're already in.
   - For any OTHER repo not in the config (`carbon-apimgt`, and `docs-apim` for the feature persona): **ASK the
     developer for the path and wait — do NOT auto-detect and persist a guess.** A wrong repo → wrong analysis.
     Write the confirmed paths to the config so later runs don't re-ask.
   See `references/repo-config.md`. Hybrid: a repo present locally → you can *run* tests there; absent →
   *plan only*.
3. **Ingest the change.** Take PR URLs / branches / diffs. Correlate them as ONE logical change across repos
   (e.g. a config key in carbon-apimgt → default config in product-apim → doc in docs-apim), not three
   independent diffs. For patch persona, take the ticket text (dev pastes it).
Output: a change-set manifest (repo, files, hunks, touched symbols).

## Phase 1 — Change analysis
Follow `references/change-analysis.md`. Classify each changed unit: unit-testable pure logic / integration-only
(wiring) / doc-spec. Feature: mine docs-apim additions → candidate assertions. Patch: extract root cause + the
behavioral delta → the exact regression to pin. Output: a test-worthiness ledger.

## Phase 2 — Existing-coverage gap analysis
Follow `references/gap-analysis.md`.
- **Unit track:** find existing `*Test` classes for the touched classes; list changed methods/branches with no
  assertions.
- **Integration track:** place each candidate flow in the **capability tree** (`docs/devs/capability-map.yml` +
  `docs/devs/coverage-tree.md`, the generated tree) for ORGANIZATION/placement, then read the owning feature
  file(s)/scenarios to judge covered / partial / absent. Also verify runner registration and block setup in both
  `testng-v2.xml` (all-in-one) and `testng-v2_distributed.xml` (distributed). The tree tells you *where a test
  belongs*; the feature files tell you *what's covered*; the two suite files tell you *where it executes* — do
  not trust tags or the tree alone.
Output: a gap report (unit gaps + integration gaps, each with a covered/partial/absent verdict + file pointers).

## Phase 3 — Test-plan synthesis
Prioritize by `coverage-gain × regression-value ÷ cost` (cost is block-level — §Rubrics). Present in TWO forms
(template: `references/plan-template.md`):
- **Unit tests** → a coverage-description summary per target class (methods/branches + assertion intent);
  approved as a batch.
- **Integration tests** → a **flow-by-flow** breakdown, each item individually selectable, with: `@cap`/`@feat`
  placement · the feature file it *extends* (or a new file/block + justification) · the step definitions it
  reuses · the exact-value assertion · ×2-tenant note · all-in-one and distributed runner/block mapping ·
  new-infra flag.
- **Opportunistic** (not-from-diff) gaps are allowed but **quarantined**: clearly labeled, default-off, capped
  (≤3), adjacency-limited. Never turn a one-bug patch into a 20-item plan.
Output: a `TEST-PLAN.md` the dev can edit/select from.

## Phase 4 — Approval loop
Dev selects items and gives feedback; iterate until they're confident. **Lead-approval GATES** (halt the affected
item until the dev confirms lead sign-off):
- a **new `@cap`** (the vocabulary in `capability-map.yml` is closed),
- a **new `@feat`** (also edits the closed vocabulary),
- a **new `<test>` block / container** (real wall-clock + host-budget cost).
Warn against dodging the `@cap`/`@feat` gate by mis-filing a genuinely-new capability under an existing tag.

## Phase 5 — Implementation (implement mode only)
Per approved item, obey CLAUDE.md fully. Run the `references/duplication-check.md` pass BEFORE writing any new
artifact (grep the glue for a reusable step; confirm no existing feature fits).
- **Unit:** extend the existing test class without duplication, in the module's idiom. **Patch persona — prove
  the regression:** check out the fix's PARENT commit, run the candidate test, confirm it FAILS; check out the
  fix, confirm it PASSES. A test that can't fail on the old code doesn't guard anything.
- **Integration:** reuse/extend steps (never near-duplicate), extend existing feature files where one fits,
  correct folder + `@cap`, isolation/cleanup/actor rules, `Copyright (c) 2026` on new `.java`, ×2 tenant, strict
  **exact-value** assertions (never `401 || 403`). Keep product scenario coverage in parity: update/register the
  runner and scenario in both `testng-v2.xml` and `testng-v2_distributed.xml`, preserving each topology's real
  listener and block parameters. New product runners must be registered in both suites. Do not silently omit a
  scenario from one topology; if a test is inherently topology-specific or cannot run in one topology, document
  the reason and the scoped exception in the plan.
- **Docs divergence (feature):** where a docs-derived assertion disagrees with actual behavior, SURFACE it
  (doc bug vs impl bug) — never silently encode one.

## Phase 6 — Verify
- **Minimal first:** unit → run the affected class(es); integration → run focused suites on BOTH all-in-one and
  distributed topologies before marking the change ready for a pull request. Each focused suite selects the
  affected runner(s) while preserving the corresponding suite's real block configuration, listener, overlay,
  and topology. Confirm the expected runner/scenarios executed and inspect test counts/reports; a successful
  Maven exit with zero or unintended tests is not verification. Use the repository's documented Docker/image
  setup and rebuild any changed fixture image that the test consumes.
- This two-topology focused verification is required even if the distributed full-suite CI lane is disabled or
  skipped. The product suite contract is runner/scenario parity; distributed JaCoCo collection remains
  intentionally unsupported, so do not require `-Dapim.coverage=true` for the distributed run.
- If either topology fails, diagnose and fix it or report the change as not ready for PR; do not claim parity
  from the other topology's pass. If a topology is genuinely inapplicable, use the documented scoped exception
  from Phase 5 and explain its runtime evidence.
- **Then ASK** before the full local suite. Run it with `-Dapim.coverage=true` on all-in-one to capture the
  coverage delta (the quantitative re-eval). The exec dir is auto-purged at suite start, so no manual cleanup is
  needed.
- **Blockers** (infra gaps, product quirks) → surface and discuss; park with a documented reason if unresolved
  (never massage a suspicious failure to green).

## Phase 7 — Wrap
- If a new `@cap`/`@feat` was approved: update `capability-map.yml`, regenerate the tree
  (`python3 docs/devs/render_coverage_tree.py`, require `invalid: 0`), and mark the new flows covered.
- Report the coverage delta (unit + integration, before/after) and focused runtime results separately for
  all-in-one and distributed as the closing artifact.

---

## Rubrics (the judgment to apply)

**Unit vs integration — decide by observable properties, not vibe:**
- *Integration* if the change touches: a gateway/Synapse handler or data-plane path · a REST resource/endpoint
  contract · a runtime config/TOML key · a DTO crossing the wire · DB or a cross-component boundary ·
  multi-actor/multi-tenant behavior · lifecycle/deploy semantics.
- *Unit* if it's a pure function, validator, calculator, parser, or branch logic with no I/O.
- *Both* when a computed value (unit) is enforced at a boundary (integration) — state why.
- Ambiguous → escalate to integration only when a regression would otherwise be unguarded; else unit.

**Cost is block-level.** The unit of integration cost is the **container boot** (minutes + host budget). A
scenario appended to an existing feature/block is near-free; a new block with its own overlay/restart is a whole
container → lead-approval gate. This reorders priority: *extend an existing block* dominates *new block* even when
a new block is cleaner.

**Failure modes to guard against:** hallucinated placement (read the real feature files) · duplicate tests
(mandatory grep) · over-mocked units that pass but don't protect (the parent-commit fail check kills these) ·
plan bloat (opportunistic quarantine) · `@cap`-gate gaming · docs ahead of impl (surface divergence).

## References
- `references/change-analysis.md` — diff → semantic-unit classification
- `references/gap-analysis.md` — unit-gap + capability-tree placement procedure
- `references/duplication-check.md` — the pre-write search discipline
- `references/plan-template.md` — the TEST-PLAN.md shape
- `references/repo-config.md` — repo-location config file format
