#!/bin/bash

dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
scriptpath="$dir/target/universal/stage/bin/js2cpg"

$scriptpath \
    -Dlog4j.configurationFile=$dir/src/main/resources/log4j2.xml \
    $@
