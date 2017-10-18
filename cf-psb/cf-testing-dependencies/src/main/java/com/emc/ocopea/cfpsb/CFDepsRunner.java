// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.dpa.dev.DevResourceProvider;
import com.emc.microservice.MicroService;
import com.emc.microservice.MicroServiceApplication;
import com.emc.microservice.MicroServiceInitializationHelper;
import com.emc.microservice.blobstore.PGBlobstoreSchemaBootstrap;
import com.emc.microservice.blobstore.StandalonePostgresBlobStoreConfiguration;
import com.emc.microservice.bootstrap.AbstractSchemaBootstrap;
import com.emc.microservice.bootstrap.SchemaBootstrapRunner;
import com.emc.microservice.configuration.ConfigurationMicroservice;
import com.emc.microservice.configuration.bootstrap.ConfigurationSchemaBootstrap;
import com.emc.microservice.configuration.client.RemoteConfigurationClient;
import com.emc.microservice.registry.ServiceRegistryImpl;
import com.emc.microservice.runner.MicroServiceRunner;
import com.emc.microservice.webclient.WebAPIResolver;
import com.emc.ocopea.demo.CFServices;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.util.PostgresUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Application;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CFDepsRunner {
    private static final Logger log = LoggerFactory.getLogger(CFDepsRunner.class);

    @NoJavadoc
    public static void main(String[] args) throws IOException, SQLException {
        Map<String, AbstractSchemaBootstrap> schemaMap = new HashMap<>();
        schemaMap.put(ConfigurationMicroservice.CONFIG_DB, new ConfigurationSchemaBootstrap());
        new MicroServiceRunner().run(new DevResourceProvider(schemaMap), new ConfigurationMicroservice(),
                new AdditionalDepsMicroservice());
    }

    private static class AdditionalDepsMicroservice extends MicroService {
        private AdditionalDepsMicroservice() {
            super("cf-psb dependencies", "additional-deps", "Service supllying cf-psb with stuff for testing.", 1, log,
                    new MicroServiceInitializationHelper()
                            //API
                            .withRestResource(DependenciesResource.class, "Configuration API"));
        }
    }

    @Path("/")
    public static class DependenciesResource {
        static final String configurationURI = "http://" + CFServices.getInstance().getAppUris().iterator().next()
                + "/" + ConfigurationMicroservice.SERVICE_ID + "-api";
        private WebAPIResolver apiResolver;

        @javax.ws.rs.core.Context
        public void setApplication(Application app) {
            apiResolver = ((MicroServiceApplication) app).getMicroServiceContext().getWebAPIResolver();
        }

        private void dropCreateSchema(String schema, boolean create) {
            try {
                RemoteConfigurationClient configurationClient = new RemoteConfigurationClient(
                        new RemoteConfigurationClient.RestClientResolver() {
                            @Override
                            public <T> T resolve(Class<T> webInterface, URI remoteService, boolean verifySSL) {
                                return apiResolver.getWebAPI(remoteService.toString(), webInterface);
                            }
                        }, new URI(configurationURI), false);
                StandalonePostgresBlobStoreConfiguration conf = new ServiceRegistryImpl(configurationClient)
                        .getBlobStoreConfiguration(StandalonePostgresBlobStoreConfiguration.class, "hack-images-bank");

                DataSource ds = PostgresUtil.getDataSource(conf.getDatabaseName(), conf.getServer(), conf.getPort(),
                        conf.getDbUser(), conf.getDbPassword(), conf.getMaxConnections(), conf.getDatabaseSchema());

                SchemaBootstrapRunner.dropSchemaIfExist(ds, schema);
                if (create) {
                    SchemaBootstrapRunner.runBootstrap(ds, new PGBlobstoreSchemaBootstrap(schema), schema, null);
                }
            } catch (IOException | SQLException e) {
                log.error("failure in creating schema in DB.", e);
                throw new IllegalStateException("failed operation");
            } catch (URISyntaxException e) {
                log.error("ridiculous error.", e);
                throw new IllegalStateException("failed operation");
            }
        }

        @POST
        @Path("/blobstore-schema/{schema}")
        public void createBlobstoreSchema(@PathParam("schema") String schema) {
            dropCreateSchema(schema, true);
        }

        @DELETE
        @Path("/blobstore-schema/{schema}")
        public void removeBlobstoreSchema(@PathParam("schema") String schema) {
            dropCreateSchema(schema, false);
        }
    }
}
