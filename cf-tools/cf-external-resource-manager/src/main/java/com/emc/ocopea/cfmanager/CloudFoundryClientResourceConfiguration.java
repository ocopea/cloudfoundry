// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
 
package com.emc.ocopea.cfmanager;

import com.emc.microservice.resource.ResourceConfiguration;
import com.emc.microservice.resource.ResourceConfigurationProperty;
import com.emc.microservice.resource.ResourceConfigurationPropertyType;

import java.util.Arrays;
import java.util.List;

/**
 * Created with true love by liebea on 11/12/2014.
 */
public class CloudFoundryClientResourceConfiguration extends ResourceConfiguration {
    private static final String CONFIGURATION_NAME = "Cloud foundry Client Configuration";

    private static final ResourceConfigurationProperty API_HOST = new ResourceConfigurationProperty("apiHost",
            ResourceConfigurationPropertyType.STRING, "CF API Host", true, false);

    private static final ResourceConfigurationProperty USERNAME = new ResourceConfigurationProperty("userName",
            ResourceConfigurationPropertyType.STRING, "User name", true, true);

    private static final ResourceConfigurationProperty PASSWORD = new ResourceConfigurationProperty("password",
            ResourceConfigurationPropertyType.STRING, "Password", true, true);

    private static final ResourceConfigurationProperty SKIP_SSL_VALIDATION = new ResourceConfigurationProperty(
            "skipSSLValidation", ResourceConfigurationPropertyType.BOOLEAN, "Skip SSL Validation", false, false);

    private static final ResourceConfigurationProperty POOL_SIZE = new ResourceConfigurationProperty("poolSize",
            ResourceConfigurationPropertyType.INT, "Size of the connection pool", false, false);

    private static final List<ResourceConfigurationProperty> PROPERTIES = Arrays.asList(API_HOST, USERNAME, PASSWORD,
            SKIP_SSL_VALIDATION, POOL_SIZE);

    public CloudFoundryClientResourceConfiguration() {
        super(CONFIGURATION_NAME, PROPERTIES);
    }

    public CloudFoundryClientResourceConfiguration(String apiHost, String userName, String password,
            boolean skipSSLCertificate, int poolSize) {
        this();
        setPropertyValues(propArrayToMap(new String[] { API_HOST.getName(), apiHost, USERNAME.getName(), userName,
                PASSWORD.getName(), password, SKIP_SSL_VALIDATION.getName(), Boolean.toString(skipSSLCertificate),
                POOL_SIZE.getName(), Integer.toString(poolSize) }));
    }

    public String getAPIHost() {
        return getProperty(API_HOST.getName());
    }

    public String getUserName() {
        return getProperty(USERNAME.getName());
    }

    public String getPassword() {
        return getProperty(PASSWORD.getName());
    }

    public boolean isSkipSSLValidation() {
        return Boolean.parseBoolean(getProperty(SKIP_SSL_VALIDATION.getName()));
    }

    public int getPoolSize() {
        return Integer.valueOf(getProperty(POOL_SIZE.getName()));
    }
}
