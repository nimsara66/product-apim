# Port report — `SharedScopeTestWithRestart` (group1)

Legacy source: `modules/integration/tests-integration/tests-backend/src/test/java/.../SharedScopeTestWithRestart.java`
(listed in group1's `apim-integration-tests-shared-scope-with-restart` block).

## Disposition — ✅ COVERED (already ported)
This class was analysed and ported during the **server-restart family** work — it is not new to group1. See the
existing report: [`../../shared-scope-restart-port.md`](../../shared-scope-restart-port.md).

Delivered in v2:
- **`publisher/shared_scope_restart.feature`** (`@legacy:SharedScopeTestWithRestart`) — shared-scope
  **enforcement persists across a graceful server restart** (the genuine across-restart property; runs in the
  sequential `IntegrationV2-ServerRestart` block, super tenant).
- **`publisher/scopes.feature`** — the non-restart shared-scope behaviour, incl. `Update a shared scope's
  description` (`@legacy:SharedScopeTestWithRestart`), create/retrieve/assign/delete, ×2 tenant.

No further action for group1. Detailed method mapping, the "assert the enforcement (not just persistence)"
decision, and the single-tenant rationale are in the linked report.
