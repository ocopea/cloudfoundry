// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.demo.cftriple;

import com.emc.microservice.ServiceConfig;
import com.emc.microservice.config.ConfigurationAPI;
import com.emc.microservice.configuration.ConfigServiceLocalConfigurationImpl;
import com.emc.microservice.configuration.ConfigurationMicroservice;
import com.emc.microservice.datasource.DatasourceProvider;
import com.emc.microservice.datasource.MicroServiceDataSource;
import com.emc.microservice.postgres.StandalonePostgresDatasourceConfiguration;
import com.emc.microservice.registry.ServiceRegistryImpl;
import com.emc.microservice.standalone.web.UndertowWebServerConfiguration;
import com.emc.ocopea.demo.CFResourceProviderFactory;
import com.emc.ocopea.demo.CFServices;
import com.emc.ocopea.demo.model.Service;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.hub.copy.ShpanCopyRepositoryMicroService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigurationRunner {
    private static final String SCHEMA_PREFIX_ENV = "SCHEMA_PREFIX";
    private static final int DB_MAX_CONNECTIONS_PER_TYPE = 1;
    private static String schemaPrefix = "";

    @NoJavadoc
    // TODO add javadoc
    public static void main(String[] args) throws Exception {
        String schemaPrefixEnv = System.getenv(SCHEMA_PREFIX_ENV);
        if (schemaPrefixEnv != null) {
            schemaPrefix = schemaPrefixEnv + "_";
        }

        StandalonePostgresDatasourceConfiguration dsConf = buildPGDataSourceConf();
        ConfigurationAPI configAPI = new ConfigServiceLocalConfigurationImpl(getDataSource(dsConf));

        // register basic configurations so we can start the configuration service...
        ServiceRegistryImpl serviceRegistry = new ServiceRegistryImpl(configAPI);
        serviceRegistry.registerWebServer("default", new UndertowWebServerConfiguration(0));
        serviceRegistry.registerDataSource(ConfigurationMicroservice.CONFIG_DB, dsConf);
        new ServiceRegistryImpl(configAPI).registerServiceConfig(
                ConfigurationMicroservice.SERVICE_ID,
                ServiceConfig.generateServiceConfig(
                        ConfigurationMicroservice.SERVICE_ID,
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
        CFResourceProviderFactory.runServices(configAPI,
                null,
                new ConfigurationMicroservice(),
                new ShpanCopyRepositoryMicroService());
    }

    private static StandalonePostgresDatasourceConfiguration buildPGDataSourceConf() {
        List<Service> postgresServices = CFServices
                .getInstance()
                .getServices("user-provided")
                .stream()
                .filter(service -> service.getName().equals("postgres"))
                .collect(Collectors.toList());
        if (postgresServices.size() != 1) {
            throw new IllegalStateException("expected one user-provided service named postgres, check VCAP_SERVICES");
        }
        Map<String, String> credentials = postgresServices.get(0).getCredentials();
        // url should be of the form "postgresql://<host>:<port>/<database>"
        Matcher pgUrl = Pattern.compile("postgresql://([^:]*):(\\d*)/(.*)").matcher(credentials.get("url"));
        if (!pgUrl.matches()) {
            throw new IllegalStateException("malformed url to postgres - " + credentials.get("url"));
        }
        return new StandalonePostgresDatasourceConfiguration(
                pgUrl.group(1),
                Integer.parseInt(pgUrl.group(2)),
                pgUrl.group(3),
                schemaPrefix + "configuration",
                DB_MAX_CONNECTIONS_PER_TYPE,
                credentials.get("username"),
                credentials.get("password")
        );
    }

    private static MicroServiceDataSource getDataSource(StandalonePostgresDatasourceConfiguration conf) {
        final ServiceLoader<DatasourceProvider> providers = ServiceLoader.load(DatasourceProvider.class);
        if (!providers.iterator().hasNext()) {
            throw new IllegalStateException(
                    "Could not find provider for " + DatasourceProvider.class.getCanonicalName());
        }
        @SuppressWarnings("unchecked") final DatasourceProvider<StandalonePostgresDatasourceConfiguration>
                datasourceProvider = providers.iterator().next();

        return datasourceProvider.getDatasource(conf);
    }
}
