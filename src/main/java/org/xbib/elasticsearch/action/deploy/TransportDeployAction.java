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
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Injector;
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
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TransportDeployAction extends TransportNodesAction<DeployRequest, DeployResponse, DeployNodeRequest, DeployNodeResponse> {

    private final Environment environment;

    private final Injector injector;

    @Inject
    public TransportDeployAction(Settings settings, ClusterName clusterName, ThreadPool threadPool,
                                 ClusterService clusterService, TransportService transportService,
                                 ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                 Environment environment,
                                 Injector injector) {
        super(settings, DeployAction.NAME, clusterName, threadPool, clusterService, transportService,
                actionFilters, indexNameExpressionResolver, DeployRequest.class, DeployNodeRequest.class,
                ThreadPool.Names.MANAGEMENT);
        this.environment = environment;
        this.injector = injector;
    }

    @Override
    protected DeployResponse newResponse(DeployRequest request, AtomicReferenceArray nodesResponses) {
        final List<DeployNodeResponse> nodes = new ArrayList<>();
        for (int i = 0; i < nodesResponses.length(); i++) {
            Object resp = nodesResponses.get(i);
            if (resp instanceof DeployNodeResponse) {
                nodes.add((DeployNodeResponse) resp);
            }
        }
        return new DeployResponse(clusterName, nodes.toArray(new DeployNodeResponse[nodes.size()]));
    }

    @Override
    protected DeployNodeRequest newNodeRequest(String nodeId, DeployRequest request) {
        return new DeployNodeRequest(request, nodeId);
    }

    @Override
    protected DeployNodeResponse newNodeResponse() {
        return new DeployNodeResponse();
    }

    @Override
    protected DeployNodeResponse nodeOperation(DeployNodeRequest request) throws ElasticsearchException {
        DeployService deployService = injector.getInstance(DeployService.class);
        DeployNodeResponse response = new DeployNodeResponse(clusterService.localNode());
        if (request.getRequest().getRead()) {
            Map<String, Object> m = new HashMap<>();
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
            path = name;
        }
        // add .zip suffix if appropriate
        String contentType = request.getRequest().getContentType();
        if ("application/zip".equals(contentType) && !path.endsWith(".zip")) {
            path = path + ".zip";
        }
        BytesReference content = request.getRequest().getContent();
        if (content == null || content.length() == 0) {
            throw new ElasticsearchException("no content in request");
        }
        File dir = new File(environment.pluginsFile().toFile(),
                DeployPlugin.NAME + File.separator + "plugins" + File.separator + name);
        try {
            if (dir.exists()) {
                deleteFiles(dir.toPath());
            }
            if (!dir.mkdirs()) {
                logger.warn("unable to make directory: {}", dir.getAbsolutePath());
            }
            // clear directory from previous files
            File targetFile = new File(dir, new File(path).getName());
            logger.info("dir={} path={} target={}",
                    dir.getAbsolutePath(), path,
                    targetFile.getAbsolutePath());
            FileOutputStream out = new FileOutputStream(targetFile);
            Streams.copy(content.streamInput(), out);
            out.close();
            logger.info("received {} bytes", targetFile.length());
            deployService.add(name, targetFile.getAbsoluteFile());
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

    private void deleteFiles(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
