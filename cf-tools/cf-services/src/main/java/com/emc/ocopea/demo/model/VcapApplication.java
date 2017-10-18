// Copyright (c) [2018 - 2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VcapApplication {
    private final String name;
    private final UUID version;
    private final UUID applicationVersion;
    private final String applicationName;
    private final List<String> applicationsURIs;
    private final Map<String, String> limits;
    private final String spaceName;
    private final UUID spaceId;
    private final List<String> uris;
    private final String users;

    @JsonCreator
    public VcapApplication(@JsonProperty("name") String name, @JsonProperty("version") UUID version,
            @JsonProperty("application_version") UUID applicationVersion,
            @JsonProperty("application_name") String applicationName,
            @JsonProperty("application_uris") List<String> applicationsURIs,
            @JsonProperty("limits") Map<String, String> limits, @JsonProperty("space_name") String spaceName,
            @JsonProperty("space_id") UUID spaceId, @JsonProperty("uris") List<String> uris,
            @JsonProperty("users") String users) {
        this.name = name;
        this.version = version;
        this.applicationVersion = applicationVersion;
        this.applicationName = applicationName;
        this.applicationsURIs = Collections.unmodifiableList(applicationsURIs);
        this.limits = Collections.unmodifiableMap(limits);
        this.spaceName = spaceName;
        this.spaceId = spaceId;
        this.uris = Collections.unmodifiableList(uris);
        this.users = users;
    }

    public String getName() {
        return name;
    }

    public UUID getVersion() {
        return version;
    }

    public UUID getApplicationVersion() {
        return applicationVersion;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public List<String> getApplicationsURIs() {
        return applicationsURIs;
    }

    public Map<String, String> getLimits() {
        return limits;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public UUID getSpaceId() {
        return spaceId;
    }

    public List<String> getUris() {
        return uris;
    }

    public String getUsers() {
        return users;
    }
}
