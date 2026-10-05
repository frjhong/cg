#!/usr/bin/env sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mode=${1:-ai}
usage() {
  printf '%s\n' 'Usage: scripts/run.sh [ai|demo]' \
    '  ai    Process reports using AI_API_KEY (default).' \
    '  demo  Replay only the bundled 0916.docx and 0930.docx samples.'
}
if [ "$#" -gt 1 ]; then usage >&2; exit 2; fi
case "$mode" in
  help|--help|-h) usage; exit 0 ;;
  ai)
    if [ -z "${AI_API_KEY:-}" ]; then
      printf '%s\n' 'Set AI_API_KEY before starting AI mode, or use scripts/run.sh demo.' >&2
      exit 2
    fi
    export AI_FIXTURE=
    ;;
  demo)
    export AI_FIXTURE="$project_dir/src/test/resources/sample-plan.json"
    if [ ! -f "$AI_FIXTURE" ]; then printf '%s\n' 'Bundled sample-plan.json is missing.' >&2; exit 2; fi
    ;;
  *) usage >&2; exit 2 ;;
esac

if [ -n "${JAVA_HOME:-}" ]; then
  java_cmd="$JAVA_HOME/bin/java"
  if [ ! -x "$java_cmd" ]; then printf '%s\n' 'JAVA_HOME must point to a valid JDK 21 or newer.' >&2; exit 2; fi
else
  java_cmd=$(command -v java) || { printf '%s\n' 'Install JDK 21 or newer and configure JAVA_HOME or PATH.' >&2; exit 2; }
fi

cd "$project_dir"
if [ -f target/weekly-report-1.0.0.jar ]; then
  app_jar=target/weekly-report-1.0.0.jar
elif [ -f artifacts/weekly-report-1.0.0.jar ]; then
  app_jar=artifacts/weekly-report-1.0.0.jar
else
  printf '%s\n' 'No JAR found; building with Maven Wrapper (including tests).'
  "$project_dir/mvnw" -B package
  app_jar=target/weekly-report-1.0.0.jar
fi
printf 'Starting weekly-report (%s). Stop with Ctrl+C.\n' "$mode"
exec "$java_cmd" -jar "$app_jar"
