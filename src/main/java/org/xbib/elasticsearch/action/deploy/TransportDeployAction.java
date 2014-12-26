/*
 * Copyright 2014 Jörg Prante
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xbib.elasticsearch.action.deploy;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.TransportNodesOperationAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.xbib.elasticsearch.module.deploy.DeployService;
import org.xbib.elasticsearch.plugin.deploy.DeployPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.elasticsearch.common.collect.Lists.newArrayList;
import static org.elasticsearch.common.collect.Maps.newHashMap;

public class TransportDeployAction extends TransportNodesOperationAction<DeployRequest, DeployResponse, DeployNodeRequest, DeployNodeResponse> {

    private final Environment environment;

    private final DeployService deployService;

    @Inject
    public TransportDeployAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
                                 ClusterService clusterService, TransportService transportService,
                                 ActionFilters actionFilters,
                                 Environment environment,
                                 DeployService deployService) {
        super(settings, DeployAction.NAME, clusterName, threadPool, clusterService, transportService, actionFilters);
        this.environment = environment;
        this.deployService = deployService;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.MANAGEMENT;
    }

    @Override
    protected DeployRequest newRequest() {
        return new DeployRequest();
    }

    @Override
    protected DeployResponse newResponse(DeployRequest request, AtomicReferenceArray nodesResponses) {
        final List<DeployNodeResponse> nodes = newArrayList();
        for (int i = 0; i < nodesResponses.length(); i++) {
            Object resp = nodesResponses.get(i);
            if (resp instanceof DeployNodeResponse) {
                nodes.add((DeployNodeResponse) resp);
            }
        }
        return new DeployResponse(clusterName, nodes.toArray(new DeployNodeResponse[nodes.size()]));
    }

    @Override
    protected DeployNodeRequest newNodeRequest() {
        return new DeployNodeRequest();
    }

    @Override
    protected DeployNodeRequest newNodeRequest(String nodeId, DeployRequest request) {
        return new DeployNodeRequest(nodeId, request);
    }

    @Override
    protected DeployNodeResponse newNodeResponse() {
        return new DeployNodeResponse();
    }

    @Override
    protected DeployNodeResponse nodeOperation(DeployNodeRequest request) throws ElasticsearchException {
        DeployNodeResponse response = new DeployNodeResponse(clusterService.localNode());
        if (request.getRequest().getRead()) {
            Map<String, Object> m = newHashMap();
            Map<String, Plugin> plugins = deployService.getRegistry().getPlugins();
            for (String key : plugins.keySet()) {
                m.put(key, plugins.get(key));
            }
            response.setPlugins(m);
            return response;
        }
        String name = request.getRequest().getName();
        if (name == null) {
            throw new ElasticsearchException("no name given");
        }
        String path = request.getRequest().getPath();
        if (path == null) {
            throw new ElasticsearchException("no path given");
        }
        BytesReference ref = request.getRequest().getBytes();
        if (ref == null || ref.length() == 0) {
            throw new ElasticsearchException("no bytes in request");
        }
        File dir = new File(environment.pluginsFile(),
                DeployPlugin.NAME + File.separator + "plugins" + File.separator + name);
        try {
            if (!dir.mkdirs()) {
                logger.warn("unable to make directories: {}", dir.getAbsolutePath());
            }
            File target = new File(dir, new File(path).getName());
            logger.info("target is {}", target.getAbsolutePath());
            FileOutputStream out = new FileOutputStream(target);
            Streams.copy(ref.streamInput(), out);
            out.close();
            logger.info("received {} bytes", target.length());
            deployService.add(name, target.getAbsoluteFile());
            logger.info("{} deployed", name);
            response.setSuccess(true);
        } catch (Exception e) {
            throw new ElasticsearchException(e.getMessage(), e);
        }
        return response;
    }

    @Override
    protected boolean accumulateExceptions() {
        return true;
    }

}
