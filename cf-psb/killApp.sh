# Copyright (c) [2016 - 2017] Dell Inc. or its subsidiaries. All Rights Reserved.
cf d hackathon-cf-h1 -f
cf ds hack-docs-hackathon-cf-h1 -f
cf ds hackathon-db-hackathon-cf-h1 -f
cf delete-security-group -f hackathon-cf-h1-sg
