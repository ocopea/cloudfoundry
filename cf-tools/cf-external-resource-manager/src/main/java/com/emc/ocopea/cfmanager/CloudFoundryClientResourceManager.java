// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
 
package com.emc.ocopea.cfmanager;

import com.emc.microservice.Context;
import com.emc.microservice.health.HealthCheck;
import com.emc.microservice.resource.ExternalResourceManager;
import org.cloudfoundry.client.v2.info.GetInfoRequest;
import org.cloudfoundry.client.v2.info.GetInfoResponse;
import org.cloudfoundry.doppler.DopplerClient;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.cloudfoundry.reactor.client.ReactorCloudFoundryClient;
import org.cloudfoundry.reactor.doppler.ReactorDopplerClient;
import org.cloudfoundry.reactor.tokenprovider.PasswordGrantTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

public class CloudFoundryClientResourceManager implements
                ExternalResourceManager<CloudFoundryClientResourceDescriptor, CloudFoundryClientResourceConfiguration, CloudFoundryClientManagedResource> {
        private static final Logger log = LoggerFactory.getLogger(CloudFoundryClientResourceManager.class);

        private static final String RESOURCE_NAME = "Cloudfoundry Client";
        private static final String RESOURCE_NAME_PLURAL = "Cloudfoundry Clients";

        public CloudFoundryClientResourceManager() {
        }

        @Override
        public String getResourceTypeNamePlural() {
                return RESOURCE_NAME_PLURAL;
        }

        @Override
        public String getResourceTypeName() {
                return RESOURCE_NAME;
        }

        @Override
        public CloudFoundryClientManagedResource initializeResource(
                        CloudFoundryClientResourceDescriptor resourceDescriptor,
                        CloudFoundryClientResourceConfiguration conf, Context context) {

                // Building connection context
                DefaultConnectionContext connectionContext = DefaultConnectionContext.builder()
                                .apiHost(conf.getAPIHost()).skipSslValidation(conf.isSkipSSLValidation()).build();

                // Using user authentication. in the future we might use tokens to delegate user authentication
                PasswordGrantTokenProvider tokenProvider = PasswordGrantTokenProvider.builder()
                                .username(conf.getUserName()).password(conf.getPassword()).build();

                // Building cf client
                ReactorCloudFoundryClient client = ReactorCloudFoundryClient.builder()
                                .connectionContext(connectionContext).tokenProvider(tokenProvider).build();

                // Checking connectivity using the info request. if it fails connection will not be availabe
                // and the service will not start
                final GetInfoResponse cfInfoResponse = client.info().get(GetInfoRequest.builder().build()).block();
                log.info("Connected to cf {}", cfInfoResponse);

                ReactorDopplerClient cfDopplerClient = ReactorDopplerClient.builder()
                                .connectionContext(connectionContext).tokenProvider(tokenProvider).build();

                return new CloudFoundryClientManagedResource(resourceDescriptor, conf, client, cfDopplerClient);
        }

        @Override
        public void postInitResource(CloudFoundryClientResourceDescriptor cloudFoundryClientResourceDescriptor,
                        CloudFoundryClientResourceConfiguration cloudFoundryClientResourceConfiguration,
                        CloudFoundryClientManagedResource cloudFoundryClientManagedResource, Context context) {
        }

        @Override
        public void cleanUpResource(CloudFoundryClientManagedResource resourceToCleanUp) {

        }

        @Override
        public void pauseResource(CloudFoundryClientManagedResource resourceToPause) {

        }

        @Override
        public void startResource(CloudFoundryClientManagedResource resourceToStart) {

        }

        @Override
        public Class<CloudFoundryClientResourceConfiguration> getResourceConfigurationClass() {
                return CloudFoundryClientResourceConfiguration.class;
        }

        @Override
        public Class<CloudFoundryClientResourceDescriptor> getDescriptorClass() {
                return CloudFoundryClientResourceDescriptor.class;
        }

        @Override
        public Collection<HealthCheck> getResourceHealthChecks(CloudFoundryClientManagedResource managedResource) {

                //todo:add health check for cf connectivity..
                return Collections.emptyList();
                //todo:add error handlers for disconnect if library supports?
        }
}
