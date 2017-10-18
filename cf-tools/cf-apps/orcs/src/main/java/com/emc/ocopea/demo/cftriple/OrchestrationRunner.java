package com.emc.ocopea.demo.cftriple;

import com.emc.ocopea.demo.CFResourceProviderFactory;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.hub.HubMicroService;
import com.emc.ocopea.hub.webapp.HubWebAppMicroService;
import com.emc.ocopea.messaging.PersistentMessagingProvider;
import com.emc.ocopea.protection.ProtectionMicroService;
import com.emc.ocopea.site.SiteMicroService;

import java.io.IOException;
import java.sql.SQLException;

public class OrchestrationRunner {
    @NoJavadoc
    public static void main(String[] args) throws IOException, SQLException {
        CFResourceProviderFactory.runServices(
                PersistentMessagingProvider::new,
                new ProtectionMicroService(),
                new SiteMicroService(),
                new HubMicroService(),
                new HubWebAppMicroService()
        );
    }
}
