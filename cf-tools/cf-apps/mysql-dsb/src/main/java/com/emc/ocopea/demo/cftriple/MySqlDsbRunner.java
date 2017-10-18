package com.emc.ocopea.demo.cftriple;

import com.emc.ocopea.demo.CFResourceProviderFactory;
import com.emc.ocopea.demo.CFServices;
import com.emc.ocopea.devtools.checkstyle.NoJavadoc;
import com.emc.ocopea.dsb.mysql.MySqlDSBMicroService;

import java.io.IOException;
import java.sql.SQLException;

public class MySqlDsbRunner {
    @NoJavadoc
    public static void main(String[] args) throws IOException, SQLException {
        String cfOrg = System.getenv("CF_ORG");
        if (cfOrg == null) {
            throw new IllegalArgumentException("requires CF_ORG environment variable");
        }
        System.setProperty("OCOPEA_CF_ORG", cfOrg);
        String cfSpace = System.getenv("CF_SPACE");
        if (cfSpace == null) {
            cfSpace = CFServices.getInstance().getSpaceName();
        }
        System.setProperty("OCOPEA_CF_SPACE", cfSpace);

        CFResourceProviderFactory.runServices(
                new MySqlDSBMicroService()
        );
    }
}
