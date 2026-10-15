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

package gr.uoa.di.madgik.federation.search.aggregator.controller;

import gr.uoa.di.madgik.federation.search.aggregator.core.NodeInfo;
import gr.uoa.di.madgik.federation.search.aggregator.service.NodeResolver;
import gr.uoa.di.madgik.node.registry.client.Node;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "nodes", produces = {MediaType.APPLICATION_JSON_VALUE})
public class NodeController {

    private final NodeResolver nodeResolver;

    public NodeController(NodeResolver nodeResolver) {
        this.nodeResolver = nodeResolver;
    }

    @Operation(summary = "Get all Nodes from the Node Registry.")
    @GetMapping
    public ResponseEntity<List<NodeInfo>> getNodes() {
        return ResponseEntity.ok(nodeResolver.fetchNodes().stream()
                .map(NodeController::toNodeInfo)
                .toList());
    }

    @Operation(summary = "Get a Node by its PID from the Node Registry.")
    @GetMapping(path = "{prefix}/{suffix}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<NodeInfo> get(@PathVariable(value = "prefix") String prefix,
                                        @PathVariable(value = "suffix") String suffix) {
        return new ResponseEntity<>(nodeResolver.fetchNodes().stream()
                .filter(node -> node.getPid().equals(prefix + "/" + suffix))
                .findAny()
                .map(NodeController::toNodeInfo)
                .orElseThrow(), HttpStatus.OK);
    }

    private static NodeInfo toNodeInfo(Node node) {
        return new NodeInfo(node.getPid(), node.getName(), node.getLogo(), node.getNodeEndpoint());
    }

}
