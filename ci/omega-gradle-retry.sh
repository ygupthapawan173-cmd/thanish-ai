#!/usr/bin/env bash
set -euo pipefail
# Retry only transient remote dependency failures; never suppress compiler or test failures.
for attempt in 1 2 3 4; do
  log="$(mktemp)"
  set +e
  gradle --no-daemon --no-configuration-cache --console=plain "$@" 2>&1 | tee "$log"
  status=("${PIPESTATUS[@]}")
  set -e
  if [[ "${status[0]}" -eq 0 && "${status[1]}" -eq 0 ]]; then
    rm -f "$log"; exit 0
  fi
  if [[ "${status[1]}" -ne 0 ]]; then rm -f "$log"; exit 1; fi
  if [[ "$attempt" -eq 4 ]] || ! grep -Eqi "status code (429|502|503|504)|Too Many Requests|Read timed out|Connection reset|Temporary failure in name resolution" "$log"; then
    rm -f "$log"; exit "${status[0]}"
  fi
  rm -f "$log"
  echo "Transient repository/network failure; retry $attempt/3"
  sleep "$((attempt * 25))"
done
exit 1
