// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Collection;

/**
 * REST interface for cf-psb specific operations. Must be separated from
 * implementation to allow RESTEasy to create a client.
 */
@Path("internal")
public interface CloudFoundryPSBInternalAPI {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("artifact-registry/{artifactId}")
    Collection<String> listVersions(@PathParam("artifactId") String artifactId);

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("try-bits")
    Response getBits(@QueryParam("imageName") String imageName, @QueryParam("imageVersion") String imageVersion);

    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Path("try-bits")
    Response storeBits(InputStream inputStream, @QueryParam("imageName") String imageName,
            @QueryParam("imageVersion") String imageVersion);
}
