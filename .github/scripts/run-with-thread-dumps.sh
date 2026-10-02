#!/usr/bin/env bash
# Runs the command it is given, and once it has run THREAD_DUMP_AFTER seconds (600 unless set), writes the
# threads of every Gradle worker JVM, the test JVMs among them, into the log, then again every five minutes:
# a test that hangs says where.
set -u
after="${THREAD_DUMP_AFTER:-600}"
(
  sleep "$after"
  while true; do
    for pid in $(jps -l | awk '/GradleWorkerMain/ { print $1 }'); do
      echo "::group::Threads of Gradle worker $pid"
      jcmd "$pid" Thread.print || true
      echo "::endgroup::"
    done
    sleep 300
  done
) &
watchdog=$!
status=0
"$@" || status=$?
pkill -P "$watchdog" 2>/dev/null || true
kill "$watchdog" 2>/dev/null || true
exit "$status"
