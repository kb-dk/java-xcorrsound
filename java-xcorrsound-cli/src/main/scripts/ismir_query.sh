#!/bin/bash

SCRIPT_DIR=$(dirname "$(readlink -f -- ${BASH_SOURCE[0]})")

source "$SCRIPT_DIR/../conf/appEnv.sh"

if [ -z "$QUERY_MAIN_CLASS" ]; then
    echo "QUERY_MAIN_CLASS has not been set" 1>&2
    exit 1
fi

if [ -z "$APP_CONFIG" ]; then
    echo "APP_CONFIG has not been set" 1>&2
    exit 1
fi

CLASS_PATH="${CLASS_PATH_OVERRIDE:-"$SCRIPT_DIR/../lib/*"}"
JAVA_OPTS=${JAVA_OPTS:-"-Xmx256m -Xms256m"}

java $JAVA_OPTS -classpath "$CLASS_PATH" -Dlogback.configurationFile="$SCRIPT_DIR/../conf/logback.xml" -Ddk.kb.applicationConfig="$SCRIPT_DIR/../conf/$APP_CONFIG" "$QUERY_MAIN_CLASS" "$@"


#How to run with modules
#MODULE_PATH="${MODULE_PATH_OVERRIDE:-"$SCRIPT_DIR/../lib"}"
#exec java $JAVA_OPTS --module-path "$MODULE_PATH" -Dlogback.configurationFile="$SCRIPT_DIR/../conf/logback.xml" -Ddk.kb.applicationConfig="$SCRIPT_DIR/../conf/$APP_CONFIG" --module "dk.kb.xcorrsound.cli/$QUERY_MAIN_CLASS" "$@"
