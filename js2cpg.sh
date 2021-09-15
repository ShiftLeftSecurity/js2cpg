#!/usr/bin/env sh

dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
scriptpath="$dir/js2cpg/target/universal/stage/bin/js2cpg"

$scriptpath \
    -Dlog4j.configurationFile=$dir/js2cpg/src/main/resources/log4j2.xml \
    $@
