package gr.uoa.di.madgik.federation.search.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;

public record NodeInfo(
        String pid,
        String name,
        URI logo,
        @JsonProperty("node_endpoint") URI nodeEndpoint
) {
}
