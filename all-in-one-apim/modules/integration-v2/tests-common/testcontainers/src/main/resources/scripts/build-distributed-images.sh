#!/bin/bash
# Phase 2 (T2.1) — build the three distributed-component Docker images for the integration-v2 lane,
# mirroring the all-in-one recipe: docker build from the wso2/docker-apim #master Dockerfile fed the
# locally-built component zip over a throwaway http.server, then overlay the MySQL JDBC connector into
# repository/components/lib (the packs do not bundle it).
#
# Invoked by tests-common/testcontainers/pom.xml (profile 'distributed', phase pre-integration-test).
# Args:
#   $1 = testcontainers module basedir (${project.basedir})
#   $2 = apim.server.version           (e.g. 4.7.0-SNAPSHOT)
#   $3 = path to the staged mysql-connector jar
#   $4 = docker extra hosts arg or empty (e.g. --add-host=host.docker.internal:host-gateway)
set -euo pipefail

BASEDIR="$1"; VER="$2"; CONN_JAR="$3"; EXTRA_HOSTS="${4:-}"
PORT=8001
GH=https://github.com/wso2/docker-apim.git#master:dockerfiles/ubuntu

# product-apim root is 5 levels up from the testcontainers module
# (testcontainers -> tests-common -> integration-v2 -> modules -> all-in-one-apim -> product-apim)
PRODUCT_ROOT="$(cd "$BASEDIR/../../../../.." && pwd)"
echo "[dist-images] product root: $PRODUCT_ROOT"
[ -f "$CONN_JAR" ] || { echo "[dist-images] ERROR: connector jar not found: $CONN_JAR"; exit 1; }

# component entries: <docker-apim-subdir>:<zip-module-dir>:<image-name>
# (kept bash-3.2 portable — no associative arrays; macOS ships bash 3.2)
COMPONENTS="apim-acp:api-control-plane:wso2am-acp apim-tm:traffic-manager:wso2am-tm apim-universal-gw:gateway:wso2am-universal-gw"

# 1. stage all three zips into one served dir (symlinks; http.server follows them)
ZDIR="$BASEDIR/target/dist-images/zips"; rm -rf "$ZDIR"; mkdir -p "$ZDIR"
for c in $COMPONENTS; do
  moduledir="$(echo "$c" | cut -d: -f2)"; name="$(echo "$c" | cut -d: -f3)"
  zip="$PRODUCT_ROOT/$moduledir/modules/distribution/product/target/$name-$VER.zip"
  [ -f "$zip" ] || { echo "[dist-images] ERROR: component zip missing: $zip (build the component reactor first)"; exit 1; }
  ln -sf "$zip" "$ZDIR/$name-$VER.zip"
done

# 2. serve the zip dir; ensure it is stopped on exit
( cd "$ZDIR" && nohup python3 -m http.server $PORT --bind 0.0.0.0 >"$BASEDIR/target/dist-images/http.log" 2>&1 & echo $! > "$BASEDIR/target/dist-images/http.pid" )
cleanup() {
  kill "$(cat "$BASEDIR/target/dist-images/http.pid" 2>/dev/null)" 2>/dev/null || true
  # belt-and-suspenders: nohup in a subshell can detach the child, so also kill by pattern
  pkill -f "http.server $PORT" 2>/dev/null || true
}
trap cleanup EXIT
for i in $(seq 1 15); do curl -s "http://localhost:$PORT/" >/dev/null 2>&1 && break; sleep 1; done

# 3. build each component: docker-apim base + connector overlay
for c in $COMPONENTS; do
  sub="$(echo "$c" | cut -d: -f1)"; short="$(echo "$c" | cut -d: -f3 | sed 's/^wso2am-//')"; name="$(echo "$c" | cut -d: -f3)"
  echo "[dist-images] building $name:$VER-jdk21 (base $sub + connector overlay)"
  docker build $EXTRA_HOSTS "$GH/$sub" \
    -t "$name-base:$VER-jdk21" \
    --build-arg WSO2_SERVER_VERSION="$VER" \
    --build-arg WSO2_SERVER_ZIP_VERSION="$VER" \
    --build-arg WSO2_SERVER="$name-$VER" \
    --build-arg WSO2_SERVER_DIST_URL="http://host.docker.internal:$PORT/$name-$VER.zip"

  ovl="$BASEDIR/target/dist-images/$short.Dockerfile"
  printf 'FROM %s-base:%s-jdk21\nCOPY %s /home/wso2carbon/%s-%s/repository/components/lib/\n' \
    "$name" "$VER" "$(basename "$CONN_JAR")" "$name" "$VER" > "$ovl"
  cp "$CONN_JAR" "$BASEDIR/target/dist-images/$(basename "$CONN_JAR")"
  docker build -f "$ovl" -t "$name:$VER-jdk21" "$BASEDIR/target/dist-images"
  docker run --rm --entrypoint sh "$name:$VER-jdk21" \
    -c "ls /home/wso2carbon/$name-$VER/repository/components/lib/ | grep -q mysql-connector" \
    && echo "[dist-images] $name:$VER-jdk21 OK (connector present)"
done

echo "[dist-images] done: $(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^wso2am-(acp|tm|universal-gw):$VER-jdk21" | tr '\n' ' ')"
