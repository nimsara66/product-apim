#!/usr/bin/env bash
#
# Phase 4.5 verification — skip-on-failure for the parallel-on-shared-container model.
#
# The block's tomlOverlayPath <parameter> points at a nonexistent file, so BlockLifecycleListener.onStart
# fails reading the overlay, records the cause as the bootError attribute (without throwing), and
# BaseBlockRunner's guard converts that into a per-class SkipException. Both probe classes must therefore
# be reported SKIPPED (not FAILED), with the boot exception as the single root cause and no NPE cascade;
# onFinish must no-op (no container was ever created -> nothing to stop, nothing to leak).
#
# Asserts, after the run: Maven build SUCCEEDS (skips are not failures); testng-results shows
# skipped>=2, failed=0, passed=0; the skip carries the boot cause (NoSuchFileException + the
# "APIM block boot failed" message) so it is diagnosable; there is NO NullPointerException; the probe
# observation file was NOT produced (no step ran); and no containers leaked. Re-runnable / idempotent.
# Prints a single PASS/FAIL line and exits non-zero on failure.
#
# Usage:  ./verify-4.5.sh
set -euo pipefail

STEP="4.5"
BLOCK_LABEL="fv-4.5"
LABEL_FILTER="label=block=${BLOCK_LABEL}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# .../cucumber-tests/src/test/scripts/verification -> module root is 4 levels up
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
REACTOR_DIR="$(cd "${MODULE_DIR}/../.." && pwd)"   # integration-v2 root
OBS_FILE="${MODULE_DIR}/target/fv-block-observations.txt"
RESULTS_XML="${MODULE_DIR}/target/surefire-reports/testng-results.xml"

fail() { echo "VERIFY ${STEP}: FAIL - $1"; exit 1; }

cleanup_containers() {
    local ids
    ids="$(docker ps -aq --filter "${LABEL_FILTER}" 2>/dev/null || true)"
    if [ -n "${ids}" ]; then
        echo "Cleaning up verify-${STEP} containers: ${ids}"
        docker rm -f ${ids} >/dev/null 2>&1 || true
    fi
}

# Always clean up on exit so a crashed run never poisons the next.
trap cleanup_containers EXIT

echo "== Phase ${STEP} verification: skip-on-failure =="
rm -f "${OBS_FILE}" "${RESULTS_XML}"
cleanup_containers

# A boot failure must SKIP, not FAIL: Maven must therefore succeed (skips don't fail the build).
echo "Running verification suite via Maven..."
if ! ( cd "${REACTOR_DIR}" && mvn -q -pl tests-integration/cucumber-tests -am \
        -Dsurefire.suite.xml=testng-fv-4.5.xml test ); then
    fail "Maven build failed - a boot failure was reported as FAILED/ERROR instead of SKIPPED"
fi

# Assertion 1: results show only skips (no failures, no passes).
[ -f "${RESULTS_XML}" ] || fail "expected testng results not produced: ${RESULTS_XML}"
ROOT_ATTRS="$(grep -o '<testng-results[^>]*>' "${RESULTS_XML}" | head -1)"
get_attr() { printf '%s' "${ROOT_ATTRS}" | sed -n "s/.* $1=\"\([0-9]*\)\".*/\1/p"; }
SKIPPED="$(get_attr skipped)"; FAILED="$(get_attr failed)"; PASSED="$(get_attr passed)"
[ "${FAILED:-x}" = "0" ] || fail "expected 0 failed, got '${FAILED}' (boot failure leaked as FAILED): ${ROOT_ATTRS}"
[ "${PASSED:-x}" = "0" ] || fail "expected 0 passed, got '${PASSED}' (a probe ran despite boot failure): ${ROOT_ATTRS}"
[ "${SKIPPED:-0}" -ge 2 ] || fail "expected >=2 skipped (one per probe class), got '${SKIPPED}': ${ROOT_ATTRS}"

# Assertion 2: the skip is diagnosable - carries the guard message and the real boot cause as root.
grep -q "APIM block boot failed" "${RESULTS_XML}" \
    || fail "skip reason missing the 'APIM block boot failed' guard message (blank skip?)"
grep -q "NoSuchFileException" "${RESULTS_XML}" \
    || fail "skip reason missing the boot root cause (NoSuchFileException) from the bad toml overlay"

# Assertion 3: no NPE cascade from the absent container.
if grep -q "NullPointerException" "${RESULTS_XML}"; then
    fail "NullPointerException present - the absent container caused an NPE cascade"
fi

# Assertion 4: onFinish no-op - no probe step ran, so no observation file was produced.
[ ! -s "${OBS_FILE}" ] || fail "observation file was produced - a probe step ran despite the skip"

# Assertion 5: nothing leaked - no container was ever created for this block.
LEFTOVER="$(docker ps -aq --filter "${LABEL_FILTER}" 2>/dev/null || true)"
[ -z "${LEFTOVER}" ] || fail "containers leaked after run: ${LEFTOVER}"

echo "VERIFY ${STEP}: PASS - boot failure SKIPPED ${SKIPPED} classes (0 failed/0 passed), boot cause preserved as root, no NPE cascade, onFinish no-op, no leaks"
