#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

THREADS="${1:-50}"
RAMP_UP="${2:-10}"
LOOPS="${3:-1}"
HOST="${HOST:-localhost}"
PORT="${PORT:-8004}"
STORE_ID="${STORE_ID:-49e778c5-522c-4cfd-86a9-57f5e9b7fab4}"

RESULT_DIR="${SCRIPT_DIR}/results"
GENERATED_DIR="${SCRIPT_DIR}/generated-users"
REPORT_DIR="${RESULT_DIR}/html-${THREADS}-${RAMP_UP}-${LOOPS}"
RESULT_FILE="${RESULT_DIR}/waiting-registration-${THREADS}-${RAMP_UP}-${LOOPS}.jtl"
TOTAL_USERS=$((THREADS * LOOPS))
RUN_ID="$(date +%Y%m%d%H%M%S)"
GENERATED_USER_CSV="${GENERATED_DIR}/users-${THREADS}-${RAMP_UP}-${LOOPS}-${RUN_ID}.csv"
USER_CSV="${USER_CSV:-${GENERATED_USER_CSV}}"

mkdir -p "${RESULT_DIR}"
mkdir -p "${GENERATED_DIR}"
rm -f "${RESULT_FILE}"
rm -rf "${REPORT_DIR}"

if [[ "${USER_CSV}" == "${GENERATED_USER_CSV}" ]]; then
  printf 'userId\n' > "${USER_CSV}"
  for ((i = 1; i <= TOTAL_USERS; i++)); do
    uuidgen | tr '[:upper:]' '[:lower:]' >> "${USER_CSV}"
  done
fi

jmeter -n \
  -t "${SCRIPT_DIR}/waiting-registration-load-test.jmx" \
  -Jhost="${HOST}" \
  -Jport="${PORT}" \
  -JstoreId="${STORE_ID}" \
  -JuserCsv="${USER_CSV}" \
  -Jthreads="${THREADS}" \
  -JrampUp="${RAMP_UP}" \
  -Jloops="${LOOPS}" \
  -JresultFile="${RESULT_FILE}" \
  -l "${RESULT_FILE}" \
  -e \
  -o "${REPORT_DIR}"

echo
echo "JMeter user CSV: ${USER_CSV}"
echo "JMeter result file: ${RESULT_FILE}"
echo "JMeter HTML report: ${REPORT_DIR}/index.html"
