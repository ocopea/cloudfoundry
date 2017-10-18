// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.Context;
import com.emc.microservice.MicroService;
import com.emc.microservice.MicroServiceApplication;
import com.emc.microservice.standalone.web.LocationHeaderFilter;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.psb.DeployAppServiceManifestDTO;
import com.emc.ocopea.psb.DeployAppServiceResponseDTO;
import com.emc.ocopea.psb.PSBAppServiceInstanceDTO;
import com.emc.ocopea.psb.PSBInfoDTO;
import com.emc.ocopea.psb.PSBLogsWebSocketDTO;
import com.emc.ocopea.psb.PSBSpaceDTO;
import com.emc.ocopea.psb.PSBWebAPI;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriInfo;
import java.util.List;

/**
 * Created by liebea on 6/7/16.
 * Drink responsibly
 */
public class CloudFoundryPSBResource implements PSBWebAPI {

    private static final int CF_APP_NAME_MAX_LENGTH = 50;
    private PSBInfoDTO psbInfo;
    private CfPsbSingleton cfPsbSingleton;

    @javax.ws.rs.core.Context
    private UriInfo uriInfo;

    @javax.ws.rs.core.Context
    private HttpServletRequest request;

    @NoJavadoc
    @javax.ws.rs.core.Context
    public void setApplication(Application application) {
        Context context = ((MicroServiceApplication) application).getMicroServiceContext();
        MicroService serviceDescriptor = context.getServiceDescriptor();
        psbInfo = new PSBInfoDTO(serviceDescriptor.getIdentifier().getShortName(),
                Integer.toString(serviceDescriptor.getVersion()), "cf", serviceDescriptor.getDescription(),
                CF_APP_NAME_MAX_LENGTH);

        cfPsbSingleton = context.getSingletonManager().getManagedResourceByName("cf").getInstance();
    }

    @Override
    public PSBInfoDTO getPSBInfo() {
        return psbInfo;
    }

    @Override
    public PSBAppServiceInstanceDTO getAppService(@PathParam("space") String space,
            @PathParam("appServiceId") String appServiceId) {

        return cfPsbSingleton.getAppStatus(space, appServiceId);
    }

    @Override
    public PSBLogsWebSocketDTO getAppServiceLogsWebSocket(@PathParam("space") String space,
            @PathParam("appServiceId") String appServiceId) {
        return new PSBLogsWebSocketDTO(uriInfo.getAbsolutePathBuilder().scheme("ws").build().toString(), "json");
    }

    @Override
    public DeployAppServiceResponseDTO deployApplicationService(DeployAppServiceManifestDTO appServiceManifest) {
        try {
            cfPsbSingleton.deploy(appServiceManifest);
            request.setAttribute(LocationHeaderFilter.REQUEST_CONTEXT_KEY, uriInfo.getPath() + "/"
                    + appServiceManifest.getSpace() + "/" + appServiceManifest.getAppServiceId());
            return new DeployAppServiceResponseDTO(0, "Yey");
        } catch (Exception e) {
            if (e instanceof WebApplicationException) {
                throw e;
            } else {
                throw new InternalServerErrorException(
                        "Failed deploying app " + appServiceManifest.getAppServiceId() + " - " + e.getMessage(), e);
            }
        }
    }

    @Override
    public DeployAppServiceResponseDTO stopApp(@PathParam("space") String space,
            @PathParam("appServiceId") String appServiceId) {
        cfPsbSingleton.stop(space, appServiceId);
        return new DeployAppServiceResponseDTO(0, "Stopped, not deleted");
    }

    @Override
    public List<PSBSpaceDTO> listSpaces() {
        return cfPsbSingleton.listSpaces();
    }
}
