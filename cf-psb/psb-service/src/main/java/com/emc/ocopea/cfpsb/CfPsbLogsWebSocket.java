// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.Context;
import com.emc.ocopea.hackathon.CFConnection;
import com.emc.ocopea.hackathon.CloudFoundryClientManagedResource;
import com.emc.ocopea.hackathon.CloudFoundryClientResourceDescriptor;
import com.emc.ocopea.psb.PSBLogMessageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.doppler.Envelope;
import org.cloudfoundry.doppler.EventType;
import org.cloudfoundry.doppler.LogMessage;
import org.cloudfoundry.doppler.MessageType;
import org.cloudfoundry.doppler.StreamRequest;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Cancellation;
import reactor.core.publisher.Flux;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;

@ServerEndpoint(value = "/psb/app-services/{space}/{appServiceId}/logs")
public class CfPsbLogsWebSocket extends Endpoint {
    private static final Logger log = LoggerFactory.getLogger(CfPsbLogsWebSocket.class);
    private static final ObjectMapper objMapper = new ObjectMapper();
    private boolean keepTrying = true;
    private static final String org = System.getProperty("OCOPEA_CF_ORG");

    /***
     * Called when a client opens the logging websocket. It connects to CF's event flux and emits all log message
     * events on the websocket.
     */
    @Override
    public void onOpen(Session session, EndpointConfig config) {
        final String space = session.getPathParameters().get("space");
        final String appServiceId = session.getPathParameters().get("appServiceId");

        log.info("log websocket opened for space={} appServiceId={}. session={}", space, appServiceId, session.getId());

        Context context = (Context) session.getUserProperties().get("ctx");
        CloudFoundryClientManagedResource cfResource = context
                .getManagedResourceByDescriptor(CloudFoundryClientResourceDescriptor.class, "cf");

        // retrieve the service app id
        CFConnection cfConnection = cfResource.getConnection(org, space);
        String appId = null;
        while (keepTrying) {
            appId = retrieveAppId(cfConnection, appServiceId);
            if (appId == null) {
                try {
                    log.info("failed get app id from cloudfoundry. appServiceId={}. retrying in 1 second",
                            appServiceId);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // don't care
                }
            } else {
                break;
            }
        }

        // stream events for the app
        DopplerClient dopplerClient = cfResource.getCfDopplerClient();
        Flux<Envelope> envelopeFlux = dopplerClient.stream(StreamRequest.builder().applicationId(appId).build());
        Cancellation cancellation = envelopeFlux.filter(e -> e.getEventType().equals(EventType.LOG_MESSAGE))
                .map(Envelope::getLogMessage).map(e -> convert(e, appServiceId)).subscribe(logMessage -> {
                    try {
                        emit(session, logMessage);
                    } catch (IOException ex) {
                        log.warn("failed emitting log message", ex);
                    }
                });

        getFluxCancellationManager(session).add(session.getId(), cancellation);
    }

    /**
     * given an appServiceId returns the corresponding CF app id, or null if the application doesn't exist yet (or
     * another error occurred)
     */
    private String retrieveAppId(CFConnection cfConnection, String appServiceId) {
        String appId = null;
        try {
            appId = cfConnection.getCloudFoundryOperations().applications()
                    .get(GetApplicationRequest.builder().name(appServiceId).build()).map(ApplicationDetail::getId)
                    .block();
        } catch (IllegalArgumentException ex) {
            // application doesn't exist yet, ignoring
        } catch (Exception ex) {
            log.error("failed retrieving app id from cloudfoundry. appServiceId=" + appServiceId, ex);
        }
        return appId;
    }

    FluxCancellationsManager getFluxCancellationManager(Session session) {
        return ((Context) session.getUserProperties().get("ctx")).getSingletonManager()
                .getManagedResourceByName(FluxCancellationsManager.class.getSimpleName()).getInstance();
    }

    private void emit(Session session, PSBLogMessageDTO logMessage) throws IOException {
        session.getBasicRemote().sendText(objMapper.writeValueAsString(logMessage));
    }

    private PSBLogMessageDTO convert(LogMessage cfLogMessage, String appServiceId) {
        return new PSBLogMessageDTO(cfLogMessage.getMessage(), cfLogMessage.getTimestamp() / 1000000,
                cfLogMessage.getMessageType() == MessageType.OUT ? PSBLogMessageDTO.MessageType.out
                        : PSBLogMessageDTO.MessageType.err,
                appServiceId);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        keepTrying = false;
        getFluxCancellationManager(session).cancel(session.getId());
        log.info("session {} closed, {}", session.getId(), closeReason.getReasonPhrase());
    }
}
