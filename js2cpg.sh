#!/usr/bin/env sh

SCRIPT_ABS_PATH=$(readlink -f "$0")
SCRIPT_ABS_DIR=$(dirname "$SCRIPT_ABS_PATH")

exec "$SCRIPT_ABS_DIR/target/universal/stage/bin/js2cpg" "-Dlog4j.configurationFile=$SCRIPT_ABS_DIR/src/main/resources/log4j2.xml" "$@"
