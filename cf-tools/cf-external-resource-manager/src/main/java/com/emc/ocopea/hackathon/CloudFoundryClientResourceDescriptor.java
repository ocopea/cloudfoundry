// Copyright (c) [2018 - 2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.hackathon;

import com.emc.microservice.resource.ResourceDescriptor;

/**
 * Created by liebea on 2/8/16.
 * Drink responsibly
 */
public class CloudFoundryClientResourceDescriptor implements ResourceDescriptor {

    private final String name;

    public CloudFoundryClientResourceDescriptor(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
