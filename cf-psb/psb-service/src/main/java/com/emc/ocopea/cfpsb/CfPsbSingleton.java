// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.Context;
import com.emc.microservice.blobstore.BlobStoreAPI;
import com.emc.microservice.singleton.ServiceLifecycle;
import com.emc.microservice.webclient.WebAPIResolver;
import com.emc.microservice.webclient.WebApiResolverBuilder;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.cfmanager.CFConnection;
import com.emc.ocopea.cfmanager.CloudFoundryClientManagedResource;
import com.emc.ocopea.cfmanager.CloudFoundryClientResourceDescriptor;
import com.emc.ocopea.psb.DeployAppServiceManifestDTO;
import com.emc.ocopea.psb.PSBAppServiceInstanceDTO;
import com.emc.ocopea.psb.PSBAppServiceStatusEnumDTO;
import com.emc.ocopea.psb.PSBServiceBindingInfoDTO;
import com.emc.ocopea.psb.PSBSpaceDTO;
import com.emc.ocopea.util.MapBuilder;
import org.cloudfoundry.client.v2.securitygroups.CreateSecurityGroupRequest;
import org.cloudfoundry.client.v2.securitygroups.RuleEntity;
import org.cloudfoundry.client.v2.spaces.ListSpacesRequest;
import org.cloudfoundry.operations.applications.DeleteApplicationRequest;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.applications.StopApplicationRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateUserProvidedServiceInstanceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class CfPsbSingleton implements ServiceLifecycle {
    private static final Logger log = LoggerFactory.getLogger(CfPsbSingleton.class);

    private static final int MAX_CF_NAME_LENGTH = 50;
    private static final String CF_SERVICE_NAME_BINDING_INFO_KEY = "cf-service-name";
    private static final String IMAGES_NAMESPACE = "images";
    private CloudFoundryClientManagedResource cfResource;
    private BlobStoreAPI imagesBank;
    private WebAPIResolver webAPIResolver;
    private boolean createSecurityGroup = true;
    private String org;
    private final Set<String> inProgressDeployingAppInstanceIds = new ConcurrentSkipListSet<>();

    /***
     * List spaces in the current CF instance
     */
    public List<PSBSpaceDTO> listSpaces() {
        return cfResource.getCloudFoundryClient().spaces().list(ListSpacesRequest.builder().build()).block()
                .getResources().stream()
                .map(spaceSummary -> new PSBSpaceDTO(spaceSummary.getEntity().getName(), MapBuilder
                        .<String, String>newHashMap().with("spaceId", spaceSummary.getMetadata().getId()).build()))
                .collect(Collectors.toList());
    }

    @Override
    public void init(Context context) {
        cfResource = context.getManagedResourceByDescriptor(CloudFoundryClientResourceDescriptor.class, "cf");
        imagesBank = context.getBlobStoreManager().getManagedResourceByName("hack-images-bank").getBlobStoreAPI();
        createSecurityGroup = context.getParametersBag().getBoolean("create-sg");
        webAPIResolver = context.getWebAPIResolver();
        org = System.getProperty("OCOPEA_CF_ORG");
    }

    @Override
    public void shutDown() {
    }

    @NoJavadoc
    // TODO add javadoc
    public void stop(String spaceName, String appInstanceId) {
        MonoContext monoContext = new MonoContext();
        String cfAppName = getAppCFName(appInstanceId);

        monoContext.append(mono -> mono.then(cfResource.getConnection(org, spaceName).getCloudFoundryOperations()
                .applications().delete(DeleteApplicationRequest.builder().name(cfAppName).deleteRoutes(true).build())));

        monoContext.mono.subscribe().block();
    }

    @NoJavadoc
    // TODO add javadoc
    public void pause(String spaceName, String appInstanceId) {
        MonoContext monoContext = new MonoContext();
        String cfAppName = getAppCFName(appInstanceId);

        monoContext.append(mono -> mono.then(cfResource.getConnection(org, spaceName).getCloudFoundryOperations()
                .applications().stop(StopApplicationRequest.builder().name(cfAppName).build())));

        monoContext.mono.subscribe().block();
    }

    @NoJavadoc
    // TODO add javadoc
    public void deploy(DeployAppServiceManifestDTO appServiceManifest) {
        inProgressDeployingAppInstanceIds.add(appServiceManifest.getAppServiceId());

        try {
            MonoContext monoContext = new MonoContext();
            String cfAppName = getAppCFName(appServiceManifest.getAppServiceId());
            boolean hasDependencies = appServiceManifest.getServiceBindings() != null
                    && !appServiceManifest.getServiceBindings().isEmpty();

            final CFConnection cfConnection = cfResource.getConnection(org, appServiceManifest.getSpace());
            if (hasDependencies) {
                // Create User Provided service for each DSB
                appServiceManifest.getServiceBindings().entrySet()
                        .forEach(dsbTypeEntry -> dsbTypeEntry.getValue().forEach(bindingEntry -> {

                            // In case the service is not a native cf service, we need to create a user provided service
                            // in order to allow cf apps connecting to it
                            if (!bindingEntry.getBindInfo().containsKey(CF_SERVICE_NAME_BINDING_INFO_KEY)) {
                                String serviceName = bindingEntry.getServiceId();
                                monoContext.append(mono -> {
                                    Map<String, Object> credentials = new HashMap<>(bindingEntry.getBindInfo());
                                    credentials.put("serviceLogicalName", bindingEntry.getServiceName());

                                    return mono.then(cfConnection.getCloudFoundryOperations().services()
                                            .createUserProvidedInstance(CreateUserProvidedServiceInstanceRequest
                                                    .builder().name(serviceName).credentials(credentials).build())
                                            .doOnError(throwable -> {
                                                deploymentError(appServiceManifest.getAppServiceId());
                                                log.error("err", throwable);
                                            }).doOnSuccess(aVoid -> log.info("yey, service created")));
                                });
                            }
                        }));
                // Create security group for the app
                if (createSecurityGroup) {
                    createSecurityGroup(appServiceManifest, monoContext, cfAppName);
                }
            }

            // Push the app
            pushTheApp(cfConnection, monoContext, appServiceManifest, cfAppName);

            monoContext.mono.subscribe().block();

            // Removing the in progress flag
            inProgressDeployingAppInstanceIds.remove(appServiceManifest.getAppServiceId());

        } catch (Exception ex) {
            deploymentError(appServiceManifest.getAppServiceId());
        }
    }

    private void deploymentError(String appServiceId) {
        inProgressDeployingAppInstanceIds.remove(appServiceId);
    }

    private void createSecurityGroup(DeployAppServiceManifestDTO appServiceManifest, MonoContext monoContext,
            String cfAppName) {
        monoContext.append(mono -> {

            CreateSecurityGroupRequest.Builder createSGBuilder = CreateSecurityGroupRequest.builder()
                    .name(cfAppName + "-sg").spaceId(appServiceManifest.getSpace());

            appServiceManifest.getServiceBindings().entrySet().forEach(dsbTypeEntry -> dsbTypeEntry.getValue().forEach(
                    bindingEntry -> createSGBuilder.addAllRules(bindingEntry.getPorts().stream().map(psbBindPortDTO -> {
                        try {
                            return RuleEntity.builder().protocol(psbBindPortDTO.getProtocol())
                                    .destination(
                                            InetAddress.getByName(psbBindPortDTO.getDestination()).getHostAddress())
                                    .ports(Integer.toString(psbBindPortDTO.getPort())).build();
                        } catch (UnknownHostException e) {
                            throw new IllegalArgumentException("unknown host for bind", e);
                        }
                    }).collect(Collectors.toList()))));

            return mono.then(cfResource.getCloudFoundryClient().securityGroups().create(createSGBuilder.build()));
        });
    }

    private String getAppCFName(String appInstanceId) {
        return getValidCFName(appInstanceId);
    }

    private String getValidCFName(String name) {
        if (name.length() > MAX_CF_NAME_LENGTH) {
            name = name.substring(MAX_CF_NAME_LENGTH);
        }
        return name;
    }

    private void pushTheApp(CFConnection cf, MonoContext monoContext,
            DeployAppServiceManifestDTO appServiceManifest, String cfAppName) {
        log.info("####### Pushing the app " + cfAppName);
        Path appPath = getAppPath(appServiceManifest);

        // Pushy push
        monoContext.append(mono -> mono.then(cf.getCloudFoundryOperations().applications()
                .push(getPushApplicationRequest(cfAppName, appPath, appServiceManifest.getPsbSettings()))
                .doOnError(th -> {
                    log.info("error in push " + th.getMessage(), th);
                    deploymentError(appServiceManifest.getAppServiceId());
                }).doAfterTerminate((unused, throwable1) -> {
                    try {
                        Files.deleteIfExists(appPath);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).doOnSuccess(aVoid -> log.info("successfully pushed app {}", cfAppName))));

        // If has dependencies - call bind on each dsb
        final Map<String, Collection<PSBServiceBindingInfoDTO>> serviceBindings = appServiceManifest
                .getServiceBindings();
        if (serviceBindings != null) {
            serviceBindings.entrySet().forEach(dsbEntry -> dsbEntry.getValue().forEach(bindingInfo -> {

                String cfServiceName;
                if (bindingInfo.getBindInfo() != null
                        && bindingInfo.getBindInfo().containsKey(CF_SERVICE_NAME_BINDING_INFO_KEY)) {
                    cfServiceName = bindingInfo.getBindInfo().get(CF_SERVICE_NAME_BINDING_INFO_KEY);
                } else {
                    cfServiceName = bindingInfo.getServiceId();
                }
                monoContext.append(mono -> mono.then(cf.getCloudFoundryOperations().services()
                        .bind(BindServiceInstanceRequest.builder().applicationName(cfAppName)
                                .serviceInstanceName(cfServiceName)
                                //.parameter("bind", serviceEntry.getValue())
                                .build())
                        .doOnError(throwable -> {
                            log.error("Failed binding service " + cfServiceName, throwable);
                            deploymentError(appServiceManifest.getAppServiceId());
                        }).doOnSuccess(aVoid -> log.info("Bound service {}", cfServiceName))));
            }));
        }

        // Setting environment variables when available
        Map<String, String> envVarsToSet = appServiceManifest.getEnvironmentVariables();
        if (envVarsToSet != null) {
            envVarsToSet.entrySet()
                    .forEach(
                            var -> monoContext.append(mono -> mono.then(cf.getCloudFoundryOperations().applications()
                                    .setEnvironmentVariable(SetEnvironmentVariableApplicationRequest.builder()
                                            .name(cfAppName).variableName(var.getKey()).variableValue(var.getValue())
                                            .build()))));
        }

        // When all bound if needed - start the app
        monoContext.append(mono -> mono.then(cf.getCloudFoundryOperations().applications()
                .start(StartApplicationRequest.builder().name(cfAppName).build()).doOnError(throwable -> {
                    log.error("error starting ", throwable);
                    deploymentError(appServiceManifest.getAppServiceId());
                }).doOnSuccess(aVoid -> log.info("app {} started", cfAppName))));

    }

    private PushApplicationRequest getPushApplicationRequest(String cfAppName, Path appPath,
            Map<String, String> psbSettings) {

        // Calculating memory
        int memory = parseMemoryParam(psbSettings);

        return PushApplicationRequest.builder().noStart(true).application(appPath).name(cfAppName).instances(1)
                .memory(memory).build();
    }

    private int parseMemoryParam(Map<String, String> psbSettings) {
        int memory = 192;
        if (psbSettings != null) {
            String memoryStr = psbSettings.get("memory");
            if (memoryStr != null) {
                // Expected format is similar to cf manifest file number followed by M
                if (memoryStr.endsWith("M")) {
                    memoryStr = memoryStr.substring(0, memoryStr.length() - 1);
                }
                try {
                    memory = Integer.parseInt(memoryStr);
                } catch (NumberFormatException nfe) {
                    log.warn("Failed parsing memory psb Settings " + psbSettings.get("memory"), nfe);
                }
            }
        }
        return memory;
    }

    private Path getAppPath(DeployAppServiceManifestDTO appServiceManifest) {

        // Getting the artifact according to the supported artifact registry types
        switch (appServiceManifest.getArtifactRegistryType()) {
        case "mavenRepository":
            return getMavenRepoPath(appServiceManifest.getArtifactRegistryParameters(),
                    appServiceManifest.getImageName(), appServiceManifest.getImageVersion());
        case "customRest":
            return getAppBits(appServiceManifest.getImageName(), appServiceManifest.getImageVersion());
        default:
            throw new UnsupportedOperationException(
                    "Unsupported artifact registry type " + appServiceManifest.getArtifactRegistryType());
        }

    }

    private Path getMavenRepoPath(Map<String, String> artifactRegistryParameters, String imageName,
            String imageVersion) {

        final String mavenRepoUrl = Objects.requireNonNull(artifactRegistryParameters.get("url"),
                "missing required maven repository parameter - url");
        final String mavenRepoUsername = artifactRegistryParameters.get("username");
        final String mavenRepoPassword = artifactRegistryParameters.get("password");

        WebAPIResolver resolver = this.webAPIResolver;
        if (mavenRepoUsername != null && !mavenRepoUsername.isEmpty() && mavenRepoPassword != null) {
            resolver = resolver.buildResolver(new WebApiResolverBuilder()
                    .withBasicAuthentication(mavenRepoUsername, mavenRepoPassword).withVerifySsl(false));
        }
        MavenRepositoryArtifactReader repositoryArtifactReader = new MavenRepositoryArtifactReader(mavenRepoUrl,
                resolver);

        // Downloading the artifact from the maven registry
        final String[] artifactParts = imageName.split(":");
        String classifier = null;
        if (artifactParts.length > 3) {
            classifier = artifactParts[3];
        }
        Response response = repositoryArtifactReader.readArtifact(artifactParts[0], artifactParts[1], imageVersion,
                artifactParts[2], classifier);

        if (response == null) {
            throw new IllegalStateException("Received empty stream while reading artifact from maven repo "
                    + mavenRepoUrl + " artifact:" + imageName + ":" + imageVersion);
        }
        try {
            return pathFromInputStream(response.readEntity(InputStream.class));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed reading artifact from maven repository " + mavenRepoUrl + " - " + e.getMessage(), e);
        } finally {
            response.close();
        }

    }

    public Path tryBits(String imageName, String imageVersion) {
        return getAppBits(imageName, imageVersion);
    }

    public List<String> availableVersions(String imageName) {
        if (imagesBank.isExists(IMAGES_NAMESPACE, imageName)) {
            return Collections.singletonList("1.0");
        } else {
            return Collections.emptyList();
        }
    }

    private Path getAppBits(String imageName, String imageVersion) {

        log.info("Getting app bits for image {} version {}", imageName, imageVersion);

        final Path[] tempFile = { null };
        //todo:big todo :)
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(imageName + "-app.zip")) {
            tempFile[0] = pathFromInputStream(inputStream);
            if (tempFile[0] != null) {
                log.info("Yey, found image as resource for {} {}", imageName, imageVersion);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        if (tempFile[0] == null) {
            imagesBank.readBlob(IMAGES_NAMESPACE, imageName, in -> {
                tempFile[0] = pathFromInputStream(in);
                if (tempFile[0] != null) {
                    log.info("Yey, found image as fake bank for {} {}", imageName, imageVersion);
                }

            });
        }

        if (tempFile[0] == null) {
            throw new IllegalStateException(" failed locating image for " + imageName + " version: " + imageVersion);
        }
        return tempFile[0];

    }

    private Path pathFromInputStream(InputStream inputStream) {
        try {
            if (inputStream != null) {
                Path tempFile = Files.createTempFile("fun", "stuff");
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                return tempFile;
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /***
     * Return psb app status
     * @param space space to query fore
     * @param appServiceId cf appService id == app name
     */
    public PSBAppServiceInstanceDTO getAppStatus(String space, String appServiceId) {
        final String cfAppName = getAppCFName(appServiceId);

        // Checking if still in progress
        if (inProgressDeployingAppInstanceIds.contains(appServiceId)) {
            return new PSBAppServiceInstanceDTO(cfAppName, PSBAppServiceStatusEnumDTO.starting, "Pushing app", 0,
                    Collections.emptyMap(), null);
        }

        try {
            return cfResource.getConnection(org, space).getCloudFoundryOperations().applications()
                    .get(GetApplicationRequest.builder().name(cfAppName).build()).doOnError(throwable -> {
                        //todo:err
                        log.info("dude, bad thing " + throwable.getMessage());
                        log.debug("Ouch", throwable);
                        //todo:debug not info
                    }).doOnSuccess(applicationDetail1 -> log.info("yey, app status for {}", cfAppName)

                    ).map(applicationDetail -> {
                        String requestedState = applicationDetail.getRequestedState();

                        PSBAppServiceStatusEnumDTO state = PSBAppServiceStatusEnumDTO.running;
                        switch (applicationDetail.getRequestedState().toLowerCase()) {
                        case "stopped":
                            state = PSBAppServiceStatusEnumDTO.stopped;
                            break;
                        case "started":
                            state = PSBAppServiceStatusEnumDTO.running;
                            break;
                        default:
                            state = PSBAppServiceStatusEnumDTO.stopped;
                            break;
                        }

                        return new PSBAppServiceInstanceDTO(applicationDetail.getName(), state, requestedState,
                                applicationDetail.getInstances(),
                                MapBuilder.<String, String>newHashMap()
                                        .with("runningInstances",
                                                stringNotNull(applicationDetail.getRunningInstances()))
                                        .with("stack", applicationDetail.getStack())
                                        .with("diskQuota", stringNotNull(applicationDetail.getDiskQuota()))
                                        .with("memoryLimit", stringNotNull(applicationDetail.getMemoryLimit()))
                                        .with("id", applicationDetail.getId()).build(),
                                "http://" + applicationDetail.getUrls().get(0));
                    }).block();

        } catch (Exception ex) {
            log.info("Quieting that bad thing " + ex.getMessage());
            log.debug("Ouch", ex);
            throw new NotFoundException(cfAppName + "not found");
        }
    }

    private String stringNotNull(Integer metric) {
        return metric == null ? "" : metric.toString();
    }

    public void storeFakeImage(String imageName, InputStream inputStream) {
        imagesBank.create(IMAGES_NAMESPACE, imageName, Collections.emptyMap(), inputStream);
    }

    private static class MonoContext {
        private Mono mono = Mono.empty();

        public void append(MonoAppender appender) {
            mono = appender.append(this.mono);
        }

        public interface MonoAppender {
            Mono append(Mono mono);
        }
    }
}
