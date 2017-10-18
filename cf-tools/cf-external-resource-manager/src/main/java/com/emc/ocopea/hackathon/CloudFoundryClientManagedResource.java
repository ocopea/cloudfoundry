// Copyright (c) [2018 - 2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.hackathon;

import com.emc.microservice.resource.ManagedResource;
import org.cloudfoundry.client.CloudFoundryClient;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CloudFoundryClientManagedResource
        implements ManagedResource<CloudFoundryClientResourceDescriptor, CloudFoundryClientResourceConfiguration> {

    private final CloudFoundryClientResourceDescriptor descriptor;
    private final CloudFoundryClientResourceConfiguration configuration;
    private final Map<String, CFConnection> connectionPool = new ConcurrentHashMap<>();
    private final CloudFoundryClient cloudFoundryClient;
    private final DopplerClient cfDopplerClient;

    public CloudFoundryClientManagedResource(CloudFoundryClientResourceDescriptor descriptor,
            CloudFoundryClientResourceConfiguration configuration, CloudFoundryClient client,
            DopplerClient cfDopplerClient) {

        this.descriptor = descriptor;
        this.configuration = configuration;
        this.cloudFoundryClient = client;
        this.cfDopplerClient = cfDopplerClient;
    }

    @Override
    public String getName() {
        return descriptor.getName();
    }

    @Override
    public CloudFoundryClientResourceDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public CloudFoundryClientResourceConfiguration getConfiguration() {
        return configuration;
    }

    /***
     * Getting cf connection for a specific space.
     * Connection object should be released and not kept beyond the scope it is being used
     */
    public CFConnection getConnection(String org, String spaceName) {
        final String nameForPool = Objects.requireNonNull(org, "must supply org") + "|"
                + Objects.requireNonNull(spaceName, "must supply space");

        // Checking for a connection in the pool
        return connectionPool.computeIfAbsent(nameForPool, s -> {
            // Creating a new operations object
            DefaultCloudFoundryOperations operations = DefaultCloudFoundryOperations.builder()
                    .cloudFoundryClient(cloudFoundryClient).organization(org).space(spaceName).build();

            String spaceId = operations.getSpaceId().block();

            // Checking if we reached max pool size
            if (connectionPool.size() >= configuration.getPoolSize()) {

                // Evicting a connection. there is no issue with evicting connection that is currently being
                // used by another thread since we don't close it or anything like that, simply allowing the
                // GC to collect it once not in used
                // Eviction algorithm is random
                connectionPool.remove(connectionPool.keySet().iterator().next());
            }

            return new CFConnection(operations, cloudFoundryClient, org, spaceId);
        });
    }

    public CloudFoundryClient getCloudFoundryClient() {
        return cloudFoundryClient;
    }

    public DopplerClient getCfDopplerClient() {
        return cfDopplerClient;
    }
}
