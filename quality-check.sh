#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

FRESH=0
if [[ "${1:-}" == "--fresh" ]]; then
  FRESH=1
  shift
fi

GRADLE_ARGS=("$@")
if [[ "$FRESH" -eq 1 ]]; then
  GRADLE_ARGS+=(--rerun-tasks)
fi

if [[ "$FRESH" -eq 1 ]]; then
  echo "[quality-check] mode=fresh (forced rerun, no UP-TO-DATE shortcuts)"
else
  echo "[quality-check] mode=incremental (Gradle may use UP-TO-DATE/cache)"
fi

read_junit_totals() {
  local results_dir="$1"
  if [[ ! -d "$results_dir" ]]; then
    echo "tests=n/a failures=n/a errors=n/a skipped=n/a"
    return
  fi
  local xml_count
  xml_count=$(find "$results_dir" -maxdepth 1 -name "*.xml" | wc -l | tr -d ' ')
  if [[ "$xml_count" -eq 0 ]]; then
    echo "tests=n/a failures=n/a errors=n/a skipped=n/a"
    return
  fi
  awk '
    match($0,/tests="[0-9]+"/){v=substr($0,RSTART+7,RLENGTH-8); t+=v}
    match($0,/failures="[0-9]+"/){v=substr($0,RSTART+10,RLENGTH-11); f+=v}
    match($0,/errors="[0-9]+"/){v=substr($0,RSTART+8,RLENGTH-9); e+=v}
    match($0,/skipped="[0-9]+"/){v=substr($0,RSTART+9,RLENGTH-10); s+=v}
    END{printf "tests=%d failures=%d errors=%d skipped=%d\n", t,f,e,s}
  ' "$results_dir"/*.xml
}

read_jacoco_metric() {
  local xml_file="$1"
  local metric="$2"
  if [[ ! -f "$xml_file" ]]; then
    echo "n/a"
    return
  fi
  local line
  line=$(grep -o "counter type=\"$metric\" missed=\"[0-9]*\" covered=\"[0-9]*\"" "$xml_file" | tail -n 1 || true)
  if [[ -z "$line" ]]; then
    echo "n/a"
    return
  fi
  local missed covered total pct
  missed=$(echo "$line" | sed -E 's/.*missed="([0-9]+)".*/\1/')
  covered=$(echo "$line" | sed -E 's/.*covered="([0-9]+)".*/\1/')
  total=$((covered + missed))
  pct=$(awk -v c="$covered" -v t="$total" 'BEGIN{if(t==0){printf "0.00"} else {printf "%.2f",(c/t)*100}}')
  echo "$pct% ($covered/$total)"
}

print_module_summary() {
  local module_name="$1"
  local results_dir="$2"
  local jacoco_xml="$3"
  local jacoco_html="$4"

  local test_totals line_cov branch_cov
  test_totals=$(read_junit_totals "$results_dir")
  line_cov=$(read_jacoco_metric "$jacoco_xml" "LINE")
  branch_cov=$(read_jacoco_metric "$jacoco_xml" "BRANCH")

  echo "[quality-check] ${module_name} summary: $test_totals"
  echo "[quality-check] ${module_name} coverage: line=${line_cov}, branch=${branch_cov}"
  echo "[quality-check] ${module_name} reports: junit=${results_dir}, jacocoXml=${jacoco_xml}, jacocoHtml=${jacoco_html}"
}

echo "[quality-check] backend"
(cd "$ROOT_DIR/backend" && {
  if ((${#GRADLE_ARGS[@]} > 0)); then
    ./gradlew qualityCheck "${GRADLE_ARGS[@]}"
  else
    ./gradlew qualityCheck
  fi
})
print_module_summary \
  "backend" \
  "$ROOT_DIR/backend/build/test-results/test" \
  "$ROOT_DIR/backend/build/reports/jacoco/test/jacocoTestReport.xml" \
  "$ROOT_DIR/backend/build/reports/jacoco/test/html/index.html"

echo "[quality-check] android"
(cd "$ROOT_DIR/android" && {
  if ((${#GRADLE_ARGS[@]} > 0)); then
    ./gradlew :app:qualityCheck "${GRADLE_ARGS[@]}"
  else
    ./gradlew :app:qualityCheck
  fi
})
print_module_summary \
  "android" \
  "$ROOT_DIR/android/app/build/test-results/testDebugUnitTest" \
  "$ROOT_DIR/android/app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml" \
  "$ROOT_DIR/android/app/build/reports/jacoco/jacocoDebugUnitTestReport/html/index.html"

echo "[quality-check] done"
