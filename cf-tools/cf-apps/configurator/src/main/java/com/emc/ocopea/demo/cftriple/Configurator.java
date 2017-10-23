// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.demo.cftriple;

import com.emc.dpa.dev.registry.DevModeConfigurationImpl;
import com.emc.microservice.MicroService;
import com.emc.microservice.MicroServiceInitializationHelper;
import com.emc.microservice.ServiceConfig;
import com.emc.microservice.blobstore.StandalonePostgresBlobStoreConfiguration;
import com.emc.microservice.bootstrap.AbstractSchemaBootstrap;
import com.emc.microservice.bootstrap.SchemaBootstrapRunner;
import com.emc.microservice.config.ConfigurationAPI;
import com.emc.microservice.datasource.DatasourceConfiguration;
import com.emc.microservice.datasource.MicroServiceDataSource;
import com.emc.microservice.postgres.StandalonePostgresDatasourceConfiguration;
import com.emc.microservice.registry.ServiceRegistryApi;
import com.emc.microservice.registry.ServiceRegistryImpl;
import com.emc.microservice.resource.ResourceProvider;
import com.emc.microservice.resource.ResourceProviderManager;
import com.emc.microservice.standalone.web.UndertowWebServerConfiguration;
import com.emc.ocopea.demo.CFResourceProviderFactory;
import com.emc.ocopea.demo.CFServices;
import com.emc.ocopea.demo.model.Service;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.messaging.PersistentMessagingConfiguration;
import com.emc.ocopea.messaging.PersistentQueueConfiguration;
import com.emc.ocopea.scheduler.PersistentSchedulerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.PathParam;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configurator {
    private static final Logger log = LoggerFactory.getLogger(Configurator.class);

    @NoJavadoc
    // TODO add javadoc
    public static void main(String[] args) throws Exception {
        ConfigurationAPI configAPI = new DualConfigurationAPISilent(
                new DevModeConfigurationImpl(),
                CFResourceProviderFactory.getConfigurationAPI());

        // register default web server so we can start the configurator
        new ServiceRegistryImpl(configAPI).registerWebServer("default", new UndertowWebServerConfiguration(0));
        new ServiceRegistryImpl(configAPI).registerServiceConfig(
                "configurator",
                ServiceConfig.generateServiceConfig(
                        "configurator",
                        null,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap()));

        // Getting dev resource provider and overriding the config api with local cute one that access the db
        CFResourceProviderFactory.runServices(configAPI, null, new ConfiguratorMicroservice());
    }

    private static class DualConfigurationAPISilent implements ConfigurationAPI {
        private final ConfigurationAPI primary;
        private final ConfigurationAPI secondary;

        private DualConfigurationAPISilent(ConfigurationAPI primary, ConfigurationAPI secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        public Collection<String> list(String path) {
            Collection<String> primaryList = Collections.emptyList();
            try {
                primaryList = primary.list(path);
            } catch (Exception e) {
                log.info("failure on list, primary - swallowing. message: " + e.getMessage());
            }
            Collection<String> secondaryList = Collections.emptyList();
            try {
                secondaryList = secondary.list(path);
            } catch (Exception e) {
                log.info("failure on list, secondary - swallowing. message: " + e.getMessage());
            }
            Set<String> retVal = new HashSet<>(primaryList);
            retVal.addAll(secondaryList);
            return retVal;
        }

        @Override
        public String readData(String path) {
            String retVal = null;
            try {
                retVal = primary.readData(path);
            } catch (Exception e) {
                log.info("failure on readData, primary - swallowing. message: " + e.getMessage());
            }
            if (retVal != null) {
                try {
                    retVal = secondary.readData(path);
                } catch (Exception e) {
                    log.info("failure on readData, secondary - swallowing. message: " + e.getMessage());
                }
            }
            return retVal;
        }

        @Override
        public boolean isDirectory(String path) {
            Boolean retVal = null;
            try {
                retVal = primary.isDirectory(path);
            } catch (Exception e) {
                log.info("failure on isDirectory, primary - swallowing. message: " + e.getMessage());
            }
            if (retVal == null) {
                try {
                    retVal = secondary.isDirectory(path);
                } catch (Exception e) {
                    log.info("failure on isDirectory, secondary - swallowing. message: " + e.getMessage());
                }
            }
            return retVal != null && retVal;
        }

        @Override
        public boolean exists(String path) {
            Boolean retVal = null;
            try {
                retVal = primary.exists(path);
            } catch (Exception e) {
                log.info("failure on exists, primary - swallowing. message: " + e.getMessage());
            }
            if (retVal == null || !retVal) {
                try {
                    retVal = secondary.exists(path);
                } catch (Exception e) {
                    log.info("failure on exists, secondary - swallowing. message: " + e.getMessage());
                }
            }
            return retVal != null && retVal;
        }

        @Override
        public void writeData(String path, String data) {
            try {
                primary.writeData(path, data);
            } catch (Exception e) {
                log.info("faliure on writeData, primary - swallowing. message: " + e.getMessage());
            }
            try {
                secondary.writeData(path, data);
            } catch (Exception e) {
                log.info("faliure on writeData, secondary - swallowing. message: " + e.getMessage());
            }
        }

        @Override
        public void mkdir(String path) {
            try {
                primary.mkdir(path);
            } catch (Exception e) {
                log.info("faliure on mkdir, primary - swallowing. message: " + e.getMessage());
            }
            try {
                secondary.mkdir(path);
            } catch (Exception e) {
                log.info("faliure on mkdir, secondary - swallowing. message: " + e.getMessage());
            }
        }
    }

    private static class ConfiguratorMicroservice extends MicroService {

        private ConfiguratorMicroservice() {
            super(
                    "Configurator Service",
                    "configurator",
                    "Service setting initial configuration.",
                    1,
                    log,
                    new MicroServiceInitializationHelper()
                            //API
                            .withRestResource(ConfiguratorResource.class, "Configuration API")
            );
        }
    }

    public static final class ConfiguratorResource implements ConfiguratorApi {

        @Override
        public void configuresService(ServiceConfig configuration) {
            getRegistry().registerServiceConfig(configuration.getServiceURI(), configuration);
        }

        @Override
        public void configureBlobstore(
                StandalonePostgresBlobStoreConfiguration configuration,
                @PathParam("blobstoreName") String name) {
            getRegistry().registerBlobStore(name, configuration);
        }

        @Override
        public void configureDatasource(
                StandalonePostgresDatasourceConfiguration configuration,
                @PathParam("dsName") String name) {
            getRegistry().registerDataSource(name, configuration);
        }

        @Override
        public void configureSchema(
                String schemaClassName,
                @PathParam("dbName") String dbName,
                @PathParam("schemaName") String schemaName) {
            ResourceProvider resourceProvider = ResourceProviderManager.getResourceProvider();
            DatasourceConfiguration configuration = getRegistry().getDataSourceConfiguration(
                    resourceProvider.getDatasourceConfigurationClass(),
                    dbName
            );
            MicroServiceDataSource ds = resourceProvider.getDataSource(configuration);
            try {
                SchemaBootstrapRunner.dropSchemaIfExist(ds, schemaName);
                SchemaBootstrapRunner.runBootstrap(
                        ds,
                        createSchemaObject(schemaClassName, schemaName),
                        schemaName,
                        null);
            } catch (ReflectiveOperationException e) {
                log.error("schema instantiation failed using both empty constructor and String constructor. " +
                        "class: {}", schemaClassName, e);
                throw new IllegalStateException("failed operation");
            } catch (IOException | SQLException e) {
                log.error("failure in creating schema in DB.", e);
                throw new IllegalStateException("failed operation");
            }
        }

        @Override
        public void configureWebServer(
                UndertowWebServerConfiguration configuration,
                @PathParam("webServerName") String name) {
            getRegistry().registerWebServer(name, configuration);
        }

        @Override
        public void configureQueue(
                PersistentQueueConfiguration configuration, @PathParam("queueName") String name) {
            getRegistry().registerQueue(name, configuration);
        }

        @Override
        public void configureMessaging(
                PersistentMessagingConfiguration configuration, @PathParam("messagingName") String name) {
            getRegistry().registerMessaging(name, configuration);
        }

        @Override
        public void configureScheduler(
                PersistentSchedulerConfiguration configuration,
                @PathParam("schedulerName") String name) {
            getRegistry().registerScheduler(name, configuration);
        }

        @Override
        public void configureExternal(
                Map<String, String> configuration,
                @PathParam("externalServiceName") String name) {
            getRegistry().registerExternalResource(name, configuration);
        }

        @Override
        public List<Service> getBoundServices(@PathParam("type") String type) {
            return CFServices.getInstance().getServices(type);
        }

        private AbstractSchemaBootstrap createSchemaObject(String schemaClassName, String schemaName)
                throws ReflectiveOperationException {
            @SuppressWarnings("unchecked")
            Class<AbstractSchemaBootstrap> schemaClass =
                    (Class<AbstractSchemaBootstrap>) Class.forName(schemaClassName);
            try {
                return schemaClass.getConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                // ignore, try a constructor accepting string
            }
            return schemaClass.getConstructor(String.class).newInstance(schemaName);
        }

        private ServiceRegistryApi getRegistry() {
            return ResourceProviderManager.getResourceProvider().getServiceRegistryApi();
        }
    }
}
