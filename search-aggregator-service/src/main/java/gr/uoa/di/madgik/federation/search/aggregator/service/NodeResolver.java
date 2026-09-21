/*
 * Copyright 2026 OpenAIRE AMKE & Athena Research and Innovation Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gr.uoa.di.madgik.federation.search.aggregator.service;

import gr.uoa.di.madgik.node.registry.client.HttpNodeRegistryClient;
import gr.uoa.di.madgik.node.registry.client.Node;
import gr.uoa.di.madgik.node.registry.client.NodeRegistryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class NodeResolver {

    private final NodeRegistryClient client;

    public NodeResolver(@Value("${node.registry.url}") String nodeRegistryUrl,
                        @Value("${node.registry.key}") String nodeRegistryKey) {
        this.client = new HttpNodeRegistryClient(URI.create(nodeRegistryUrl), nodeRegistryKey);
    }

    @Cacheable("nodes")
    public List<Node> fetchNodes() {
        List<Node> nodes = client.fetchNodes();
        return nodes == null ? List.of() : List.copyOf(nodes);
    }
}
