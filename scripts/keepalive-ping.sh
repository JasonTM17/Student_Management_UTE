#!/usr/bin/env bash
# Bounded-retry keepalive probe. A Render free-tier cold boot that eventually
# answers counts as success (with the recovery logged); three failed attempts
# fail the workflow loudly instead of echoing the error away.
#
# Usage: keepalive-ping.sh <label> <url> [extra curl args...]
#   OK_CODES overrides the accepted status list (default: redirects + 2xx).
set -u

label="$1"
url="$2"
shift 2

ok_codes="${OK_CODES:-200 204 301 302 307 308}"
last_code=""

for attempt in 1 2 3; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -m 30 "$@" "$url" || true)
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
  echo "$label attempt $attempt got HTTP $last_code; retrying in 20s..."
  [ "$attempt" -lt 3 ] && sleep 20
done

echo "::error::$label unreachable after 3 attempts (last HTTP $last_code)"
exit 1
