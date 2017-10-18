#!/usr/bin/env bash
pushd .
cd "${0%/*}"
CF_ORG=`cf t | grep "^Org:" | cut -d : -f2 | tr -d '[:space:]'`
CF_SPACE=`cf t | grep "^Space:" | cut -d : -f2 | tr -d '[:space:]'`
CF_API_HOST=`cf t | grep "^API endpoint:" | cut -d / -f3 | cut -d ' ' -f1 | tr -d '[:space:]'`
if [ $# -gt 0 ]; then
  PREFIX=$1
else
  PREFIX=`whoami`
fi

echo
echo "cf org: ${CF_ORG}"
echo "cf space: ${CF_SPACE}"
echo "cf api address: ${CF_API_HOST}"
echo "prefix: ${PREFIX}"
echo

export NAZ_CONFIG_APP="$PREFIX"-conf
export OCOPEA_CONFIG_URL=`cf app "$NAZ_CONFIG_APP" | grep "^urls:" | cut -d : -f2 | cut -d , -f1 | tr -d '[:space:]'`
java -Dorg.jboss.logging.provider=slf4j -jar cf-triple-apps-tester.jar
echo "tested baby"
popd
