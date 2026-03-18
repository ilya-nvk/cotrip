#!/usr/bin/env bash
# Writes test and coverage metrics to GitHub Actions step summary.
# Usage: test-coverage-summary.sh <name> <test_results_dir> <jacoco_xml_path> [line_limit_pct] [branch_limit_pct]
#   name: e.g. "Backend" or "Android"
#   test_results_dir: path to JUnit test-results (directory with *.xml)
#   jacoco_xml_path: path to jacoco report XML (e.g. .../jacocoTestReport.xml)
#   line_limit_pct: optional quality gate limit for LINE coverage (integer percent)
#   branch_limit_pct: optional quality gate limit for BRANCH coverage (integer percent)

set -e
NAME="$1"
TEST_RESULTS_DIR="$2"
JACOCO_XML="$3"
LINE_LIMIT_PCT="${4:-}"
BRANCH_LIMIT_PCT="${5:-}"

if [[ -z "$NAME" || -z "$TEST_RESULTS_DIR" ]]; then
  echo "Usage: $0 <name> <test_results_dir> [jacoco_xml_path] [line_limit_pct] [branch_limit_pct]"
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

# Parse JaCoCo LINE/BRANCH coverage from XML
line_coverage_pct="N/A"
branch_coverage_pct="N/A"
if [[ -n "$JACOCO_XML" && -f "$JACOCO_XML" ]]; then
  # JaCoCo XML is often a single line; extract all counters and take the last one
  # (report-level aggregate across all packages/classes).
  line_counter=$(grep -o '<counter type="LINE" missed="[0-9]*" covered="[0-9]*"/>' "$JACOCO_XML" | tail -1)
  if [[ -n "$line_counter" ]]; then
    line_covered=$(echo "$line_counter" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p'); line_covered=${line_covered:-0}
    line_missed=$(echo "$line_counter" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p'); line_missed=${line_missed:-0}
    line_total=$((line_covered + line_missed))
    if [[ "$line_total" -gt 0 ]]; then
      line_coverage_pct=$((line_covered * 100 / line_total))
    fi
  fi

  branch_counter=$(grep -o '<counter type="BRANCH" missed="[0-9]*" covered="[0-9]*"/>' "$JACOCO_XML" | tail -1)
  if [[ -n "$branch_counter" ]]; then
    branch_covered=$(echo "$branch_counter" | sed -n 's/.*covered="\([0-9]*\)".*/\1/p'); branch_covered=${branch_covered:-0}
    branch_missed=$(echo "$branch_counter" | sed -n 's/.*missed="\([0-9]*\)".*/\1/p'); branch_missed=${branch_missed:-0}
    branch_total=$((branch_covered + branch_missed))
    if [[ "$branch_total" -gt 0 ]]; then
      branch_coverage_pct=$((branch_covered * 100 / branch_total))
    fi
  fi
fi

line_coverage_display="$line_coverage_pct"
if [[ "$line_coverage_pct" =~ ^[0-9]+$ ]]; then
  line_coverage_display="${line_coverage_pct}%"
fi

branch_coverage_display="$branch_coverage_pct"
if [[ "$branch_coverage_pct" =~ ^[0-9]+$ ]]; then
  branch_coverage_display="${branch_coverage_pct}%"
fi

line_limit_display="N/A"
if [[ "$LINE_LIMIT_PCT" =~ ^[0-9]+$ ]]; then
  line_limit_display="${LINE_LIMIT_PCT}%"
fi

branch_limit_display="N/A"
if [[ "$BRANCH_LIMIT_PCT" =~ ^[0-9]+$ ]]; then
  branch_limit_display="${BRANCH_LIMIT_PCT}%"
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
| Coverage (LINE / gate) | $line_coverage_display / $line_limit_display |
| Coverage (BRANCH / gate) | $branch_coverage_display / $branch_limit_display |
"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "$SUMMARY" >> "$GITHUB_STEP_SUMMARY"
else
  echo "$SUMMARY"
fi
