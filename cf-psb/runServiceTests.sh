#!/usr/bin/env bash
# Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
if [ "$2" != "" ]; then
    NAME_DEF="--name $2"
else
    NAME_DEF=""
fi

if [ "$3" != "" ]; then
    SPACE_CONF="--config psbSpace $3"
else
    SPACE_CONF=""
fi

if [ "$4" != "" ]; then
    if [ -d "$4" ]; then
        EXAMPLE_APP_DIR="$4"
        EXAMPLE_APP_CONF="--config exampleAppLocation /root/app"
    else
        EXAMPLE_APP_DIR=$(dirname "$4")
        EXAMPLE_APP_CONF="--config exampleAppLocation /root/app/"$(basename "$4")
    fi
else
    EXAMPLE_APP_DIR="../../../cf-testing-dependencies/target"
    EXAMPLE_APP_CONF="--config exampleAppLocation /root/app"
fi

docker run -v $(readlink -f src/test/frisby-tests):/root/service-tests/ -v $(readlink -f "$EXAMPLE_APP_DIR"):/root/app -w /root/service-tests $NAME_DEF -t cndp-docker.artifactory.cec.lab.emc.com/cndp-test-tools /bin/sh -c "jasmine-node tests_spec.js --config psbHost $1 $SPACE_CONF $EXAMPLE_APP_CONF"