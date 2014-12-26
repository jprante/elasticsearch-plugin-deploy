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
package org.xbib.elasticsearch.rest.deploy;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.support.RestToXContentListener;
import org.xbib.elasticsearch.action.deploy.DeployAction;
import org.xbib.elasticsearch.action.deploy.DeployRequest;
import org.xbib.elasticsearch.action.deploy.DeployRequestBuilder;
import org.xbib.elasticsearch.action.deploy.DeployResponse;

import java.io.IOException;
import java.util.Map;

public class RestDeployAction extends BaseRestHandler {

    Client client;

    @Inject
    public RestDeployAction(Settings settings, Client client, RestController controller) {
        super(settings, controller, client);
        this.client = client;
        controller.registerHandler(RestRequest.Method.GET, "/_deploy", new Get());
        controller.registerHandler(RestRequest.Method.POST, "/_deploy", new Post());
    }

    @Override
    protected void handleRequest(RestRequest request, RestChannel channel, Client client) throws Exception {
        // unused
    }

    class Get implements RestHandler {
        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            try {
                DeployRequestBuilder deployRequestBuilder = new DeployRequestBuilder(client.admin().cluster())
                        .setRead(true);
                final DeployRequest deployRequest = deployRequestBuilder.request();
                client.admin().cluster().execute(DeployAction.INSTANCE, deployRequest,
                        new RestToXContentListener<DeployResponse>(channel));
            } catch (Throwable ex) {
                logger.error(ex.getMessage(), ex);
                try {
                    channel.sendResponse(new BytesRestResponse(channel, ex));
                } catch (IOException ex2) {
                    logger.error(ex2.getMessage(), ex2);
                    channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR));
                }
            }
        }
    }

    class Post implements RestHandler {
        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            try {
                Map<String, Object> body = XContentHelper.convertToMap(request.content(), false).v2();
                String name = body.containsKey("name") ? body.get("name").toString() : request.param("name");
                String path = body.containsKey("path") ? body.get("path").toString() : request.param("path");
                DeployRequestBuilder deployRequestBuilder = new DeployRequestBuilder(client.admin().cluster())
                        .setName(name)
                        .setPath(settings, path);
                final DeployRequest deployRequest = deployRequestBuilder.request();
                client.admin().cluster().execute(DeployAction.INSTANCE, deployRequest,
                        new RestToXContentListener<DeployResponse>(channel));
            } catch (Throwable ex) {
                logger.error(ex.getMessage(), ex);
                try {
                    channel.sendResponse(new BytesRestResponse(channel, ex));
                } catch (IOException ex2) {
                    logger.error(ex2.getMessage(), ex2);
                    channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR));
                }
            }
        }
    }
}
