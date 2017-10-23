#!/usr/bin/env bash
# Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
pushd .
cd "${0%/*}"
if [ $# -gt 0 ]; then
  PREFIX=$1
else
  PREFIX=`whoami`
fi

echo
echo "prefix: ${PREFIX}"
echo

OCOPEA_CONFIG_URL=`cf app ${PREFIX}-conf | grep "^urls:" | cut -d : -f2 | cut -d , -f1 | tr -d '[:space:]'`
java -Dorg.jboss.logging.provider=slf4j -jar cf-triple-apps-client.jar delete ${OCOPEA_CONFIG_URL}
for app in $(grep "name:" manifest.yaml | cut -d : -f2 | sed "s/\${PREFIX}/${PREFIX}/g")
do
  cf d `echo "$app" | tr -d '[:space:]'` -f
done
popd