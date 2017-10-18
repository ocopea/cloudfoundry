// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.webclient.WebAPIResolver;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Created by liebea on 1/25/17.
 * Drink responsibly
 */
public class MavenRepositoryArtifactReader {
    private final String url;
    private final WebAPIResolver webAPIResolver;

    public MavenRepositoryArtifactReader(String url, WebAPIResolver webAPIResolver) {
        this.url = url;
        this.webAPIResolver = webAPIResolver;
    }

    /***
     * Downloading artifact from a maven repository.
     */
    public Response readArtifact(String groupId, String artifactId, String version, String type, String classifier) {
        final String groupId1 = groupId.replaceAll("\\.", "/");
        if (classifier == null || classifier.isEmpty()) {

            return webAPIResolver.getWebAPI(url, DownloadMavenArtifactWebApi.class).download(groupId1, artifactId,
                    version, type);
        } else {
            return webAPIResolver.getWebTarget(url + "/" + groupId1 + "/" + artifactId + "/" + version + "/"
                    + artifactId + "-" + version + "-" + classifier + "." + type).request().get();
        }
    }

    public interface DownloadMavenArtifactWebApi {

        @Produces("*/*")
        @Path("/{groupId}/{artifactId}/{version}/{artifactId}-{version}.{type}")
        @GET
        Response download(@PathParam("groupId") String groupId, @PathParam("artifactId") String artifactId,
                @PathParam("version") String version, @PathParam("type") String type);
    }
}
