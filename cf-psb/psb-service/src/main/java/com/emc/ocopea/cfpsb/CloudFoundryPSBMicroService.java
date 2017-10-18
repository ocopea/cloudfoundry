// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.MicroService;
import com.emc.microservice.MicroServiceInitializationHelper;
import com.emc.ocopea.hackathon.CloudFoundryClientResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by liebea on 6/7/16.
 * Drink responsibly
 */
public class CloudFoundryPSBMicroService extends MicroService {
    public static final String SERVICE_BASE_URI = "cf-psb";
    private static final String SERVICE_NAME = "CloudFoundry PaaS Broker";
    private static final String SERVICE_DESCRIPTION = "Cloud Foundry PSB";
    private static final int SERVICE_VERSION = 1;
    private static final Logger logger = LoggerFactory.getLogger(CloudFoundryPSBMicroService.class);

    public CloudFoundryPSBMicroService() {
        super(SERVICE_NAME, SERVICE_BASE_URI, SERVICE_DESCRIPTION, SERVICE_VERSION, logger,
                new MicroServiceInitializationHelper()
                        .withExternalResource(new CloudFoundryClientResourceDescriptor("cf"))
                        .withParameter("create-sg", "Should psb create sg per dsb", String.valueOf(true), true)
                        .withSingleton("cf", "cf singleton", CfPsbSingleton.class)
                        .withSingleton(FluxCancellationsManager.class)
                        .withRestResource(CloudFoundryPSBResource.class, "PSB Web API Implementation")
                        .withRestResource(CloudFoundryPSBInternalResource.class, "Internal Resource")
                        .withWebSocket(CfPsbLogsWebSocket.class).withBlobStore("hack-images-bank") // temporary  for now
        );

    }

}
