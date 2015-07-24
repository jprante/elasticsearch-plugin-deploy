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

import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

public class DeployResponse extends BaseNodesResponse<DeployNodeResponse> implements ToXContent {

    DeployResponse() {
    }

    public DeployResponse(ClusterName clusterName, DeployNodeResponse[] responses) {
        super(clusterName, responses);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        nodes = new DeployNodeResponse[in.readVInt()];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = DeployNodeResponse.readDeployNodeResponse(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(nodes.length);
        for (DeployNodeResponse node : nodes) {
            node.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        boolean b = true;
        if (getNodes() != null) {
            builder.startArray("nodes");
            for (DeployNodeResponse response : getNodes()) {
                if (response.getPlugins() != null) {
                    builder.startObject()
                            .field("name", response.getNode().getName())
                            .field("plugins")
                            .map(response.getPlugins())
                            .endObject();
                } else {
                    builder.startObject()
                            .field("name", response.getNode().getName())
                            .field("success", response.getSuccess())
                            .endObject();
                    b = b && response.getSuccess();
                }
            }
            builder.endArray();
        }
        builder.field("deployed", b);
        return builder;
    }
}
