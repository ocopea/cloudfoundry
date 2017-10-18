// Copyright (c) [2018 - 2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.demo;

import com.emc.microservice.MicroService;
import com.emc.microservice.MicroServiceController;
import com.emc.microservice.ServiceConfig;
import com.emc.microservice.config.ConfigurationAPI;
import com.emc.microservice.configuration.client.RemoteConfigurationClient;
import com.emc.microservice.messaging.MessagingProvider;
import com.emc.microservice.registry.ServiceRegistryApi;
import com.emc.microservice.registry.ServiceRegistryImpl;
import com.emc.microservice.resource.DefaultWebApiResolver;
import com.emc.microservice.resource.ResourceConfiguration;
import com.emc.microservice.resource.ResourceProvider;
import com.emc.microservice.restapi.WebServerConfiguration;
import com.emc.microservice.runner.MicroServiceRunner;
import com.emc.microservice.webclient.WebAPIResolver;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class CFResourceProviderFactory {
    private static final Logger log = LoggerFactory.getLogger(CFResourceProviderFactory.class);

    public static Map<String, MicroServiceController> runServices(MicroService... services) {
        return runServices(getConfigurationAPI(), null, services);
    }

    public static Map<String, MicroServiceController> runServices(Supplier<MessagingProvider> messagingProviderSupplier,
            MicroService... services) {

        return runServices(getConfigurationAPI(), messagingProviderSupplier, services);
    }

    @NoJavadoc
    // TODO add javadoc
    public static Map<String, MicroServiceController> runServices(ConfigurationAPI configurationAPI,
            Supplier<MessagingProvider> messagingProviderSupplier, MicroService... services) {

        final String publicRootURL = "http://" + CFServices.getInstance().getAppUris().iterator().next();

        ServiceRegistryApi serviceRegistryApi = new ServiceRegistryImpl(configurationAPI) {
            @Override
            public ServiceConfig getServiceConfig(String serviceURI) {
                ServiceConfig serviceConfig = super.getServiceConfig(serviceURI);
                // set this service's url if the configuration didn't know it
                if (serviceConfig != null && serviceConfig.getRoute() == null) {
                    Map<String, String> parameters = serviceConfig.getParameters();
                    if (parameters == null) {
                        parameters = new HashMap<>();
                    } else {
                        parameters = new HashMap<>(serviceConfig.getParameters());
                    }
                    // todo: This is code duplication, have this + "-api" in a single point in the code
                    String route = publicRootURL + "/" + serviceURI + "-api";
                    parameters.put("publicURL", route);

                    log.info("setting route of {} to {}", serviceURI, route);

                    serviceConfig = ServiceConfig.generateServiceConfig(serviceConfig.getServiceURI(), null, route,
                            serviceConfig.getGlobalLoggingConfig(), serviceConfig.getCorrelationLoggingConfig(),
                            serviceConfig.getInputQueueConfig(), serviceConfig.getDestinationQueueConfig(),
                            serviceConfig.getDataSourceConfig(), serviceConfig.getBlobstoreConfig(), parameters,
                            serviceConfig.getExternalResourceConfig());
                    registerServiceConfig(serviceURI, serviceConfig);
                }

                return serviceConfig;
            }

            @Override
            public <WebserverConfT extends WebServerConfiguration> WebserverConfT getWebServerConfiguration(
                    Class<WebserverConfT> webserverConfTClass, String webServerName) {
                WebserverConfT webServerConfiguration = super.getWebServerConfiguration(webserverConfTClass,
                        webServerName);
                Map<String, String> propertyValues = webServerConfiguration.getPropertyValues();
                if (propertyValues.get("port") == null || "".equals(propertyValues.get("port"))
                        || "0".equals(propertyValues.get("port"))) {
                    propertyValues.put("port", CFServices.getInstance().getPort());
                    WebserverConfT newWebServerConfiguration = ResourceConfiguration
                            .asSpecificConfiguration(webserverConfTClass, propertyValues);
                    super.registerWebServer(webServerName, newWebServerConfiguration);
                    return newWebServerConfiguration;
                } else {
                    return webServerConfiguration;
                }
            }
        };

        ResourceProvider resourceProvider = new ResourceProvider(configurationAPI, serviceRegistryApi) {
            @Override
            public WebAPIResolver getWebAPIResolver() {
                return new DefaultWebApiResolver(false);
            }

            @Override
            protected MessagingProvider createMessagingProvider() {
                return messagingProviderSupplier == null ? super.createMessagingProvider()
                        : messagingProviderSupplier.get();
            }
        };

        return new MicroServiceRunner().run(resourceProvider, services);
    }

    @NoJavadoc
    // TODO add javadoc
    public static ConfigurationAPI getConfigurationAPI() {
        String publicDNS = CFServices.getInstance().getAppUris().iterator().next();
        log.info("my DNS: " + publicDNS);
        String nazConfigURL = System.getProperty("OCOPEA_CONFIG_URL");
        if (nazConfigURL == null || nazConfigURL.isEmpty()) {
            nazConfigURL = System.getenv("OCOPEA_CONFIG_URL");
        }
        if (nazConfigURL == null || nazConfigURL.isEmpty()) {
            String nazConfigHostName = System.getProperty("OCOPEA_CONFIG_URL");
            if (nazConfigHostName == null || nazConfigHostName.isEmpty()) {
                nazConfigHostName = System.getenv("OCOPEA_CONFIG_HOST_NAME");
                if (nazConfigHostName == null || nazConfigHostName.isEmpty()) {
                    throw new IllegalStateException("OCOPEA_CONFIG_URL and OCOPEA_CONFIG_HOST_NAME env not set");
                }
            }
            nazConfigURL = nazConfigHostName + publicDNS.substring(publicDNS.indexOf("."));
        }
        final String configurationServiceAddr = "http://" + nazConfigURL + "/configuration-api";
        log.info("using configuration service at: " + configurationServiceAddr);
        return new RemoteConfigurationClient(new RemoteConfigurationClient.RestClientResolver() {
            WebAPIResolver webApiResolver = new DefaultWebApiResolver(false);

            @Override
            public <T> T resolve(Class<T> webInterface, URI remoteService, boolean verifySSL) {
                return webApiResolver.getWebAPI(remoteService.toString(), webInterface);
            }
        }, URI.create(configurationServiceAddr), false);
    }

}
