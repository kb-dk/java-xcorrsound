#!/bin/sh

SCRIPT_DIR=$(dirname $(readlink -f $0))

java -classpath "$SCRIPT_DIR/../conf:$SCRIPT_DIR/../lib/*" \
   dk.kb.template.TemplateMain -c $SCRIPT_DIR/../conf/config.properties

