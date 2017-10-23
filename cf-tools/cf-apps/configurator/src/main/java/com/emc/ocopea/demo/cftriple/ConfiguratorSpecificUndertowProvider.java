// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.demo.cftriple;

import com.emc.microservice.Context;
import com.emc.microservice.resource.ResourceConfiguration;
import com.emc.microservice.restapi.MicroServiceWebServer;
import com.emc.microservice.restapi.WebServerProvider;
import com.emc.microservice.standalone.web.UndertowRestApplication;
import com.emc.microservice.standalone.web.UndertowRestEasyWebServer;
import com.emc.microservice.standalone.web.UndertowWebServerConfiguration;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import java.util.List;
import java.util.Map;

/**
 * This provider differs from the standard Undertow provider in serializing/deserializing instances of
 * ResourceConfiguration using only the properties and the class information. This allows the {@link ConfiguratorApi}
 * to receive instances of those configurations as parameters.
 * Note: the mapper must be configured on the client as well.
 */
public class ConfiguratorSpecificUndertowProvider implements WebServerProvider<UndertowWebServerConfiguration> {
    private UndertowRestEasyWebServer undertowRestEasyWebServer;

    /**
     * The ObjectMapper used to serialize/desrialize JSON by this provider, must be configured by client as well.
     */
    public static ObjectMapper getMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(ResourceConfiguration.class, ResourceConfigurationMixIn.class);
        return mapper;
    }

    @Override
    public MicroServiceWebServer getWebServer(UndertowWebServerConfiguration webServerConfiguration) {
        if (undertowRestEasyWebServer == null) {
            // Possible override deployServiceApplication
            undertowRestEasyWebServer = new UndertowRestEasyWebServer(webServerConfiguration) {
                @Override
                protected List<Object> getProviders(
                        Context context, UndertowRestApplication application) {
                    List<Object> providers = super.getProviders(context, application);
                    providers.stream()
                            .filter(o -> o instanceof ResteasyJackson2Provider)
                            .forEach(o -> ((ResteasyJackson2Provider) o).setMapper(getMapper()));
                    return providers;
                }
            };
        }
        return undertowRestEasyWebServer;
    }

    @Override
    public Class<UndertowWebServerConfiguration> getConfClass() {
        return UndertowWebServerConfiguration.class;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    @JsonAutoDetect(
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            setterVisibility = JsonAutoDetect.Visibility.NONE,
            fieldVisibility = JsonAutoDetect.Visibility.NONE,
            creatorVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    public abstract static class ResourceConfigurationMixIn {
        @JsonProperty
        private Map<String, String> propertyValues;
    }
}
