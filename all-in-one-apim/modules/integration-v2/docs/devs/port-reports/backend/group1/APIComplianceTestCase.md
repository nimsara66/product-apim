# Port report — `APIComplianceTestCase` (group1, governance)

Legacy: `.../tests/apimGovernance/APIComplianceTestCase.java` — Factory ×2 (super+tenant). 2 `@Test`.
Delivered: `governance/compliance.feature` (`GovernanceComplianceRunner`, IntegrationV2-Governance block), `@cap:governance @feat:compliance`. **Verified** (blocking scenario ×2 + async compliance ×2). 🔵 **PORT+VERIFY** — verified live first (below).

## Method dispositions
| Method(s) | Disposition | Where / note |
|---|---|---|
| testRestAPIDeploymentBlockingWithPolicy | ✅ ported (×2) | create a policy attaching the default rulesets with a **BLOCK action on API_DEPLOY** → create an API → attempt a revision (deploy) → **400 `903300`**. Deterministic/synchronous. |
| testComplianceDetailsOfRestAPIAfterAPICreateWithDefaultPolicy | ✅ ported (×2) | create an API → poll `GET /artifact-compliance/api/{id}` until status settles → **NON_COMPLIANT**, governedPolicies **VIOLATED**. Async (~minutes), poll-not-sleep; ×2 tenant for parity (the two polls overlap other runners at CP=2). |

## Verify-first findings (live container, before writing assertions)
1. **Blocking is synchronous & deterministic** — the revision-create is rejected immediately with `903300` (no wait needed). The default rulesets (WSO2 API/REST/OWASP) are violated by a bare API at WARN severity, which the BLOCK action enforces on `API_DEPLOY`.
2. **The generated DTO lied about the wire value.** `ArtifactComplianceDetailsDTO.StatusEnum` declares `NON_COMPLIANT("NON-COMPLIANT")` (hyphen), but the **actual REST response returns `"status":"NON_COMPLIANT"` (underscore)**. The port asserts the real wire value `NON_COMPLIANT` — a hyphen-based assertion would have hung the full poll window then failed. (This is the whole point of verify-first.)
3. **Compliance evaluation is asynchronous** (background job; legacy hard-slept 150 s). v2 **polls, never sleeps** — `I retrieve the compliance of API "…" until the status is "NON_COMPLIANT" within 240 seconds` (catches only `IOException`, asserts after the loop, exits early once the status settles). Must run under `caffeinate` (host sleep breaks the time window — the standing lesson).

## Decisions
- **Both scenarios ×2 tenant** for parity with the legacy Factory (super+tenant). The blocking scenario is cheap/deterministic; the async compliance scenario's two polls overlap the other governance runners at CP=2, so the block wall-clock stays bounded by the ~2-minute poll ceiling rather than doubling.
- Placed in a **new IntegrationV2-Governance block** (default config, no gateway backend — compliance evaluates the API *definition*, not runtime; verified it works without `initBackend`).

## Not ported (out of scope)
- `MCPComplianceTestCase` (sibling in the same legacy package) — governs **MCP servers**, a `@cap:mcp` new capability (Wave C / new-harness). Tracked there, not here.

## Net
Both compliance behaviours ported: the deterministic **BLOCK-on-deploy** enforcement (`903300`, ×2) and the async **NON_COMPLIANT** evaluation against the default policy (×1, poll-not-sleep). Wire-value gotcha caught by verifying first.
