package gr.uoa.di.madgik.federation.search.aggregator.controller;

import gr.uoa.di.madgik.federation.search.aggregator.dto.NodeInfo;
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
                .map(n -> new NodeInfo(n.getPid(), n.getName(), n.getLogo(), n.getNodeEndpoint()))
                .toList());
    }

    @Operation(summary = "Get a Node by its PID from the Node Registry.")
    @GetMapping(path = "{prefix}/{suffix}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Node> get(@PathVariable(value = "prefix") String prefix,
                                    @PathVariable(value = "suffix") String suffix) {
        return new ResponseEntity<>(nodeResolver.fetchNodes().stream().filter(node -> node.getPid().equals(prefix + "/" + suffix)).findAny().orElseThrow(), HttpStatus.OK);
    }

}
