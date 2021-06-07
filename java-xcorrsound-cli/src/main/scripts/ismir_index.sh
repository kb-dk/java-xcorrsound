#!/bin/bash

SCRIPT_DIR=$(dirname "$(readlink -f -- ${BASH_SOURCE[0]})")

source "$SCRIPT_DIR/../conf/appEnv.sh"

if [ -z "$INDEX_MAIN_CLASS" ]; then
    echo "INDEX_MAIN_CLASS has not been set" 1>&2
    exit 1
fi

if [ -z "$APP_CONFIG" ]; then
    echo "APP_CONFIG has not been set" 1>&2
    exit 1
fi

CLASS_PATH="${CLASS_PATH_OVERRIDE:-"$SCRIPT_DIR/../lib/*"}"
JAVA_OPTS=${JAVA_OPTS:-"-Xmx256m -Xms256m"}

exec java $JAVA_OPTS -classpath "$CLASS_PATH" -Dlogback.configurationFile="$SCRIPT_DIR/../conf/logback.xml" -Ddk.kb.applicationConfig="$SCRIPT_DIR/../conf/$APP_CONFIG" "$INDEX_MAIN_CLASS" "$@"

