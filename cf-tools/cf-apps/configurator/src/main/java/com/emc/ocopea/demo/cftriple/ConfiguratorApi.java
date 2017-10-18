package com.emc.ocopea.demo.cftriple;

import com.emc.microservice.ServiceConfig;
import com.emc.microservice.blobstore.StandalonePostgresBlobStoreConfiguration;
import com.emc.microservice.postgres.StandalonePostgresDatasourceConfiguration;
import com.emc.microservice.standalone.web.UndertowWebServerConfiguration;
import com.emc.ocopea.demo.model.Service;
import com.emc.ocopea.messaging.PersistentMessagingConfiguration;
import com.emc.ocopea.messaging.PersistentQueueConfiguration;
import com.emc.ocopea.scheduler.PersistentSchedulerConfiguration;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

/**
 * API for the configurator app, used when deploying Ocopea to set/change configuration, create DB schemas and similar
 * tasks.
 */
@Path("configure")
public interface ConfiguratorApi {

    @POST
    @Path("service")
    @Consumes(MediaType.APPLICATION_JSON)
    void configuresService(ServiceConfig configuration);

    @POST
    @Path("blobstore/{blobstoreName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureBlobstore(
            StandalonePostgresBlobStoreConfiguration configuration,
            @PathParam("blobstoreName") String name);

    @POST
    @Path("datasource/{dsName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureDatasource(
            StandalonePostgresDatasourceConfiguration configuration,
            @PathParam("dsName") String name);

    @POST
    @Path("database/{dbName}/schema/{schemaName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureSchema(
            String schemaClassName,
            @PathParam("dbName") String dbName,
            @PathParam("schemaName") String schemaName);

    @POST
    @Path("webServer/{webServerName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureWebServer(
            UndertowWebServerConfiguration configuration,
            @PathParam("webServerName") String name);

    @POST
    @Path("queue/{queueName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureQueue(
            PersistentQueueConfiguration configuration,
            @PathParam("queueName") String name);

    @POST
    @Path("messaging/{messagingName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureMessaging(
            PersistentMessagingConfiguration configuration,
            @PathParam("messagingName") String name);

    @POST
    @Path("scheduler/{schedulerName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureScheduler(
            PersistentSchedulerConfiguration configuration,
            @PathParam("schedulerName") String name);

    @POST
    @Path("external/{externalServiceName}")
    @Consumes(MediaType.APPLICATION_JSON)
    void configureExternal(
            Map<String, String> configuration,
            @PathParam("externalServiceName") String name);

    @GET
    @Path("environment/services/{type}")
    @Produces(MediaType.APPLICATION_JSON)
    List<Service> getBoundServices(@PathParam("type") String type);
}
