package gr.uoa.di.madgik.federation.search.aggregator;

import com.github.tomakehurst.wiremock.WireMockServer;
import gr.uoa.di.madgik.federation.search.aggregator.service.NodeEndpointService;
import gr.uoa.di.madgik.federation.search.aggregator.service.NodeResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FederationSearchIntegrationTest {

    static WireMockServer wireMock = new WireMockServer(options().dynamicPort());

    @MockitoBean NodeEndpointService nodeEndpointService;
    @MockitoBean NodeResolver nodeResolver;

    @LocalServerPort int port;
    RestClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        client = RestClient.create("http://localhost:" + port);
        when(nodeResolver.fetchNodes()).thenReturn(List.of());
    }

    @Test
    void mergesResultsFromTwoNodes() {
        String nodeA = "http://localhost:" + wireMock.port() + "/node-a";
        String nodeB = "http://localhost:" + wireMock.port() + "/node-b";
        when(nodeEndpointService.getResourceCatalogueEndpoints()).thenReturn(List.of(nodeA, nodeB));

        stubNode("/node-a/public/service/search", 1,
                """
                [{"score":1.5,"result":{"id":"svc-a","name":"Alpha"},"highlights":[]}]
                """);
        stubNode("/node-b/public/service/search", 1,
                """
                [{"score":1.0,"result":{"id":"svc-b","name":"Beta"},"highlights":[]}]
                """);

        Map<?, ?> body = client.get().uri("/federation/services").retrieve().body(Map.class);

        assertThat(body).isNotNull();
        assertThat((Integer) body.get("total")).isEqualTo(2);
        assertThat((List<?>) body.get("results")).hasSize(2);
    }

    @Test
    void rrfPromotesResultFoundInMultipleNodes() {
        // node-a and node-c both return svc-a; node-c also returns a unique svc-c.
        // RRF should accumulate rank scores across nodes, so svc-a (rank-1 in two nodes)
        // must outrank svc-b and svc-c (each rank-1 in only one node).
        String nodeA = "http://localhost:" + wireMock.port() + "/node-a";
        String nodeB = "http://localhost:" + wireMock.port() + "/node-b";
        String nodeC = "http://localhost:" + wireMock.port() + "/node-c";
        when(nodeEndpointService.getResourceCatalogueEndpoints()).thenReturn(List.of(nodeA, nodeB, nodeC));

        stubNode("/node-a/public/service/search", 1,
                """
                [{"score":2.0,"result":{"id":"svc-a","name":"Alpha"},"highlights":[]}]
                """);
        stubNode("/node-b/public/service/search", 1,
                """
                [{"score":2.0,"result":{"id":"svc-b","name":"Beta"},"highlights":[]}]
                """);
        stubNode("/node-c/public/service/search", 2,
                """
                [{"score":1.5,"result":{"id":"svc-a","name":"Alpha"},"highlights":[]},
                 {"score":1.0,"result":{"id":"svc-c","name":"Gamma"},"highlights":[]}]
                """);

        Map<?, ?> body = client.get().uri("/federation/services").retrieve().body(Map.class);

        assertThat(body).isNotNull();
        // total = sum of each node's reported count (not deduplicated)
        assertThat((Integer) body.get("total")).isEqualTo(4);
        List<?> results = (List<?>) body.get("results");
        // svc-a is deduplicated → 3 unique results
        assertThat(results).hasSize(3);
        // svc-a ranked first because it accumulated RRF score from two nodes
        // each element is AggregatedResult: {score, result:{id,...}, highlights, originalScore}
        @SuppressWarnings("unchecked")
        Map<String, Object> topResult = (Map<String, Object>) ((Map<?, ?>) results.get(0)).get("result");
        assertThat(topResult).containsEntry("id", "svc-a");
    }

    @Test
    void toleratesUnreachableNode() {
        String nodeA = "http://localhost:" + wireMock.port() + "/node-a";
        String nodeB = "http://localhost:" + wireMock.port() + "/node-b";
        when(nodeEndpointService.getResourceCatalogueEndpoints()).thenReturn(List.of(nodeA, nodeB));

        stubNode("/node-a/public/service/search", 1,
                """
                [{"score":1.5,"result":{"id":"svc-a","name":"Alpha"},"highlights":[]}]
                """);
        wireMock.stubFor(get(urlPathEqualTo("/node-b/public/service/search"))
                .willReturn(aResponse().withStatus(503)));

        Map<?, ?> body = client.get().uri("/federation/services").retrieve().body(Map.class);

        assertThat(body).isNotNull();
        assertThat((Integer) body.get("total")).isEqualTo(1);
        assertThat((List<?>) body.get("results")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listResourceIds_projectsDedupsAndSortsAcrossNodes() {
        String nodeA = "http://localhost:" + wireMock.port() + "/node-a";
        String nodeB = "http://localhost:" + wireMock.port() + "/node-b";
        when(nodeEndpointService.getResourceCatalogueEndpoints()).thenReturn(List.of(nodeA, nodeB));

        // Single fan-out round (no quantity=0 metadata phase); svc-a is returned by both nodes.
        wireMock.stubFor(get(urlPathEqualTo("/node-a/public/service/search"))
                .willReturn(ok("""
                        {"total":2,"from":0,"to":2,"results":[
                          {"score":1.0,"result":{"id":"svc-z","name":"Zeta"},"highlights":[]},
                          {"score":1.0,"result":{"id":"svc-a","name":"Alpha"},"highlights":[]}],"facets":[]}
                        """).withHeader("Content-Type", "application/json")));
        wireMock.stubFor(get(urlPathEqualTo("/node-b/public/service/search"))
                .willReturn(ok("""
                        {"total":2,"from":0,"to":2,"results":[
                          {"score":1.0,"result":{"id":"svc-a","name":"Alpha"},"highlights":[]},
                          {"score":1.0,"result":{"id":"svc-m","name":"Mu"},"highlights":[]}],"facets":[]}
                        """).withHeader("Content-Type", "application/json")));

        List<Map<String, Object>> body = client.get().uri("/federation/services/ids")
                .retrieve().body(List.class);

        assertThat(body).isNotNull();
        assertThat(body).extracting(m -> m.get("id")).containsExactly("svc-a", "svc-m", "svc-z");
        assertThat(body).extracting(m -> m.get("name")).containsExactly("Alpha", "Mu", "Zeta");
    }

    private void stubNode(String path, int total, String resultsJson) {
        wireMock.stubFor(get(urlPathEqualTo(path))
                .withQueryParam("quantity", equalTo("0"))
                .willReturn(ok("""
                        {"total":%d,"from":0,"to":0,"results":[],"facets":[]}
                        """.formatted(total))
                        .withHeader("Content-Type", "application/json")));

        wireMock.stubFor(get(urlPathEqualTo(path))
                .withQueryParam("quantity", matching("[1-9][0-9]*"))
                .willReturn(ok("""
                        {"total":%d,"from":0,"to":%d,"results":%s,"facets":[]}
                        """.formatted(total, total, resultsJson))
                        .withHeader("Content-Type", "application/json")));
    }
}
