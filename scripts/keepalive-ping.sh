#!/usr/bin/env bash
# Bounded-retry keepalive probe. A Render free-tier cold boot that eventually
# answers counts as success (with the recovery logged); three failed attempts
# fail the workflow loudly instead of echoing the error away.
#
# Usage: keepalive-ping.sh <label> <url> [extra curl args...]
#   OK_CODES overrides the accepted status list (default: redirects + 2xx).
#   TIMEOUT_SECONDS / ATTEMPTS / SLEEP_SECONDS tune the patience window: a
#   suspended free-tier Java container needs minutes, not seconds, to answer
#   its first request, and a window that short reports a healthy service as
#   unreachable on every scheduled run.
set -u

label="$1"
url="$2"
shift 2

ok_codes="${OK_CODES:-200 204 301 302 307 308}"
attempts="${ATTEMPTS:-4}"
timeout_seconds="${TIMEOUT_SECONDS:-75}"
sleep_seconds="${SLEEP_SECONDS:-30}"
last_code=""

for attempt in $(seq 1 "$attempts"); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -m "$timeout_seconds" "$@" "$url" || true)
  last_code="${code:-curl-error}"
  for ok in $ok_codes; do
    if [ "$code" = "$ok" ]; then
      if [ "$attempt" -gt 1 ]; then
        echo "$label answered HTTP $code after $attempt attempts (cold boot recovered)"
      else
        echo "$label healthy (HTTP $code)"
      fi
      exit 0
    fi
  done
  echo "$label attempt $attempt got HTTP $last_code; retrying in ${sleep_seconds}s..."
  [ "$attempt" -lt "$attempts" ] && sleep "$sleep_seconds"
done

echo "::error::$label unreachable after $attempts attempts (last HTTP $last_code)"
exit 1
