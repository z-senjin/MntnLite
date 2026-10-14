#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/run-release-e2e.sh [quick|full] [CHECK_ID...]

Runs the live release smoke harness (docs/RELEASE_E2E.md) against a logged-in F2P account.

Options:
  --jar PATH        Run an already-built shaded jar (for example the release candidate) instead of building
  --fail-fast       Stop after the first failing check (logout still runs)
  --list            Print the check ids and exit
  -h, --help        Show this help

Environment:
  MICROBOT_E2E_JAR                Same as --jar
  MICROBOT_E2E_HOME               user.home for the client (isolated runtime home with the saved profile)
  MICROBOT_E2E_OUTPUT_DIR         Result directory (default: <home>/.runelite/test-results/release-e2e)
  MICROBOT_E2E_EXPECTED_VERSION   Version the client must report (default: microbot.version in gradle.properties, empty disables)
  MICROBOT_E2E_TOTAL_TIMEOUT_MS   Total budget for the checks (default: 900000 quick, 2100000 full)
  MICROBOT_E2E_WORLD              Log in to this world (use an F2P world for an F2P account)
  MICROBOT_E2E_HOP_WORLD          World for the world-hop check (default: least crowded other F2P world)
  MICROBOT_E2E_HUB_PLUGIN         Plugin Hub plugin for the hub check (default: AttackRangesPlusPlugin)
  MICROBOT_E2E_GRADLE             Gradle command used to build (default: ./gradlew)
  MICROBOT_E2E_JAVA               Java executable (default: java)
  MICROBOT_E2E_JVM_ARGS           Extra JVM arguments
EOF
}

CHECK_IDS=(boot login state-caches widgets walker-transport bank npc-dialogue object-interaction agent-server
  walker-medium grand-exchange world-hop hub-plugins script-lifecycle logout)

TIER=quick
FAIL_FAST=false
JAR="${MICROBOT_E2E_JAR:-}"
CHECKS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    quick|full) TIER="$1" ;;
    --jar) JAR="${2:?--jar needs a path}"; shift ;;
    --fail-fast) FAIL_FAST=true ;;
    --list) printf '%s\n' "${CHECK_IDS[@]}"; exit 0 ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Unknown option $1" >&2; usage >&2; exit 64 ;;
    *) CHECKS+=("$1") ;;
  esac
  shift
done

for id in ${CHECKS[@]+"${CHECKS[@]}"}; do
  if [[ ! " ${CHECK_IDS[*]} " =~ " ${id} " ]]; then
    echo "Unknown check id '$id'. Known ids: ${CHECK_IDS[*]}" >&2
    exit 64
  fi
done

cd "$(dirname "$0")/.."

RUNTIME_HOME="${MICROBOT_E2E_HOME:-$HOME}"
OUTPUT_DIR="${MICROBOT_E2E_OUTPUT_DIR:-$RUNTIME_HOME/.runelite/test-results/release-e2e}"
GRADLE_CMD="${MICROBOT_E2E_GRADLE:-./gradlew}"
JAVA_BIN="${MICROBOT_E2E_JAVA:-java}"

if [[ -z "${DISPLAY:-}" && "$(uname)" == "Linux" ]]; then
  echo "DISPLAY is not set; the client needs a display" >&2
  exit 65
fi

if [[ -z "$JAR" ]]; then
  echo "Building shaded client jar..."
  $GRADLE_CMD :client:shadowJar --console=plain
  JAR="$(ls -t runelite-client/build/libs/client-*-shaded.jar 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$JAR" || ! -f "$JAR" ]]; then
  echo "Shaded jar not found: '${JAR}'" >&2
  exit 66
fi

if [[ -n "${MICROBOT_E2E_EXPECTED_VERSION+x}" ]]; then
  EXPECTED_VERSION="$MICROBOT_E2E_EXPECTED_VERSION"
else
  EXPECTED_VERSION="$(sed -n 's/^microbot\.version=//p' gradle.properties | head -n 1)"
fi

if [[ -n "${MICROBOT_E2E_TOTAL_TIMEOUT_MS:-}" ]]; then
  TOTAL_TIMEOUT_MS="$MICROBOT_E2E_TOTAL_TIMEOUT_MS"
elif [[ "$TIER" == "full" ]]; then
  TOTAL_TIMEOUT_MS=2100000
else
  TOTAL_TIMEOUT_MS=900000
fi
RUNNER_TIMEOUT_MS=$((TOTAL_TIMEOUT_MS + 600000))

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

IFS=',' CHECK_LIST="${CHECKS[*]:-}"
unset IFS

CMD=("$JAVA_BIN" -ea -Dfile.encoding=UTF-8)
if [[ -n "${MICROBOT_E2E_HOME:-}" ]]; then
  CMD+=("-Duser.home=$RUNTIME_HOME")
fi
if [[ -n "${MICROBOT_E2E_JVM_ARGS:-}" ]]; then
  read -r -a EXTRA_JVM_ARGS <<< "$MICROBOT_E2E_JVM_ARGS"
  CMD+=("${EXTRA_JVM_ARGS[@]}")
fi
CMD+=(
  -Dmicrobot.disableTelemetry=true
  -Dmicrobot.test.mode=true
  "-Dmicrobot.test.script=Release Smoke Harness"
  "-Dmicrobot.test.timeout=$RUNNER_TIMEOUT_MS"
  "-Dmicrobot.test.output=$OUTPUT_DIR"
  "-Dmicrobot.test.release.tier=$TIER"
  "-Dmicrobot.test.release.failFast=$FAIL_FAST"
  "-Dmicrobot.test.release.totalTimeoutMs=$TOTAL_TIMEOUT_MS"
  "-Dmicrobot.test.release.expectedVersion=$EXPECTED_VERSION"
)
[[ -n "$CHECK_LIST" ]] && CMD+=("-Dmicrobot.test.release.checks=$CHECK_LIST")
[[ -n "${MICROBOT_E2E_WORLD:-}" ]] && CMD+=("-Dmicrobot.test.world=$MICROBOT_E2E_WORLD")
[[ -n "${MICROBOT_E2E_HOP_WORLD:-}" ]] && CMD+=("-Dmicrobot.test.release.hopWorld=$MICROBOT_E2E_HOP_WORLD")
[[ -n "${MICROBOT_E2E_HUB_PLUGIN:-}" ]] && CMD+=("-Dmicrobot.test.release.hubPlugin=$MICROBOT_E2E_HUB_PLUGIN")
CMD+=(-jar "$JAR" --disable-telemetry)

echo "Running release E2E ($TIER${CHECK_LIST:+: $CHECK_LIST}) with $JAR"
START=$(date +%s)
set +e
"${CMD[@]}" > /dev/null 2> "$OUTPUT_DIR/client-stderr.log"
STATUS=$?
set -e
ELAPSED=$(( $(date +%s) - START ))

if [[ -f "$OUTPUT_DIR/summary.md" ]]; then
  cat "$OUTPUT_DIR/summary.md"
else
  echo "No summary written; see $OUTPUT_DIR/client-stderr.log and ~/.runelite/logs/client.log" >&2
  tail -n 40 "$OUTPUT_DIR/client-stderr.log" >&2 || true
fi
if [[ ! -f "$OUTPUT_DIR/result.json" ]]; then
  echo "No result.json written in $OUTPUT_DIR" >&2
  [[ "$STATUS" -eq 0 ]] && STATUS=1
fi

echo
echo "Wall clock: ${ELAPSED}s, client exit code: $STATUS"
echo "Results: $OUTPUT_DIR/result.json, $OUTPUT_DIR/summary.md"
exit "$STATUS"
