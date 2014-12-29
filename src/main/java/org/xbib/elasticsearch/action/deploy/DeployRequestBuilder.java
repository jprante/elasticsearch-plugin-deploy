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

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.nodes.NodesOperationRequestBuilder;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;

public class DeployRequestBuilder extends NodesOperationRequestBuilder<DeployRequest, DeployResponse, DeployRequestBuilder> {

    public DeployRequestBuilder(ClusterAdminClient clusterClient) {
        super(clusterClient, new DeployRequest());
    }

    @Override
    protected void doExecute(ActionListener<DeployResponse> listener) {
        client.execute(DeployAction.INSTANCE, request, listener);
    }

    @Override
    public DeployRequest request() {
        return this.request;
    }

    public DeployRequestBuilder setName(String name) throws IOException {
        request.setName(name);
        return this;
    }

    public DeployRequestBuilder setPath(Settings settings, String path) throws IOException {
        request.setPath(settings, path);
        return this;
    }

    public DeployRequestBuilder setContent(BytesReference content) {
        request.setContent(content);
        return this;
    }

    public DeployRequestBuilder setContentType(String contentType) {
        request.setContentType(contentType);
        return this;
    }

    public DeployRequestBuilder setRead(boolean read) {
        request.setRead(read);
        return this;
    }
}
