#!/usr/bin/env bash
#
# Phase 4.1 verification — BaseBlockRunner boot-failure skip guard.
#
# Type-A (no Docker): runs the isolated Phase 4.1 suite, which drives the package-private guard
# BaseBlockRunner.abortIfBlockBootFailed directly with a stub ITestContext. Asserts the guard raises a
# SkipException carrying the recorded bootError as its cause when boot failed (so block classes are
# SKIPPED, not FAILED, with no NPE cascade), and is a no-op when no bootError was recorded.
# Prints a single PASS/FAIL line; exits non-zero on failure.
#
# Usage:  ./verify-4.1.sh
set -euo pipefail

STEP="4.1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# .../cucumber-tests/src/test/scripts/verification -> module root is 4 levels up
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
REACTOR_DIR="$(cd "${MODULE_DIR}/../.." && pwd)"   # integration-v2 root

fail() { echo "VERIFY ${STEP}: FAIL - $1"; exit 1; }

echo "== Phase ${STEP} verification: BaseBlockRunner boot-failure skip guard (no Docker) =="

echo "Running verification suite via Maven..."
if ! ( cd "${REACTOR_DIR}" && mvn -q -pl tests-integration/cucumber-tests -am \
        -Dsurefire.suite.xml=testng-fv-4.1.xml test ); then
    fail "verification suite reported test failures (see Maven output above)"
fi

echo "VERIFY ${STEP}: PASS - recorded bootError becomes a per-class SkipException (cause preserved); no-op when boot succeeded"
