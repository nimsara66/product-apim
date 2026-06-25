#!/usr/bin/env bash
#
# Phase 5.5 verification — a provisioning failure in onStart SKIPS the block cleanly.
#
# testng-fv-5.5.xml boots ONE REAL APIM block (block=fv-5.5) with initTenantUsers=true and
# tenantSet=adpsample. adpsample is the pre-migrated profile: addAdpsampleTenant only builds a context bean
# (no SOAP create), so on this FRESH (non-migrated) container adpsample.com does not exist server-side. The
# follow-up addUser SOAP authenticates as admin@adpsample.com and gets a non-200, so TenantUserProvisioner
# throws. Because provisioning runs inside BlockLifecycleListener.onStart's try, the throw is recorded as the
# bootError attribute (NOT surfaced mid-scenario), and BaseBlockRunner's @BeforeClass turns it into a
# SkipException - the probe class is reported SKIPPED with the provisioning failure as root cause.
#
# Asserts, after the run: Maven build SUCCEEDS (a clean skip is not a build failure); testng-results shows
# skipped>=1 and failed=0 (skipped cleanly, no NPE cascade); the Maven log carries the listener's
# boot-failure marker (the skip really came from the provisioning failure, not something else); the probe
# never ran, so NO block observation was recorded; and the container did not leak - onFinish stopped it even
# though provisioning failed after boot. Re-runnable / idempotent. Single PASS/FAIL line, non-zero on fail.
#
# Usage:  ./verify-5.5.sh
set -euo pipefail

STEP="5.5"
BLOCK_LABEL="fv-5.5"
LABEL_FILTER="label=block=${BLOCK_LABEL}"
SUITE_XML="testng-fv-5.5.xml"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# .../cucumber-tests/src/test/scripts/verification -> module root is 4 levels up
MODULE_DIR="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
REACTOR_DIR="$(cd "${MODULE_DIR}/../.." && pwd)"   # integration-v2 root
OBS_FILE="${MODULE_DIR}/target/fv-block-observations.txt"
RESULTS_XML="${MODULE_DIR}/target/surefire-reports/testng-results.xml"
MVN_LOG="${MODULE_DIR}/target/verify-5.5-maven.log"

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

echo "== Phase ${STEP} verification: provisioning failure skips the block cleanly =="
rm -f "${OBS_FILE}" "${MVN_LOG}"
cleanup_containers

echo "Running verification suite via Maven..."
if ! ( cd "${REACTOR_DIR}" && mvn -q -pl tests-integration/cucumber-tests -am \
        -Dsurefire.suite.xml="${SUITE_XML}" test ) > "${MVN_LOG}" 2>&1; then
    tail -25 "${MVN_LOG}"
    fail "Maven build failed - a clean skip must NOT fail the build (see ${MVN_LOG})"
fi

# Assertion 1: the block was SKIPPED cleanly - skipped>=1 and zero failures (no NPE cascade).
[ -f "${RESULTS_XML}" ] || fail "testng-results.xml not produced: ${RESULTS_XML}"
HEADER="$(grep -o '<testng-results[^>]*>' "${RESULTS_XML}" | head -1)"
get_attr() { printf '%s' "${HEADER}" | sed -n "s/.* $1=\"\\([0-9]*\\)\".*/\\1/p"; }
FAILED="$(get_attr failed)"; SKIPPED="$(get_attr skipped)"
[ "${FAILED}" = "0" ] || fail "expected failed=0 (clean skip, not a failure cascade), got '${FAILED}' (${HEADER})"
[ -n "${SKIPPED}" ] && [ "${SKIPPED}" -ge 1 ] \
    || fail "expected skipped>=1 (the block must skip on provisioning failure), got '${SKIPPED}' (${HEADER})"

# Assertion 2: the skip really came from the provisioning failure recorded by the listener.
grep -q "boot/readiness failed" "${MVN_LOG}" \
    || fail "Maven log lacks the listener's boot-failure marker - skip may not be provisioning-driven (see ${MVN_LOG})"

# Assertion 3: the probe never ran (skipped before its scenario), so it recorded no observation.
[ ! -f "${OBS_FILE}" ] \
    || fail "an observation was recorded - the skipped probe unexpectedly ran its scenario: ${OBS_FILE}"

# Assertion 4: the container did not leak - onFinish stopped it despite the post-boot provisioning failure.
LEFTOVER="$(docker ps -aq --filter "${LABEL_FILTER}" 2>/dev/null || true)"
[ -z "${LEFTOVER}" ] || fail "fv-5.5 container leaked after a provisioning-failure skip: ${LEFTOVER}"

echo "VERIFY ${STEP}: PASS - provisioning failure recorded as bootError; block SKIPPED cleanly (skipped=${SKIPPED}, failed=0), probe never ran, container released by onFinish, no leak"
