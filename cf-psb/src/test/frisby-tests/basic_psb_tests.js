// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
"use strict";
var psbUtils = require('./psb_test_utils');
var fs = require('fs');
var httpStatusCode = require('/usr/local/lib/node_modules/http-status-codes');

var space = process.env.psbSpace;
if (!('psbSpace' in process.env)) {
  space = '';
}
var exampleAppLocation = process.env.exampleAppLocation;

exports.test = function() {
  testInfo();

  testUpload(function() {
    var spacesCall = psbUtils.listSpacesSuccessful();
    if (space == '') {
      spacesCall = spacesCall.expectJSON('?', {name: space});
    }
    spacesCall
      .afterJSON(function(json) {
        if (space != '') {
          space = json.map((x) => x.name).find((x) => x.startsWith("CNDP-") && x.endsWith("-Staging"));
        }
        testDeploy(space);
      })
      .toss();
  });
}

function testInfo() {
  psbUtils.getInfoSuccessful()
    .expectJSON({
      name: "cf-psb",
      version: "1",
      type: "cf",
      description: "Cloud Foundry PSB",
      appServiceIdMaxLength: 50
    })
    .toss();
  console.log("info-call-successful done");
}

function testUpload(after) {
  if (fs.statSync(exampleAppLocation).isDirectory()) {
    var file = fs.readdirSync(appDir).find((x) => x.endsWith("-cf-app.jar"));
    expect(file).toBeDefined();
    exampleAppLocation = exampleAppLocation + "/" + file;
  }
  var data = fs.readFileSync(exampleAppLocation);
  psbUtils
    .storeBitsSuccessful(data, "test", "1.0", "upload")
    .after(function() {
      psbUtils
        .listVersionsSuccessful("test", "upload")
        .expectJSON(["1.0"])
        .toss();
      after();
    })
    .toss();
}

function testDeploy(space) {
  var appServiceId = 'service_test_' + Math.floor(Math.random() * Math.pow(2,64));
  psbUtils.deployApplicationServiceSuccessful(
      {
        appServiceId: appServiceId,
        space: space,
        imageName: "test",
        imageVersion: "1.0",
        artifactRegistryType: "customRest"
      }, "deploy"
    )
    .expectJSON({
      status: 0,
      message: "Yey"
    })
    .after(function() {
      psbUtils.getAppServiceSuccessful(space, appServiceId, "deploy")
        .expectJSON({
          status: 'running',
          statusMessage: 'STARTED',
          instances: 1,
          psbMetrics: {}
        })
        .after(function() {
          psbUtils.stopAppSuccessful(space, appServiceId, "deploy")
            .expectJSON({
              status: 0,
              message: 'Stopped, not deleted'
            })
            .after(function() {
              psbUtils.getAppService(space, appServiceId, "deploy")
                .expectStatus(httpStatusCode.NOT_FOUND)
                .retry(30, 1000)
                .toss();
            })
            .timeout(60 * 1000)
            .toss();
        })
        .retry(30, 1000)
        .toss();
    })
    .timeout(60 * 1000)
    .toss();
}