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

import org.elasticsearch.action.support.nodes.NodesOperationRequest;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.Streams;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class DeployRequest extends NodesOperationRequest<DeployRequest> {

    private final ESLogger logger = ESLoggerFactory.getLogger(DeployRequest.class.getSimpleName());

    private String name;

    private String path;

    private BytesReference ref;

    private boolean read;

    public DeployRequest() {
    }

    public DeployRequest(String... nodeIds) {
        super(nodeIds);
    }

    public DeployRequest setRead(boolean read) {
        this.read = read;
        return this;
    }

    public boolean getRead() {
        return read;
    }

    public DeployRequest setName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }

    public DeployRequest setPath(Settings settings, String path) throws IOException {
        this.path = path;
        if (path == null || path.isEmpty()) {
            throw new IOException("path not given");
        }
        InputStream in = null;
        // local access, always allowed
        File f = new File(path);
        if (f.exists() && f.canRead()) {
            in = new FileInputStream(f);
        } else {
            // try URL
            try {
                URL url = new URL(path);
                // caution: do not download from unconfigured domains
                String[] domains = settings.getAsArray("plugins.deploy.domains");
                boolean allowed = false;
                if (domains != null) {
                    for (String domain : domains) {
                        allowed = allowed || url.getHost().endsWith(domain);
                    }
                }
                if (allowed) {
                    in = url.openStream();
                }
            } catch (MalformedURLException e) {
                //
            }
            if (in == null) {
                throw new IOException("can't read from " + path);
            }
        }
        BytesStreamOutput out = new BytesStreamOutput();
        Streams.copy(in, out);
        in.close();
        out.close();
        this.ref = out.bytes();
        logger.debug("ref length = {}", ref.length());
        return this;
    }

    public String getPath() {
        return path;
    }

    public BytesReference getBytes() {
        return ref;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        this.read = in.readBoolean();
        this.name = in.readString();
        this.path = in.readString();
        this.ref = in.readBytesReference();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        if (name == null) {
            throw new IOException("no name was given for deploy request");
        }
        if (ref == null) {
            throw new IOException("no valid path was given for deploy request");
        }
        out.writeBoolean(read);
        out.writeString(name);
        out.writeString(path);
        out.writeBytesReference(ref);
    }

}
