#!/usr/bin/env bash
# Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.

# IaaS domain patterns
EMCIT_DOMAIN="isus"
AZURE_DOMAIN="dellcloudnative"

# Java buildpacks for CF on an IaaS
JAVA_EMCIT="java_buildpack_offline_quondam"
JAVA_AZURE="java_buildpack_offline"
JAVA_BUILDPACK=""

EXIT_CODE=0

pushd .
cd "${0%/*}"
CF_ORG=`cf t | grep -i "^Org:" | cut -d : -f2 | tr -d '[:space:]'`
CF_SPACE=`cf t | grep -i "^Space:" | cut -d : -f2 | tr -d '[:space:]'`
CF_API_HOST=`cf t | grep -i "^API endpoint:" | cut -d / -f3 | cut -d ' ' -f1 | tr -d '[:space:]'`
UAA_HOST="${CF_API_HOST/api./uaa.}"
if [ $# -gt 0 ]; then
  PREFIX=$1
else
  PREFIX=`whoami`
fi

if [[ $CF_API_HOST == *$EMCIT_DOMAIN* ]]; then
    JAVA_BUILDPACK=$JAVA_EMCIT
    echo "Using $JAVA_BUILDPACK build pack."
fi

if [[ $CF_API_HOST == *$AZURE_DOMAIN* ]]; then
    JAVA_BUILDPACK=$JAVA_AZURE
    echo "Using $JAVA_BUILDPACK build pack."
fi

if [[ $JAVA_BUILDPACK == "" ]]; then
    echo "Could not find a valid build pack."
    exit 1
fi

# Modify permission on executables
chmod 777 *.sh
chmod 777 *crb-fs*/*

echo
echo "cf org: ${CF_ORG}"
echo "cf space: ${CF_SPACE}"
echo "cf api address: ${CF_API_HOST}"
echo "prefix: ${PREFIX}"
echo "UAA Host: ${UAA_HOST}"
echo

sed -e "s/\${CF_ORG}/${CF_ORG}/g" -e "s/\${CF_SPACE}/${CF_SPACE}/g" -e "s/\${CF_API_HOST}/${CF_API_HOST}/g" -e "s/\${PREFIX}/${PREFIX}/g" -e "s/\${UAA_HOST}/${UAA_HOST}/g" < manifest.yaml > updated-manifest.yaml
awk 'BEGIN {
        applications = 0;
        general = "";
        app = "";
        name = "";
    }
    /^- .*/ {
        if (applications && name != "") {
            print (general app) > name;
            app ="";
        }
    }
    /^- name:.*/ {
        name = "temp-" $3 "-manifest.yaml";
    }
    {
        if (applications)
            app = (app "\n" $0);
        else
            general = (general "\n" $0)
    }
    /^applications:.*/ {
        applications = 1;
    }
    END {
        print (general app) > name;
    }' updated-manifest.yaml
if [ -f "temp-${PREFIX}-configurator-manifest.yaml" ] && [ -f "temp-${PREFIX}-conf-manifest.yaml" ]; then
    OCOPEA_CONFIG_URL=`cf app ${PREFIX}-conf | grep -E "^urls|^routes" | cut -d : -f2 | cut -d , -f1 | tr -d '[:space:]'`
    if [ -z ${OCOPEA_CONFIG_URL+foo} ]; then
        java -Dorg.jboss.logging.provider=slf4j -jar cf-triple-apps-client.jar delete ${OCOPEA_CONFIG_URL}
    fi

    for app in temp-*-manifest.yaml; do
        if [[ $app != *"crb-fs"* ]]; then
            echo "$app: Java based app"
            cf push -f $app -b $JAVA_BUILDPACK --no-start &
        else
            echo "$app: Non-Java based app"
            cf push -f $app --no-start &
        fi
    done
    wait

    export SCHEMA_PREFIX=${PREFIX}
    export CF_API_HOST=${CF_API_HOST}
    NAZ_CONFIGURATOR_URL=`cf app ${PREFIX}-configurator | grep -E "^urls|^routes" | cut -d : -f2 | cut -d , -f1 | tr -d '[:space:]'`
    OCOPEA_CONFIG_URL=`cf app ${PREFIX}-conf | grep -E "^urls|^routes" | cut -d : -f2 | cut -d , -f1 | tr -d '[:space:]'`

    cf start ${PREFIX}-configurator
    java -Dorg.jboss.logging.provider=slf4j -jar cf-triple-apps-client.jar preconf ${NAZ_CONFIGURATOR_URL}

    cf start ${PREFIX}-conf
    java -Dorg.jboss.logging.provider=slf4j -jar cf-triple-apps-client.jar conf ${NAZ_CONFIGURATOR_URL}

    cf a | grep "^${PREFIX}-" | cut -d ' ' -f1 | grep -v "^${PREFIX}-conf$" | grep -v "^${PREFIX}-configurator$" | xargs -P 10 -I app cf start app
    java -Dorg.jboss.logging.provider=slf4j -jar cf-triple-apps-client.jar bind ${OCOPEA_CONFIG_URL}
    for app in temp-*-manifest.yaml; do
        APP_NAME=$(awk '!/#/ {print $0;}' $app | awk '/name/ {split($0, a, ":"); print a[2];}')
        cf a | grep $APP_NAME | grep started | grep -q '1/1'
        if [ $? -ne 0 ]; then
            echo $APP_NAME "not started"
            EXIT_CODE=1
            break
        fi
    done
    cf delete -f ${PREFIX}-configurator
else
    echo "missing configurator or conf application"
    EXIT_CODE=1
fi
rm updated-manifest.yaml
rm temp-*-manifest.yaml
if [ $EXIT_CODE -eq 0 ]; then
    echo "http://"`cf app ${PREFIX}-orcs | grep -E "^urls|^routes" | cut -d : -f2 | cut -d , -f1 | tr -d '[:space:]'`"/hub-web-api/html/nui/index.html"
else
    echo "Failed to deploy applications properly!"
fi
popd
exit $EXIT_CODE
