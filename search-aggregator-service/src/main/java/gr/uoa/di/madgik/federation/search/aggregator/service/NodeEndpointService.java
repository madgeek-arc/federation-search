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

import gr.uoa.di.madgik.node.registry.client.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class NodeEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(NodeEndpointService.class);

    private final ObjectMapper objectMapper;
    private final boolean manualConfig;
    private final NodeResolver nodeResolver;

    public NodeEndpointService(ObjectMapper objectMapper,
                               @Value("${node.endpoints.manual-config}") boolean manualConfig,
                               NodeResolver nodeResolver) {

        this.objectMapper = objectMapper;
        this.manualConfig = manualConfig;
        this.nodeResolver = nodeResolver;
    }

    public List<String> getResourceCatalogueEndpoints() {
        if (manualConfig) {
            try {
                return loadFromJson(objectMapper);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load manual node-endpoints.json", e);
            }
        } else {
            return loadFromApi();
        }
    }


    private List<String> loadFromJson(ObjectMapper objectMapper) throws IOException {
        Resource resource = new ClassPathResource("node-endpoints.json");
        JsonNode jsonNode = objectMapper.readTree(resource.getInputStream());

        List<String> endpoints = new ArrayList<>();
        if (jsonNode.has("endpoints")) {
            for (JsonNode endpointNode : jsonNode.get("endpoints")) {
                endpoints.add(endpointNode.asString());
            }
        }
        return endpoints;
    }

    private List<String> loadFromApi() {
        List<Node> nodes = nodeResolver.fetchNodes();

        List<String> finalEndpoints = new ArrayList<>();
        if (nodes != null) {
            for (Node node : nodes) {
                if (node.getCapabilities() != null) {
                    node.getCapabilities().stream()
                            .filter(cap -> "Resource Catalogue".equalsIgnoreCase(cap.getCapabilityType()))
                            .filter(cap -> cap.getEndpoint() != null && isValid(cap.getVersion()))
                            .findFirst()
                            .ifPresent(cap -> finalEndpoints.add(cap.getEndpoint().toString()));
                }
            }
        }
        return finalEndpoints;
    }

    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty() && !value.equals("-");
    }
}
