#!/usr/bin/env bash
# Retry only confirmed transient remote artifact download failures.
# Compilation, verification and assertion failures fail immediately.
set -euo pipefail
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3 4; do
  set +e
  gradle "$@" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  set -e
  if [[ "$status" == 0 ]]; then exit 0; fi
  if [[ "$attempt" == 4 ]] || ! grep -Eq 'Received status code (429|502|503|504) from server' "$log" || ! grep -Eq 'Could not (GET|HEAD|resolve all files|download|get resource)' "$log"; then
    exit "$status"
  fi
  if grep -Eq 'tests completed, [1-9][0-9]* failed|Compilation error|AssertionError' "$log"; then
    exit "$status"
  fi
  delay=$((30 * 2 ** (attempt - 1)))
  echo "::warning::Transient Maven download failure; retrying Gradle after ${delay}s (attempt $attempt/4)."
  sleep "$delay"
done
