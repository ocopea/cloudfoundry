// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
"use strict";
var frisby = require('/usr/local/lib/node_modules/frisby');
var httpStatusCode = require('/usr/local/lib/node_modules/http-status-codes');

var psbHost = process.env.psbHost;

function validateMapToStrings(mapName, map) {
  Object.keys(map).forEach(key => {
    if (!(typeof map[key] === "string")) {
      throw new Error(mapName + ' has non-string field ' + key + ". value is " + map[key]);
    }
  });
}

function validateEnum(legalValues, fieldName, field) {
  if (legalValues.indexOf(field) < 0) {
    throw new Error('illegal value for ' + fieldName + ': ' + status);
  }
}

exports.delayUntilRunning = function(seconds, after) {
  frisby
    .create("delay until running")
    .get(`http://${psbHost}/cf-psb-api/state`)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSON({
      state: "RUNNING"
    })
    .retry(seconds, 1000)
    .after(after)
    .toss();
}

exports.getInfo = function(testName) {
  return frisby.create(testName ? testName : "info")
    .get(`http://${psbHost}/cf-psb-api/psb/info`);
}

exports.getInfoSuccessful = function(testName) {
  return exports.getInfo(testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes({
      name: String,
      version: String,
      type: String,
      description: String,
      appServiceIdMaxLength: Number
    });
}

exports.getAppService = function(space, serviceId, testName) {
  return frisby.create(testName ? testName : "appService")
    .get(`http://${psbHost}/cf-psb-api/psb/app-services/${space}/${serviceId}`);
}

exports.getAppServiceSuccessful = function(space, serviceId, testName) {
  return exports.getAppService(space, serviceId, testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes({
      name: String,
      status: function(val) { validateEnum(['stopped', 'starting', 'running', 'error'], 'status', val) },
      statusMessage: String,
      instances: Number,
      psbMetrics: function(val) { validateMapToStrings('psbMetrics', val) },
      entryPointURL: String
    });
}

exports.getAppServiceLogsWebSocket = function(space, serviceId, testName) {
  return frisby.create(testName ? testName : "appServiceLogs")
    .get(`http://${psbHost}/cf-psb-api/psb/app-services/${space}/${serviceId}/logs`);
}

exports.getAppServiceLogsWebSocketSuccessful = function(space, serviceId, testName) {
  return exports.getAppServiceLogsWebSocket(space, serviceId, testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes({
      address: String,
      serialization: String
    });
}

exports.listSpaces = function(testName) {
  return frisby.create(testName ? testName : "listSpaces")
    .get(`http://${psbHost}/cf-psb-api/psb/spaces`);
}

exports.listSpacesSuccessful = function(testName) {
  return exports.listSpaces(testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes('*', {
      name: String,
      properties: function(val) { validateMapToStrings('properties', val) }
    });
}

exports.deployApplicationService = function(manifest, testName) {
  return frisby.create(testName ? testName : "deployApplicationService")
    .post(`http://${psbHost}/cf-psb-api/psb/app-services`, manifest, {json: true});
}

exports.deployApplicationServiceSuccessful = function(manifest, testName) {
  return exports.deployApplicationService(manifest, testName)
    .expectStatus(httpStatusCode.CREATED)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes({
      status: Number,
      message: String
    });
}

exports.stopApp = function(space, serviceId, testName) {
  return frisby.create(testName ? testName : "stopApp")
    .delete(`http://${psbHost}/cf-psb-api/psb/app-services/${space}/${serviceId}`)
    .addHeader('Content-Type', 'application/json');
}

exports.stopAppSuccessful = function(space, serviceId, testName) {
  return exports.stopApp(space, serviceId, testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes({
      status: Number,
      message: String
    });
}

exports.listVersions = function(artifactId, testName) {
  return frisby.create(testName ? testName : "listVersions")
    .get(`http://${psbHost}/cf-psb-api/internal/artifact-registry/${artifactId}`);
}

exports.listVersionsSuccessful = function(artifactId, testName) {
  return exports.listVersions(artifactId, testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/json')
    .expectJSONTypes('*', String);
}

exports.getBits = function(imageName, version, testName) {
  return frisby.create(testName ? testName : "getBits")
    .get(`http://${psbHost}/cf-psb-api/internal/try-bits?imageName=${imageName}&imageVersion=${version}`);
}

exports.getBitsSuccessful = function(imageName, version, testName) {
  return exports.getBits(imageName, version, testName)
    .expectStatus(httpStatusCode.OK)
    .expectHeader('content-type', 'application/octet-stream');
}

exports.storeBits = function(image, imageName, version, testName) {
  return frisby.create(testName ? testName : "storeBits")
    .post(`http://${psbHost}/cf-psb-api/internal/try-bits?imageName=${imageName}&imageVersion=${version}`,
      image, {json: false, headers: {"content-type": "application/octet-stream"}});
}

exports.storeBitsSuccessful = function(image, imageName, version, testName) {
  return exports.storeBits(image, imageName, version, testName)
    .expectStatus(httpStatusCode.CREATED);
}