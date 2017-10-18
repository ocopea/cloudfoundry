// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.ocopea.demo.CFResourceProviderFactory;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;

import java.io.IOException;
import java.sql.SQLException;

public class CFPaaSRunner {
    @NoJavadoc
    // TODO add javadoc
    public static void main(String[] args) throws IOException, SQLException {
        String cfOrg = System.getenv("CF_ORG");
        if (cfOrg == null) {
            throw new IllegalArgumentException("requires CF_ORG environment variable");
        }
        System.setProperty("OCOPEA_CF_ORG", cfOrg);
        CFResourceProviderFactory.runServices(new CloudFoundryPSBMicroService());
    }
}
