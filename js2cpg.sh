#!/usr/bin/env sh

SCRIPT_ABS_PATH=$(realpath $0)
SCRIPT_ABS_DIR=$(dirname $SCRIPT_ABS_PATH)

$SCRIPT_ABS_DIR/target/universal/stage/bin/js2cpg -Dlog4j.configurationFile=$SCRIPT_ABS_DIR/src/main/resources/log4j2.xml $@
