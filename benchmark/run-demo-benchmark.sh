#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home}"
PATH="$JAVA_HOME/bin:$PATH"

exec ./gradlew :app:assembleDemoDebug :benchmark:assemble
