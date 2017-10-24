// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.demo.cftriple;

import com.emc.dpa.dev.messaging.DevMessagingServer;
import com.emc.dpa.dev.registry.DevModeConfigurationImpl;
import com.emc.microservice.ServiceConfig;
import com.emc.microservice.blobstore.PGBlobstoreSchemaBootstrap;
import com.emc.microservice.blobstore.StandalonePostgresBlobStoreConfiguration;
import com.emc.microservice.config.ConfigurationAPI;
import com.emc.microservice.configuration.ConfigurationMicroservice;
import com.emc.microservice.configuration.bootstrap.ConfigurationSchemaBootstrap;
import com.emc.microservice.configuration.client.RemoteConfigurationClient;
import com.emc.microservice.messaging.MessagingProviderConfiguration;
import com.emc.microservice.messaging.QueueConfiguration;
import com.emc.microservice.postgres.StandalonePostgresDatasourceConfiguration;
import com.emc.microservice.registry.RegistryClientConfiguration;
import com.emc.microservice.registry.RegistryClientDescriptor;
import com.emc.microservice.registry.ServiceRegistryImpl;
import com.emc.microservice.resource.DefaultWebApiResolver;
import com.emc.microservice.resource.ResourceProvider;
import com.emc.microservice.standalone.web.UndertowWebServerConfiguration;
import com.emc.microservice.webclient.WebAPIResolver;
import com.emc.ocopea.demo.model.Service;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.cfmanager.CloudFoundryClientResourceConfiguration;
import com.emc.ocopea.hub.application.HubWebApi;
import com.emc.ocopea.hub.application.StopAppCommandArgs;
import com.emc.ocopea.hub.repository.HubRepositorySchema;
import com.emc.ocopea.hub.webapp.UICommandCreateAppTemplate;
import com.emc.ocopea.hub.webapp.UICreateAppServiceExternalDependency;
import com.emc.ocopea.hub.webapp.UICreateAppServiceExternalDependencyProtocols;
import com.emc.ocopea.hub.webapp.UICreateApplicationServiceTemplate;
import com.emc.ocopea.messaging.PersistentMessagingConfiguration;
import com.emc.ocopea.messaging.PersistentQueueConfiguration;
import com.emc.ocopea.protection.ProtectionRepositorySchema;
import com.emc.ocopea.scheduler.PersistentSchedulerConfiguration;
import com.emc.ocopea.site.repository.SiteRepositorySchema;
import com.emc.ocopea.util.MapBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OcopeaClient {
    private static final String CF_API_HOST_ENV = "CF_API_HOST";
    private static final String SCHEMA_PREFIX_ENV = "SCHEMA_PREFIX";

    private static final String CENTRAL_BLOBSTORE_SCHEMA = "central_blobstore";
    private static final int DB_MAX_CONNECTIONS_PER_TYPE = 1;

    private static final String CONFIGURATOR_URN = "configurator";

    @NoJavadoc
    // TODO add javadoc
    public static void main(String[] args) {

        if (args.length < 1) {
            throw new IllegalArgumentException("action required");
        }
        switch (args[0]) {
            case "preconf":
                if (args.length != 2) {
                    throw new IllegalArgumentException("exactly one argument required - configurator dns. " +
                            "found " + (args.length - 1));
                }
                preConfigureOcopea(args[1]);
                break;
            case "conf":
                if (args.length != 2) {
                    throw new IllegalArgumentException("exactly one argument required - configurator dns. " +
                            "found " + (args.length - 1));
                }
                configureOcopea(args[1]);
                break;
            case "bind":
                if (args.length != 2) {
                    throw new IllegalArgumentException("exactly one argument required - configuration dns. " +
                            "found " + (args.length - 1));
                }
                bindOcopea(args[1]);
                break;
            case "delete":
                if (args.length != 2) {
                    throw new IllegalArgumentException("exactly one argument required - configuration dns. " +
                            "found " + (args.length - 1));
                }
                clearOcopea(args[1]);
                break;
            default:
                throw new IllegalArgumentException("unknown action " + args[0]);
        }
    }

    private static void preConfigureOcopea(String configuratorDns) {
        ApiInvoker api = configuratorInvoker(configuratorDns);

        // Create Postgres DataSource and BlobStore configurations
        final StandalonePostgresDatasourceConfiguration pgDataSourceConf = buildPGDataSourceConf(api);

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureDatasource(
                pgDataSourceConf,
                ConfigurationMicroservice.CONFIG_DB
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureSchema(
                ConfigurationSchemaBootstrap.class.getName(),
                ConfigurationMicroservice.CONFIG_DB,
                getSchemaPrefix() + "configuration"
        );

        // Central blobstore is in the same DB as configuration, but possibly need a different method for
        // creating schemas in DB that is not registered as a datasource.
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureSchema(
                PGBlobstoreSchemaBootstrap.class.getName(),
                ConfigurationMicroservice.CONFIG_DB,
                getSchemaPrefix() + CENTRAL_BLOBSTORE_SCHEMA
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureWebServer(
                new UndertowWebServerConfiguration(0),
                "default"
        );
    }

    /**
     * Changes RestEasy client, doesn't authenticate but adds type jackson information
     * (for configuration serialization)
     */
    private static ApiInvoker configuratorInvoker(final String configuratorDns) {
        return new ApiInvoker(getConfiguratorMocResourceProvider(configuratorDns)) {
            @Override
            protected DefaultWebApiResolver getResolver() {
                return new DefaultWebApiResolver() {
                    @Override
                    protected ResteasyWebTarget getResteasyWebTarget(String url) {
                        ResteasyJackson2Provider jacksonProvider = new ResteasyJackson2Provider();
                        jacksonProvider.setMapper(ConfiguratorSpecificUndertowProvider.getMapper());
                        return super.getResteasyWebTarget(url).register(jacksonProvider);
                    }
                };
            }
        };
    }

    private static void configureOcopea(String configuratorDns) {
        String domain = configuratorDns.substring(configuratorDns.indexOf('.'));
        ApiInvoker api = configuratorInvoker(configuratorDns);

        // Create Postgres DataSource and BlobStore configurations
        final StandalonePostgresDatasourceConfiguration pgDataSourceConf = buildPGDataSourceConf(api);
        final StandalonePostgresBlobStoreConfiguration pgBlobStoreConf = buildPGBlobStoreConf(pgDataSourceConf);

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureWebServer(
                new UndertowWebServerConfiguration(0),
                "ocopea"
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureWebServer(
                new UndertowWebServerConfiguration(0),
                "cf-paas"
        );

        registerShapnServices(api, domain);
        registerHubServiceDependencies(api, pgDataSourceConf, pgBlobStoreConf, domain);
        registerSiteServiceDependencies(api, pgDataSourceConf, pgBlobStoreConf, domain);
        registerCFPaaSServiceDependencies(api, pgBlobStoreConf, domain);

    }

    private static void registerShapnServices(ApiInvoker api, String domain) {

        String shpanCopyStoreUrl = "http://" + getHostPrefix() + "configuration" + domain + "/shpan-copy-store-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "shpan-copy-store",
                        "default",
                        shpanCopyStoreUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                //.with("print-all-json-requests", Boolean.toString(true))
                                .with("publicURL", shpanCopyStoreUrl)
                                .build(),
                        Collections.emptyMap()
                ));
    }

    private static String getSchemaPrefix() {
        String schemaPrefixEnv = System.getenv(SCHEMA_PREFIX_ENV);
        if (schemaPrefixEnv != null) {
            return schemaPrefixEnv + "_";
        } else {
            return "";
        }
    }

    private static String getHostPrefix() {
        String schemaPrefixEnv = System.getenv(SCHEMA_PREFIX_ENV);
        if (schemaPrefixEnv != null) {
            return schemaPrefixEnv + "-";
        } else {
            return "";
        }
    }

    private static StandalonePostgresDatasourceConfiguration buildPGDataSourceConf(ApiInvoker api) {
        List<Service> postgresServices =
                api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class)
                        .getBoundServices("user-provided")
                        .stream()
                        .filter(service -> service.getName().equals("postgres"))
                        .collect(Collectors.toList());
        if (postgresServices.size() != 1) {
            throw new IllegalStateException("expected single user-provided service named postgres, check " +
                    "VCAP_SERVICES on configurator");
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
                getSchemaPrefix() + "configuration",
                DB_MAX_CONNECTIONS_PER_TYPE,
                credentials.get("username"),
                credentials.get("password")
        );
    }

    private static StandalonePostgresBlobStoreConfiguration buildPGBlobStoreConf(
            StandalonePostgresDatasourceConfiguration pgDataSourceConf) {

        return new StandalonePostgresBlobStoreConfiguration(
                pgDataSourceConf.getServer(),
                pgDataSourceConf.getPort(),
                pgDataSourceConf.getDatabaseName(),
                getSchemaPrefix() + CENTRAL_BLOBSTORE_SCHEMA,
                pgDataSourceConf.getMaxConnections(),
                pgDataSourceConf.getDbUser(),
                pgDataSourceConf.getDbPassword()
        );
    }

    private static void registerHubServiceDependencies(
            ApiInvoker api,
            StandalonePostgresDatasourceConfiguration pgDataSourceConf,
            StandalonePostgresBlobStoreConfiguration pgBlobStoreConf,
            String domain) {

        String hubDbName = "hub-db";
        String hubSchemaName = getSchemaPrefix() + "hub_db";
        String hubUrl = "http://" + getHostPrefix() + "orcs" + domain + "/hub-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "hub",
                        "ocopea",
                        hubUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, ServiceConfig.DataSourceConfig>newHashMap()
                                .with(
                                        hubDbName,
                                        new ServiceConfig.DataSourceConfig(
                                                DB_MAX_CONNECTIONS_PER_TYPE,
                                                Collections.emptyMap()))
                                .build(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                .with("publicURL", hubUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        String hubWebUrl = "http://" + getHostPrefix() + "orcs" + domain + "/hub-web-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "hub-web",
                        "ocopea",
                        hubWebUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                .with("publicURL", hubUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        final StandalonePostgresDatasourceConfiguration hubDBConf = new StandalonePostgresDatasourceConfiguration(
                pgDataSourceConf.getServer(),
                pgDataSourceConf.getPort(),
                pgDataSourceConf.getDatabaseName(),
                hubSchemaName,
                DB_MAX_CONNECTIONS_PER_TYPE,
                pgDataSourceConf.getDbUser(),
                pgDataSourceConf.getDbPassword()
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureDatasource(
                hubDBConf,
                hubDbName
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureSchema(
                HubRepositorySchema.class.getName(),
                hubDbName,
                hubSchemaName
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureBlobstore(
                pgBlobStoreConf,
                "image-store"
        );
    }

    private static void registerSiteServiceDependencies(
            ApiInvoker api,
            StandalonePostgresDatasourceConfiguration pgDataSourceConf,
            StandalonePostgresBlobStoreConfiguration pgBlobStoreConf,
            String domain) {

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureExternal(
                new RegistryClientConfiguration().getPropertyValues(),
                RegistryClientDescriptor.REGISTRY_CLIENT_RESOURCE_NAME
        );

        String siteDbName = "site-db";
        String siteScehmaName = getSchemaPrefix() + "site_db";
        String siteUrl = "http://" + getHostPrefix() + "orcs" + domain + "/site-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "site",
                        "ocopea",
                        siteUrl,
                        null,
                        null,
                        MapBuilder.<String, ServiceConfig.InputQueueConfig>newHashMap()
                                .with(
                                        "deployed-application-events",
                                        new ServiceConfig.InputQueueConfig(5, true, Collections.emptyList()))
                                .with(
                                        "pending-deployed-application-events",
                                        new ServiceConfig.InputQueueConfig(1, true, Collections.emptyList()))
                                .build(),
                        MapBuilder.<String, ServiceConfig.DestinationQueueConfig>newHashMap()
                                .with(
                                        "deployed-application-events",
                                        new ServiceConfig.DestinationQueueConfig(null, null, true))
                                .with(
                                        "pending-deployed-application-events",
                                        new ServiceConfig.DestinationQueueConfig(null, null, true))
                                .build(),
                        MapBuilder.<String, ServiceConfig.DataSourceConfig>newHashMap()
                                .with(
                                        siteDbName,
                                        new ServiceConfig.DataSourceConfig(
                                                DB_MAX_CONNECTIONS_PER_TYPE,
                                                Collections.emptyMap())).build(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                .with("site-name", "EMC IT")
                                .with("location", "{" +
                                        "\"latitude\":32.1792126," +
                                        "\"longitude\":34.9005128," +
                                        "\"name\":\"Israel\"," +
                                        "\"properties\":{}}")
                                .with("publicURL", siteUrl)
                                .build(),
                        Collections.emptyMap()
                ));
        String protectionDbName = "protection-db";
        String protectionSchemaName = getSchemaPrefix() + "protection_db";
        String protectionUrl = "http://" + getHostPrefix() + "orcs" + domain + "/protection-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "protection",
                        "ocopea",
                        protectionUrl,
                        null,
                        null,
                        MapBuilder.<String, ServiceConfig.InputQueueConfig>newHashMap()
                                .with(
                                        "application-copy-events",
                                        new ServiceConfig.InputQueueConfig(5, true, Collections.emptyList()))
                                .with(
                                        "pending-application-copy-events",
                                        new ServiceConfig.InputQueueConfig(1, true, Collections.emptyList()))

                                .build(),
                        MapBuilder.<String, ServiceConfig.DestinationQueueConfig>newHashMap()
                                .with(
                                        "application-copy-events",
                                        new ServiceConfig.DestinationQueueConfig(null, null, true))
                                .with(
                                        "pending-application-copy-events",
                                        new ServiceConfig.DestinationQueueConfig(null, null, true))
                                .build(),
                        MapBuilder.<String, ServiceConfig.DataSourceConfig>newHashMap()
                                .with(
                                        protectionDbName,
                                        new ServiceConfig.DataSourceConfig(
                                                DB_MAX_CONNECTIONS_PER_TYPE,
                                                Collections.emptyMap())).build(),
                        MapBuilder.<String, ServiceConfig.DataSourceConfig>newHashMap()
                                .with(
                                        "copy-store",
                                        new ServiceConfig.DataSourceConfig(
                                                DB_MAX_CONNECTIONS_PER_TYPE,
                                                Collections.emptyMap()))
                                .build(),

                        MapBuilder.<String, String>newHashMap()
                                .with("publicURL", protectionUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        String mysqlDsbUrl = "http://" + getHostPrefix() + "mysql-dsb" + domain + "/mysql-dsb-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "mysql-dsb",
                        "ocopea",
                        mysqlDsbUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                .with("print-all-json-requests", Boolean.toString(true))
                                .with("mysql-service-name", "p-mysql")
                                .with("mysql-default-plan", "pre-existing-plan")
                                .with("publicURL", mysqlDsbUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        String crbFsGoUrl = "http://" + getHostPrefix() + "crb-fs-go" + domain + "/crb";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "crb-fs-go",
                        "ocopea",
                        crbFsGoUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                .with("publicURL", crbFsGoUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        String vmwareDsbUrl = "http://" + getHostPrefix() + "docker-dsb" + domain + "/vmware-dsb-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "vmware-dsb",
                        "ocopea",
                        vmwareDsbUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, String>newHashMap()
                                .with("print-all-json-requests", Boolean.toString(true))
                                .with("publicURL", vmwareDsbUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        // Registering site datasource
        final StandalonePostgresDatasourceConfiguration siteDBConf = new StandalonePostgresDatasourceConfiguration(
                pgDataSourceConf.getServer(),
                pgDataSourceConf.getPort(),
                pgDataSourceConf.getDatabaseName(),
                siteScehmaName,
                DB_MAX_CONNECTIONS_PER_TYPE,
                pgDataSourceConf.getDbUser(),
                pgDataSourceConf.getDbPassword()
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureDatasource(
                siteDBConf,
                siteDbName
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureSchema(
                SiteRepositorySchema.class.getName(),
                siteDbName,
                siteScehmaName
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureBlobstore(
                pgBlobStoreConf,
                "staging-area"
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureBlobstore(
                pgBlobStoreConf,
                "copy-store"
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureBlobstore(
                pgBlobStoreConf,
                DevMessagingServer.DEV_MESSAGING_BLOBSTORE_NAME
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureMessaging(
                new PersistentMessagingConfiguration("site-db", true),
                MessagingProviderConfiguration.DEFAULT_MESSAGING_SYSTEM_NAME
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureScheduler(
                new PersistentSchedulerConfiguration("site-db", true),
                "default"
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureQueue(
                new PersistentQueueConfiguration(
                        QueueConfiguration.MessageDestinationType.QUEUE,
                        "deployed-application-events",
                        1000,
                        2,
                        2),
                "deployed-application-events"
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureQueue(
                new PersistentQueueConfiguration(
                        QueueConfiguration.MessageDestinationType.QUEUE,
                        "pending-deployed-application-events",
                        1000,
                        2,
                        2),
                "pending-deployed-application-events"
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureQueue(
                new PersistentQueueConfiguration(
                        QueueConfiguration.MessageDestinationType.QUEUE,
                        "application-copy-events",
                        1000,
                        2,
                        2),

                "application-copy-events"
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureQueue(
                new PersistentQueueConfiguration(
                        QueueConfiguration.MessageDestinationType.QUEUE,
                        "pending-application-copy-events",
                        1000,
                        2,
                        2),

                "pending-application-copy-events"
        );

        // Registering protection datasource
        final StandalonePostgresDatasourceConfiguration protectionDBConf =
                new StandalonePostgresDatasourceConfiguration(
                        pgDataSourceConf.getServer(),
                        pgDataSourceConf.getPort(),
                        pgDataSourceConf.getDatabaseName(),
                        protectionSchemaName,
                        DB_MAX_CONNECTIONS_PER_TYPE,
                        pgDataSourceConf.getDbUser(),
                        pgDataSourceConf.getDbPassword());

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureDatasource(
                protectionDBConf,
                protectionDbName
        );
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureSchema(
                ProtectionRepositorySchema.class.getName(),
                protectionDbName,
                protectionSchemaName
        );
    }

    private static void registerCFPaaSServiceDependencies(
            ApiInvoker api,
            StandalonePostgresBlobStoreConfiguration pgsqlBlobStoreConf,
            String domain) {

        String cfPsbUrl = "http://" + getHostPrefix() + "paas" + domain + "/cf-psb-api";
        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configuresService(
                ServiceConfig.generateServiceConfig(
                        "cf-psb",
                        "cf-paas",
                        cfPsbUrl,
                        null,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        MapBuilder.<String, ServiceConfig.DataSourceConfig>newHashMap()
                                .with(
                                        "hack-images-bank",
                                        new ServiceConfig.DataSourceConfig(
                                                DB_MAX_CONNECTIONS_PER_TYPE,
                                                Collections.emptyMap())).build(),
                        MapBuilder.<String, String>newHashMap()
                                .with("create-sg", Boolean.FALSE.toString())
                                .with("publicURL", cfPsbUrl)
                                .build(),
                        Collections.emptyMap()
                ));

        String cfApiNode = System.getenv(CF_API_HOST_ENV);
        if (cfApiNode == null) {
            throw new IllegalArgumentException("Missing mandatory environment variable " + CF_API_HOST_ENV);
        }

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureExternal(
                new CloudFoundryClientResourceConfiguration(
                        cfApiNode,
                        "CNDPDev-OrgMgr",
                        "CNDPD3vOrgMgr",
                        true,
                        5).getPropertyValues(),
                "cf"
        );

        api.proxy(CONFIGURATOR_URN, ConfiguratorApi.class).configureBlobstore(
                pgsqlBlobStoreConf,
                "hack-images-bank"
        );
    }

    private static void bindOcopea(String configurationDns) {
        final String siteUrl = "http://" + configurationDns.replaceFirst("configuration", "orcs") + "/site-api";
        final String cfPsbUrl = "http://" + configurationDns.replaceFirst("configuration", "paas") + "/cf-psb-api";
        final String mysqlDsbUrl =
                "http://" + configurationDns.replaceFirst("configuration", "mysql-dsb") + "/mysql-dsb-api";
        final String crbGoUrl = "http://" + configurationDns.replaceFirst("configuration","crb-fs-go") + "/crb";
        final String shpanCopyStoreUrl = "http://" + configurationDns + "/shpan-copy-store-api";


        ResourceProvider resourceProvider = getConfigurationResourceProvider(configurationDns);
        ApiInvoker api = new ApiInvoker(resourceProvider);

        api.addSiteToHub("site", siteUrl);
        api.addPsb(siteUrl, "cf-psb", cfPsbUrl);
        api.addDsb(siteUrl, "mysql-dsb", mysqlDsbUrl);
        api.addCrb(siteUrl, "shpan-copy-store", shpanCopyStoreUrl);
        //api.addCrb(siteUrl, "crb-fs-go", crbGoUrl);

        api.addMavenArtifactRegistry(
                siteUrl,
                "shpanRegistry", // TODO: rename after ui bug fixed
                "https://dl.bintray.com/ocopea/central");

        /*
        api.addJiraIntegration(
                "https://jira.cec.lab.emc.com:8443",
                "10305",
                "10004"
        );
        */

        // Creating demo apps that won't work yet but nice for demos
        //createDemoApps(api);

        api.createAppTemplate(
                new UICommandCreateAppTemplate(
                        "spring-music",
                        "1.0",
                        "Spring Music",
                        "spring-music",
                        Collections.singletonList(
                                new UICreateApplicationServiceTemplate(
                                        "spring-music",
                                        "cf",
                                        "org.springframework.samples:spring-music:jar",
                                        "java",
                                        "2.2",
                                        MapBuilder.<String, String>newHashMap()
                                                .with("memory", "1024M")
                                                .build(),
                                        Collections.emptyMap(),
                                        Collections.singletonList(
                                                new UICreateAppServiceExternalDependency(
                                                        UICreateAppServiceExternalDependency.TypeEnum.DATABASE,
                                                        "music-db",
                                                        "Music App persistent store",
                                                        Collections.singletonList(
                                                                new UICreateAppServiceExternalDependencyProtocols(
                                                                        "mysql",
                                                                        null,
                                                                        null,
                                                                        null))
                                                )),
                                        Collections.singletonList(8080),
                                        8080,
                                        "/"))),
                OcopeaClient.class.getResourceAsStream("/spring-music.png"));

        String psbRoute = resourceProvider.getServiceRegistryApi().getServiceConfig("cf-psb").getRoute();


    }

    private static void clearOcopea(String configurationDns) {
        ResourceProvider resourceProvider = getConfigurationResourceProvider(configurationDns);
        ApiInvoker api = new ApiInvoker(resourceProvider);

        HubWebApi hub = api.proxy("hub", HubWebApi.class);
        hub.listAppInstances().forEach(appInstanceDTO ->
                hub.stopApp(new StopAppCommandArgs(appInstanceDTO.getId(), appInstanceDTO.getSiteId())));
    }

    private static ResourceProvider getConfigurationResourceProvider(String configurationDns) {
        String configurationServiceAddr = "http://" + configurationDns + "/configuration-api";
        System.out.println("configuration url: " + configurationServiceAddr);
        ConfigurationAPI configurationApi =
                new RemoteConfigurationClient(new RemoteConfigurationClient.RestClientResolver() {
                    WebAPIResolver webApiResolver = new DefaultWebApiResolver();

                    @Override
                    public <T> T resolve(Class<T> webInterface, URI remoteService, boolean verifySSL) {
                        return webApiResolver.getWebAPI(remoteService.toString(), webInterface);
                    }
                }, URI.create(configurationServiceAddr), false);

        return new ResourceProvider(configurationApi);
    }

    private static ResourceProvider getConfiguratorMocResourceProvider(String configuratorDns) {
        ConfigurationAPI configurationApi = new DevModeConfigurationImpl();
        new ServiceRegistryImpl(configurationApi).registerServiceConfig(
                CONFIGURATOR_URN,
                ServiceConfig.generateServiceConfig(
                        CONFIGURATOR_URN,
                        null,
                        "http://" + configuratorDns + "/configurator-api",
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        Collections.emptyMap()
                ));

        return new ResourceProvider(configurationApi);
    }
}
