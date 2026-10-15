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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Service
public class NodeResolver {

    private static final Logger logger = LoggerFactory.getLogger(NodeResolver.class);

    private final NodeRegistryClient client;

    public NodeResolver(@Value("${node.registry.url}") String nodeRegistryUrl,
                        @Value("${node.registry.key}") String nodeRegistryKey,
                        @Value("${node.request.connect-timeout-ms:2000}") long connectTimeoutMs,
                        @Value("${node.request.read-timeout-ms:5000}") long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        this.client = HttpNodeRegistryClient.builder(URI.create(nodeRegistryUrl), nodeRegistryKey)
                .httpClient(httpClient)
                .requestTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }

    @Cacheable("nodes")
    public List<Node> fetchNodes() {
        List<Node> nodes = fetchNodesOrNull();
        return nodes == null ? List.of() : nodes;
    }

    /**
     * Bypasses the cache; returns {@code null} (instead of throwing) when the registry
     * is unreachable, so callers can tell "confirmed empty" apart from "fetch failed"
     * and decide whether to keep serving a previously cached list.
     */
    List<Node> fetchNodesOrNull() {
        try {
            List<Node> nodes = client.fetchNodes();
            return nodes == null ? List.of() : List.copyOf(nodes);
        } catch (Exception e) {
            logger.warn("Failed to fetch nodes from node registry", e);
            return null;
        }
    }
}
