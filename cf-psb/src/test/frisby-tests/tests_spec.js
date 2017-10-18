// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
"use strict";
var basicTests = require('./basic_psb_tests');
var psbUtils = require('./psb_test_utils');

psbUtils.delayUntilRunning(30, () => {
  basicTests.test();
});
