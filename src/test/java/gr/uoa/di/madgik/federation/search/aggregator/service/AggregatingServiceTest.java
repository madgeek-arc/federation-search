package gr.uoa.di.madgik.federation.search.aggregator.service;

import gr.uoa.di.madgik.federation.search.aggregator.dto.Page;
import gr.uoa.di.madgik.federation.search.aggregator.dto.AggregatedResult;
import gr.uoa.di.madgik.registry.domain.FacetFilter;
import gr.uoa.di.madgik.registry.domain.HighlightedResult;
import gr.uoa.di.madgik.registry.domain.Paging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AggregatingServiceTest {

    private AggregatingService aggregatingService;
    private RestClient restClient;
    private NodeEndpointService nodeEndpointService;
    private NodeResolver nodeResolver;
    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        nodeEndpointService = mock(NodeEndpointService.class);
        nodeResolver = mock(NodeResolver.class);
        
        ScoringService.RrfProperties properties = new ScoringService.RrfProperties();
        properties.setK(20);
        scoringService = new ScoringService(properties);
        
        aggregatingService = new AggregatingService(restClient, nodeEndpointService, nodeResolver, scoringService);
    }

    @Test
    void testRRFMerging() {
        // Prepare endpoints
        when(nodeEndpointService.getResourceCatalogueEndpoints()).thenReturn(List.of("node1", "node2"));
        when(nodeResolver.fetchNodes()).thenReturn(Collections.emptyList());

        // Prepare Mock RestClient behavior
        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.accept(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        // Node 1 results
        HighlightedResult<?> resA1 = mock(HighlightedResult.class);
        when(resA1.getScore()).thenReturn(10.0f);
        when(resA1.getResult()).thenReturn(new HashMap<>(Map.of("id", "A1")));

        HighlightedResult<?> resA2 = mock(HighlightedResult.class);
        when(resA2.getScore()).thenReturn(9.0f);
        when(resA2.getResult()).thenReturn(new HashMap<>(Map.of("id", "A2")));

        Paging<HighlightedResult<?>> paging1 = new Paging<>(2, 0, 2, List.of(resA1, resA2), Collections.emptyList());

        // Node 2 results
        HighlightedResult<?> resB1 = mock(HighlightedResult.class);
        when(resB1.getScore()).thenReturn(10.0f);
        when(resB1.getResult()).thenReturn(new HashMap<>(Map.of("id", "B1")));

        HighlightedResult<?> resA1_again = mock(HighlightedResult.class);
        when(resA1_again.getScore()).thenReturn(8.0f);
        when(resA1_again.getResult()).thenReturn(new HashMap<>(Map.of("id", "A1")));

        Paging<HighlightedResult<?>> paging2 = new Paging<>(2, 0, 2, List.of(resB1, resA1_again), Collections.emptyList());

        // First call is for metadata, returns size 2
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(paging1) // node1 metadata
                .thenReturn(paging2) // node2 metadata
                .thenReturn(paging1) // node1 data
                .thenReturn(paging2); // node2 data

        FacetFilter ff = new FacetFilter();
        ff.setFrom(0);
        ff.setQuantity(10);

        Page<AggregatedResult> resultPage = aggregatingService.getMergedPagedResults(ff, "service");

        assertThat(resultPage.getTotal()).isEqualTo(4);
        List<AggregatedResult> results = resultPage.getResults();
        assertThat(results).hasSize(3); // A1, A2, B1

        // A1 should be first because it's in both nodes
        // Score A1: 1/(20+1) [from node1] + 1/(20+2) [from node2] = 1/21 + 1/22 ≈ 0.09307
        // Score B1: 1/(20+1) = 1/21 ≈ 0.04762
        // Score A2: 1/(20+2) = 1/22 ≈ 0.04545

        AggregatedResult first = results.get(0);
        assertThat(first.result().get("id")).isEqualTo("A1");
        assertThat(first.score()).isCloseTo(1.0 / 21.0 + 1.0 / 22.0, org.assertj.core.data.Offset.offset(0.00001));
        // originalScore is the score from the node where it was first found (node1: 10.0)
        assertThat(first.originalScore()).isEqualTo(10.0);

        AggregatedResult second = results.get(1);
        assertThat(second.result().get("id")).isEqualTo("B1");

        AggregatedResult third = results.get(2);
        assertThat(third.result().get("id")).isEqualTo("A2");
    }

    @Test
    void testSortByNestedField() {
        when(nodeEndpointService.getResourceCatalogueEndpoints()).thenReturn(List.of("node1"));
        when(nodeResolver.fetchNodes()).thenReturn(Collections.emptyList());

        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.accept(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        // Results with "name" nested under the resource type key "service"
        HighlightedResult<?> resZeta = mock(HighlightedResult.class);
        when(resZeta.getScore()).thenReturn(10.0f);
        when(resZeta.getResult()).thenReturn(new HashMap<>(Map.of("service", new HashMap<>(Map.of("id", "Z","name", "Zeta")))));

        HighlightedResult<?> resAlpha = mock(HighlightedResult.class);
        when(resAlpha.getScore()).thenReturn(9.0f);
        when(resAlpha.getResult()).thenReturn(new HashMap<>(Map.of("service", new HashMap<>(Map.of("id", "A","name", "Alpha")))));

        HighlightedResult<?> resMu = mock(HighlightedResult.class);
        when(resMu.getScore()).thenReturn(8.0f);
        when(resMu.getResult()).thenReturn(new HashMap<>(Map.of("service", new HashMap<>(Map.of("id", "M","name", "Mu")))));

        Paging<HighlightedResult<?>> paging = new Paging<>(3, 0, 3, List.of(resZeta, resAlpha, resMu), Collections.emptyList());

        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(paging) // metadata
                .thenReturn(paging); // data

        FacetFilter ff = new FacetFilter();
        ff.setFrom(0);
        ff.setQuantity(10);
        ff.addOrderBy("name", "asc");

        Page<AggregatedResult> resultPage = aggregatingService.getMergedPagedResults(ff, "service");

        List<AggregatedResult> results = resultPage.getResults();
        assertThat(results).hasSize(3);
        // Sorted by name ascending: Alpha, Mu, Zeta
        assertThat(results.get(0).result().get("id")).isEqualTo("A");
        assertThat(results.get(1).result().get("id")).isEqualTo("M");
        assertThat(results.get(2).result().get("id")).isEqualTo("Z");
    }
}
