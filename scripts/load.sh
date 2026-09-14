#!/usr/bin/env bash
set -euo pipefail
: "${TOKEN:?Set customer JWT}"
: "${SHOW_ID:?Set a disposable test show UUID}"
export RUN_ID=$(date +%s)
k6 run --summary-export target/load-results.json scripts/load.js
