package org.xbib.elasticsearch;

import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.Streams;
import org.junit.Test;
import org.xbib.elasticsearch.action.deploy.DeployAction;
import org.xbib.elasticsearch.action.deploy.DeployRequest;
import org.xbib.elasticsearch.action.deploy.DeployRequestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SimpleTest extends AbstractNodeTestHelper {

    @Test
    public void simpleDeployTest() throws IOException, InterruptedException {
        String name = "demo";
        InputStream in = getClass().getResourceAsStream("/example.zip");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Streams.copy(in, out);
        BytesReference content = new BytesArray(out.toByteArray());
        String contentType = "application/zip";
        DeployRequestBuilder deployRequestBuilder = new DeployRequestBuilder(client("1").admin().cluster())
                .setName(name)
                .setContentType(contentType)
                .setContent(content);
        final DeployRequest deployRequest = deployRequestBuilder.request();
        client("1").admin().cluster().execute(DeployAction.INSTANCE, deployRequest);
        Thread.sleep(5000L);
    }
}
