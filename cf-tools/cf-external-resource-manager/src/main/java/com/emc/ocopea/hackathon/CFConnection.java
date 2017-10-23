// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
 
package com.emc.ocopea.hackathon;

import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.operations.CloudFoundryOperations;

/**
 * Cloud foundry connection object.
 */
public class CFConnection {
    private final CloudFoundryOperations cloudFoundryOperations;
    private final CloudFoundryClient cloudFoundryClient;
    private final String org;
    private final String spaceId;

    CFConnection(CloudFoundryOperations cloudFoundryOperations, CloudFoundryClient cloudFoundryClient, String org,
            String spaceId) {
        this.cloudFoundryOperations = cloudFoundryOperations;
        this.cloudFoundryClient = cloudFoundryClient;
        this.org = org;
        this.spaceId = spaceId;
    }

    public CloudFoundryOperations getCloudFoundryOperations() {
        return cloudFoundryOperations;
    }

    public CloudFoundryClient getCloudFoundryClient() {
        return cloudFoundryClient;
    }

    public String getOrg() {
        return org;
    }

    public String getSpaceId() {
        return spaceId;
    }
}
