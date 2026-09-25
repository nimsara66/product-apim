# CI failure analysis report

Use this shape for multi-failure or multi-attempt investigations. For a single failure, keep only the useful
fields and report concisely.

## Run context

- Workflow/job and run URL:
- CI commit SHA:
- Topology and suite/block configuration:
- Local checkout SHA (if runtime verification was performed):

## Failure summary

| Issue | Observed attempt(s) / topology | Evidence-backed root cause and confidence | Proposed/applied fix | Status and runtime verification |
|---|---|---|---|---|
| `<failure>` | `<attempt, time, runner, block, topology>` | `<causal chain; confidence; evidence>` | `<focused change; invariants preserved>` | `<status; exact runner/suite and result>` |

## Verification details

For each topology run, record the focused command or suite descriptor, selected runner(s), block parameters kept
from CI, test count/failures/errors/skips, and report location. Record repeat attempts when performed. Identify
unrelated failures and state whether the CI failure was reproduced locally.

## Remaining uncertainty

List only questions that evidence could not settle and the next check that would distinguish the plausible causes.
