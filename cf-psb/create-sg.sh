# Copyright (c) [2016 - 2017] Dell Inc. or its subsidiaries. All Rights Reserved.
cf create-security-group host-h2 sg.json
cf bind-security-group host-h2 pcfdev-org pcfdev-space
