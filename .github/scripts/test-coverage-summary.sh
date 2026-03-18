#!/usr/bin/env bash
# Writes test and coverage metrics to GitHub Actions step summary.
# Usage: test-coverage-summary.sh <name> <test_results_dir> <jacoco_xml_path>
#   name: e.g. "Backend" or "Android"
#   test_results_dir: path to JUnit test-results (directory with *.xml)
#   jacoco_xml_path: path to jacoco report XML (e.g. .../jacocoTestReport.xml)

set -e
NAME="$1"
TEST_RESULTS_DIR="$2"
JACOCO_XML="$3"

if [[ -z "$NAME" || -z "$TEST_RESULTS_DIR" ]]; then
  echo "Usage: $0 <name> <test_results_dir> [jacoco_xml_path]"
  exit 1
fi

# Parse JUnit XML: sum tests, failures, errors from all testsuite elements
total=0
failed=0
errors=0
if [[ -d "$TEST_RESULTS_DIR" ]]; then
  while IFS= read -r line; do
    t=$(echo "$line" | sed -n 's/.*tests="\([0-9]*\)".*/\1/p'); total=$((total + ${t:-0}))
    f=$(echo "$line" | sed -n 's/.*failures="\([0-9]*\)".*/\1/p'); failed=$((failed + ${f:-0}))
    e=$(echo "$line" | sed -n 's/.*errors="\([0-9]*\)".*/\1/p'); errors=$((errors + ${e:-0}))
  done < <(grep -rh "<testsuite " "$TEST_RESULTS_DIR" 2>/dev/null || true)
fi
passed=$((total - failed - errors))
[[ -z "$total" || "$total" -lt 0 ]] && total=0
[[ -z "$passed" || "$passed" -lt 0 ]] && passed=0
[[ -z "$failed" || "$failed" -lt 0 ]] && failed=0
[[ -z "$errors" || "$errors" -lt 0 ]] && errors=0

if [[ "$total" -gt 0 ]]; then
  pct=$((passed * 100 / total))
else
  pct=0
fi

# Parse JaCoCo LINE coverage from XML (counter type="LINE" missed="N" covered="M")
coverage_pct="N/A"
if [[ -n "$JACOCO_XML" && -f "$JACOCO_XML" ]]; then
  line_line=$(grep 'type="LINE"' "$JACOCO_XML" | head -1)
  if [[ -n "$line_line" ]]; then
    line_covered=$(echo "$line_line" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p'); line_covered=${line_covered:-0}
    line_missed=$(echo "$line_line" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p'); line_missed=${line_missed:-0}
    line_total=$((line_covered + line_missed))
    if [[ "$line_total" -gt 0 ]]; then
      coverage_pct=$((line_covered * 100 / line_total))
    fi
  fi
fi

coverage_display="$coverage_pct"
if [[ "$coverage_pct" =~ ^[0-9]+$ ]]; then
  coverage_display="${coverage_pct}%"
fi

# Append to GitHub Step Summary (or stdout if not in Actions)
SUMMARY="## $NAME — Tests & Coverage

| Metric | Value |
|--------|----------|
| Total tests | $total |
| Passed | $passed |
| Failed (failures) | $failed |
| Errors | $errors |
| Pass rate | $pct% |
| Code coverage (LINE) | $coverage_display |
"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "$SUMMARY" >> "$GITHUB_STEP_SUMMARY"
else
  echo "$SUMMARY"
fi
