// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
 
package com.emc.ocopea.demo;

import com.emc.ocopea.demo.model.Service;
import com.emc.ocopea.demo.model.VcapApplication;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CFServices {
    public static final String VCAP_APPLICATION_NAME = "VCAP_APPLICATION";
    public static final String VCAP_SERVICES_NAME = "VCAP_SERVICES";
    public static final String PORT_NAME = "PORT";

    private static final CFServices instance = new CFServices();
    private final VcapApplication vcapApplication;
    private final Map<String, List<Service>> vcapServices;
    private final String port;

    private CFServices() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            vcapApplication = mapper.readValue(System.getenv(VCAP_APPLICATION_NAME), VcapApplication.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse " + VCAP_APPLICATION_NAME + " system property", e);
        }
        try {
            Map<String, List<Service>> vcapServicesMap = mapper.readValue(System.getenv(VCAP_SERVICES_NAME),
                    new TypeReference<Map<String, List<Service>>>() {
                    });
            for (String key : vcapServicesMap.keySet()) {
                vcapServicesMap.put(key, Collections.unmodifiableList(vcapServicesMap.get(key)));
            }
            vcapServices = Collections.unmodifiableMap(vcapServicesMap);
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse " + VCAP_SERVICES_NAME + " system property", e);
        }
        port = System.getenv(PORT_NAME);
    }

    public static CFServices getInstance() {
        return instance;
    }

    public Collection<String> getAppUris() {
        return vcapApplication.getUris();
    }

    public String getSpaceName() {
        return vcapApplication.getSpaceName();
    }

    public String getPort() {
        return port;
    }

    public List<Service> getServices(String type) {
        return vcapServices.get(type);
    }
}
