// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.Context;
import com.emc.microservice.MicroServiceApplication;
import com.emc.ocopea.util.io.StreamUtil;

import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;

/**
 * Created by liebea on 6/28/16.
 * Drink responsibly
 */
public class CloudFoundryPSBInternalResource implements CloudFoundryPSBInternalAPI {

    private CfPsbSingleton cfPsbSingleton;

    @javax.ws.rs.core.Context
    public void setApplication(Application application) {
        Context context = ((MicroServiceApplication) application).getMicroServiceContext();
        cfPsbSingleton = context.getSingletonManager().getManagedResourceByName("cf").getInstance();
    }

    @Override
    public Collection<String> listVersions(@PathParam("artifactId") String artifactId) {
        return cfPsbSingleton.availableVersions(artifactId);
    }

    @Override
    public Response getBits(String imageName, String imageVersion) {
        StreamingOutput so = output -> {
            try (InputStream inputStream = new FileInputStream(
                    cfPsbSingleton.tryBits(imageName, imageVersion).toFile())) {
                StreamUtil.copy(inputStream, output);
            }
        };
        return Response.ok(so).build();
    }

    @Override
    public Response storeBits(InputStream inputStream, String imageName, String imageVersion) {
        cfPsbSingleton.storeFakeImage(imageName, inputStream);
        return Response.created(null).build();
    }

}
