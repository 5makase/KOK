#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

THREADS="${1:-300}"
RAMP_UP="${2:-30}"
LOOPS="${3:-1}"
HOST="${HOST:-localhost}"
PORT="${PORT:-8004}"
SCENARIO_CSV="${SCENARIO_CSV:-${SCRIPT_DIR}/synthetic-data/friday-peak-300.csv}"

RESULT_DIR="${SCRIPT_DIR}/synthetic-results"
REPORT_DIR="${RESULT_DIR}/html-${THREADS}-${RAMP_UP}-${LOOPS}"
RESULT_FILE="${RESULT_DIR}/waiting-synthetic-${THREADS}-${RAMP_UP}-${LOOPS}.jtl"

if [[ ! -f "${SCENARIO_CSV}" ]]; then
  echo "SCENARIO_CSV file not found: ${SCENARIO_CSV}" >&2
  echo "Generate it first: jmeter/waiting/generate-synthetic-waiting-users.py" >&2
  exit 1
fi

SCENARIO_ROWS=$(( $(wc -l < "${SCENARIO_CSV}") - 1 ))
TOTAL_USERS=$(( THREADS * LOOPS ))
if (( SCENARIO_ROWS < TOTAL_USERS )); then
  echo "SCENARIO_CSV has ${SCENARIO_ROWS} data rows, but ${TOTAL_USERS} are required." >&2
  exit 1
fi

mkdir -p "${RESULT_DIR}"
rm -f "${RESULT_FILE}"
rm -rf "${REPORT_DIR}"

jmeter -n \
  -t "${SCRIPT_DIR}/waiting-synthetic-load-test.jmx" \
  -Jhost="${HOST}" \
  -Jport="${PORT}" \
  -JscenarioCsv="${SCENARIO_CSV}" \
  -Jthreads="${THREADS}" \
  -JrampUp="${RAMP_UP}" \
  -Jloops="${LOOPS}" \
  -JresultFile="${RESULT_FILE}" \
  -l "${RESULT_FILE}" \
  -e \
  -o "${REPORT_DIR}"

echo
echo "JMeter scenario CSV: ${SCENARIO_CSV}"
echo "JMeter result file: ${RESULT_FILE}"
echo "JMeter HTML report: ${REPORT_DIR}/index.html"
