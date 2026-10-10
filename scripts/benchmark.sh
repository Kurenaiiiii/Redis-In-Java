#!/usr/bin/env bash
#
# Compares Redis in Java against the real redis-server built in .tools/
# using redis-benchmark. Both run with persistence off.
#
# Usage: scripts/benchmark.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Local build output if present, otherwise system-wide tools.
if [ -x "$ROOT/.tools/bin/redis-server" ]; then
  TOOLS="$ROOT/.tools/bin"
else
  TOOLS=""
fi
redis_server() { if [ -n "$TOOLS" ]; then "$TOOLS/redis-server" "$@"; else redis-server "$@"; fi; }
redis_cli() { if [ -n "$TOOLS" ]; then "$TOOLS/redis-cli" "$@"; else redis-cli "$@"; fi; }
redis_benchmark() { if [ -n "$TOOLS" ]; then "$TOOLS/redis-benchmark" "$@"; else redis-benchmark "$@"; fi; }
REAL_PORT=7789
OUR_PORT=6399

# A full JDK is needed for compilation (javac + ct.sym, not a JRE).
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  : # JAVA_HOME already points at a full JDK
elif [ -x /usr/lib/jvm/bellsoft-java25.x86_64/bin/javac ]; then
  export JAVA_HOME=/usr/lib/jvm/bellsoft-java25.x86_64
fi

echo "== building Redis in Java"
mvn -q -B -f "$ROOT/pom.xml" compile

echo "== starting real redis on $REAL_PORT"
redis_server --port "$REAL_PORT" --save '' --appendonly no \
  --logfile /tmp/opencode/bench-real.log --daemonize yes
echo "== starting Redis in Java on $OUR_PORT"
java -cp "$ROOT/target/classes" dev.redisinjava.Main --port "$OUR_PORT" \
  > /tmp/opencode/bench-ours.log 2>&1 &
OUR_PID=$!

cleanup() {
  kill "$OUR_PID" 2>/dev/null || true
  redis_cli -p "$REAL_PORT" shutdown nosave 2>/dev/null || true
}
trap cleanup EXIT

wait_for() {
  for _ in $(seq 1 50); do
    if redis_cli -p "$1" ping 2>/dev/null | grep -q PONG; then
      return 0
    fi
    sleep 0.1
  done
  echo "server on port $1 did not start" >&2
  return 1
}
wait_for "$REAL_PORT"
wait_for "$OUR_PORT"

run_case() {
  local label="$1"; shift
  echo "-- $label"
  REAL_RPS=$(redis_benchmark -p "$REAL_PORT" -n 100000 -c 50 -q "$@" \
    | tr '\r' '\n' | grep -o '[0-9][0-9.]* requests per second' | head -1 | awk '{print $1}')
  OUR_RPS=$(redis_benchmark -p "$OUR_PORT" -n 100000 -c 50 -q "$@" \
    | tr '\r' '\n' | grep -o '[0-9][0-9.]* requests per second' | head -1 | awk '{print $1}')
  RATIO=$(awk "BEGIN {printf \"%.0f\", ($OUR_RPS/$REAL_RPS)*100}")
  printf '%-22s real=%10s/s  ours=%10s/s  ours/real=%s%%\n' \
    "$label" "$REAL_RPS" "$OUR_RPS" "$RATIO"
  echo "$label | $REAL_RPS | $OUR_RPS | ${RATIO}%" >> /tmp/opencode/bench-table.txt
}

rm -f /tmp/opencode/bench-table.txt
echo
echo "== results (100k requests, 50 clients)"
run_case "SET" -t set
run_case "GET" -t get
run_case "LPUSH" -t lpush
run_case "SET pipelined (P=16)" -t set -P 16
echo
echo "== summary table"
printf '%-22s | %-12s | %-12s | %s\n' "test" "real rps" "ours rps" "ours/real"
cat /tmp/opencode/bench-table.txt | while IFS='|' read -r a b c d; do
  printf '%-22s | %-12s | %-12s | %s\n' "$a" "$b" "$c" "$d"
done
