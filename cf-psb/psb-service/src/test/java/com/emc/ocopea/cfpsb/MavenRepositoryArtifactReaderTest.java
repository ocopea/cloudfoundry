// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.webclient.WebAPIResolver;
import com.emc.microservice.webclient.WebApiResolverBuilder;
import com.emc.ocopea.util.io.StreamUtil;
import com.emc.ocopea.util.rest.RestClientUtil;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Created by liebea on 1/25/17.
 * Drink responsibly
 */
public class MavenRepositoryArtifactReaderTest {
    private static String convertStreamToString(InputStream is) {
        Scanner s = new Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    //todo: uncomment test when bintray is back
    //@Test
    public void testMe() {

        String repositoryURL = readFile("repository-name.txt");
        String msApiVersion = readFile("ms-api-version.txt");
        final MavenRepositoryArtifactReader repositoryArtifactReader = new MavenRepositoryArtifactReader(repositoryURL,
                new TestApiResolver());
        Response response = repositoryArtifactReader.readArtifact("com.emc.ocopea.microservice.api", "microservice-api",
                msApiVersion, "jar", null);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            StreamUtil.copyLarge(response.readEntity(InputStream.class), baos);
            Assert.assertTrue(baos.size() > 10000 && baos.size() < 1000000);
        } catch (IOException e) {
            throw new IllegalStateException("Test has failed ha ha ha", e);
        } finally {
            response.close();
        }

    }

    public String readFile(String fileName) {
        try (InputStream inputStream = this.getClass().getResourceAsStream(fileName)) {
            return convertStreamToString(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Test failed, bye", e);
        }
    }

    private static class TestApiResolver implements WebAPIResolver {

        @Override
        public WebAPIResolver buildResolver(WebApiResolverBuilder builder) {
            return this;
        }

        @Override
        public <T> T getWebAPI(String url, Class<T> resourceWebAPI) {
            ResteasyWebTarget target = getResteasyWebTarget(url);
            return target.proxy(resourceWebAPI);
        }

        @Override
        public WebTarget getWebTarget(String url) {
            return getResteasyWebTarget(url);
        }

        private ResteasyWebTarget getResteasyWebTarget(String url) {
            return RestClientUtil.getWebTarget(url);
        }
    }

}
