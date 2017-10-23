// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
 
package com.emc.ocopea.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Service {
    private final String name;
    private final String label;
    private final List<String> tags;
    private final String plan;
    private final Map<String, String> credentials;

    @JsonCreator
    public Service(@JsonProperty("name") String name, @JsonProperty("label") String label,
            @JsonProperty("tags") List<String> tags, @JsonProperty("credentials") Map<String, String> credentials,
            @JsonProperty("plan") String plan) {
        this.name = name;
        this.label = label;
        this.tags = Collections.unmodifiableList(tags);
        this.credentials = Collections.unmodifiableMap(credentials);
        this.plan = plan;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getTags() {
        return tags;
    }

    public Map<String, String> getCredentials() {
        return credentials;
    }

    public String getPlan() {
        return plan;
    }
}
