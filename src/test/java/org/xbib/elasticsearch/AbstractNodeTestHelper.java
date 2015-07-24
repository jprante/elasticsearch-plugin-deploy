package org.xbib.elasticsearch;

import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.client.support.AbstractClient;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.network.NetworkUtils;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.Node;
import org.junit.After;
import org.junit.Before;
import org.xbib.elasticsearch.plugin.deploy.DeployPlugin;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

public abstract class AbstractNodeTestHelper {

    protected final static ESLogger logger = ESLoggerFactory.getLogger("test");

    private Map<String, Node> nodes = new HashMap<>();

    private Map<String, AbstractClient> clients = new HashMap<>();

    private AtomicInteger counter = new AtomicInteger();

    private String cluster;

    private String host;

    private int port;

    protected void setClusterName() {
        this.cluster = "test-deploy-cluster-"
                + NetworkUtils.getLocalAddress().getHostName()
                + "-" + System.getProperty("user.name")
                + "-" + counter.incrementAndGet();
    }

    protected String getClusterName() {
        return cluster;
    }

    protected String getHome() {
        return System.getProperty("path.home");
    }

    protected Settings getSettings() {
        return settingsBuilder()
                .put("host", host)
                .put("port", port)
                .put("cluster.name", cluster)
                .put("path.home", getHome())
                .put("plugin.types", DeployPlugin.class.getName())
                .build();
    }

    protected Settings getNodeSettings() {
        return settingsBuilder()
                .put("cluster.name", cluster)
                .put("cluster.routing.schedule", "50ms")
                .put("cluster.routing.allocation.disk.threshold_enabled", false)
                .put("discovery.zen.multicast.enabled", true)
                .put("discovery.zen.multicast.ping_timeout", "5s")
                .put("gateway.type", "none")
                .put("http.enabled", false)
                .put("path.home", getHome())
                .put("plugin.types", DeployPlugin.class.getName())
                .build();
    }

    @Before
    public void startNodes() throws Exception {
        setClusterName();
        startNode("1");
        findNodeAddress();
        logger.info("ready");
    }

    @After
    public void stopNodes() throws Exception {
        try {
            logger.info("deleting all indices");
            // delete all indices
            client("1").admin().indices().prepareDelete("_all").execute().actionGet();
        } catch (Exception e) {
            logger.error("can not delete indexes", e);
        } finally {
            closeAllNodes();
        }
    }

    protected Node startNode(String id) {
        return buildNode(id).start();
    }

    public AbstractClient client(String id) {
        return clients.get(id);
    }

    private Node buildNode(String id) {
        String settingsSource = getClass().getName().replace('.', '/') + ".yml";
        Settings finalSettings = settingsBuilder()
                .loadFromClasspath(settingsSource)
                .put(getNodeSettings())
                .put("name", id)
                .build();
        logger.info("settings={}", finalSettings.getAsMap());
        Node node = nodeBuilder()
                .settings(finalSettings).build();
        AbstractClient client = (AbstractClient)node.client();
        nodes.put(id, node);
        clients.put(id, client);
        return node;
    }

    protected void findNodeAddress() {
        NodesInfoRequest nodesInfoRequest = new NodesInfoRequest().transport(true);
        NodesInfoResponse response = client("1").admin().cluster().nodesInfo(nodesInfoRequest).actionGet();
        Object obj = response.iterator().next().getTransport().getAddress()
                .publishAddress();
        if (obj instanceof InetSocketTransportAddress) {
            InetSocketTransportAddress address = (InetSocketTransportAddress) obj;
            host = address.address().getHostName();
            port = address.address().getPort();
        }
    }

    public void closeAllNodes() throws IOException {
        logger.info("closing all clients");
        for (AbstractClient client : clients.values()) {
            client.close();
        }
        clients.clear();
        logger.info("closing all nodes");
        for (Node node : nodes.values()) {
            if (node != null) {
                node.close();
            }
        }
        nodes.clear();
        logger.info("all nodes closed");
    }

}
