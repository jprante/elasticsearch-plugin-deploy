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

import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.Map;

public class DeployNodeResponse extends BaseNodeResponse {

    private boolean success;

    private Map<String, Object> plugins;

    DeployNodeResponse() {
    }

    public DeployNodeResponse(DiscoveryNode node) {
        super(node);
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean getSuccess() {
        return success;
    }

    public void setPlugins(Map<String, Object> plugins) {
        this.plugins = plugins;
    }

    public Map<String, Object> getPlugins() {
        return plugins;
    }

    public static DeployNodeResponse readDeployNodeResponse(StreamInput in) throws IOException {
        DeployNodeResponse response = new DeployNodeResponse();
        response.readFrom(in);
        return response;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        success = in.readBoolean();
        plugins = in.readMap();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(success);
        out.writeMap(plugins);
    }
}
