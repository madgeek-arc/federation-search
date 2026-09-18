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

package gr.uoa.di.madgik.federation.search.aggregator.client;

import gr.uoa.di.madgik.federation.search.aggregator.core.AggregatedResult;
import gr.uoa.di.madgik.federation.search.aggregator.core.Page;
import gr.uoa.di.madgik.federation.search.aggregator.core.ResourceIdName;
import gr.uoa.di.madgik.registry.domain.Paging;
import gr.uoa.di.madgik.registry.domain.ScoredResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SearchAggregatorClientTest {

    private static final String BASE_URL = "https://aggregator.example.org/api/federation";

    private MockRestServiceServer mockServer;
    private SearchAggregatorClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new SearchAggregatorClient(builder.build());
    }

    @Test
    void listResourceIds_parsesBody() {
        mockServer.expect(requestTo(BASE_URL + "/services/ids?query=foo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"id\":\"21.T15/svc\",\"name\":\"A Service\"}]", MediaType.APPLICATION_JSON));

        List<ResourceIdName> result = client.listResourceIds("services", "foo");

        assertThat(result).containsExactly(new ResourceIdName("21.T15/svc", "A Service"));
    }

    @Test
    void browse_parsesPagedResultsAndMetadata() {
        mockServer.expect(requestTo(BASE_URL + "/services?suspended=false"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"total\":1,\"from\":0,\"to\":1,\"facets\":[],\"results\":["
                                + "{\"score\":0.9,\"result\":{\"id\":\"21.T15/svc\"},\"highlights\":[],\"originalScore\":0.9}"
                                + "],\"metadata\":{\"nodes\":[{\"pid\":\"21.T15999/node-a\"}]}}",
                        MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("suspended", "false");
        Page<AggregatedResult> result = client.browse("services", params);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getResults()).hasSize(1);
        assertThat(result.getResults().get(0).result().get("id")).isEqualTo("21.T15/svc");
        assertThat(result.getMetadata()).isInstanceOfSatisfying(Map.class, metadata ->
                assertThat(metadata).containsKey("nodes"));
    }

    @Test
    void getById_found_returnsBody() {
        mockServer.expect(requestTo(BASE_URL + "/services/21.T15/svc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"21.T15/svc\",\"name\":\"A Service\"}", MediaType.APPLICATION_JSON));

        Optional<Map<String, Object>> result = client.getById("services", "21.T15", "svc");

        assertThat(result).isPresent();
        assertThat(result.get().get("id")).isEqualTo("21.T15/svc");
    }

    @Test
    void getById_notFound_returnsEmpty() {
        mockServer.expect(requestTo(BASE_URL + "/services/21.T15/missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        Optional<Map<String, Object>> result = client.getById("services", "21.T15", "missing");

        assertThat(result).isEmpty();
    }

    @Test
    void getConfigurationTemplatesByInteroperabilityRecordId_parsesPagingEnvelope() {
        mockServer.expect(requestTo(BASE_URL + "/configurationTemplates?interoperability_record_id=21.T15/ir"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"total\":1,\"from\":0,\"to\":1,\"results\":[{\"id\":\"21.T15/ct\"}]}", MediaType.APPLICATION_JSON));

        Paging<Map<String, Object>> result =
                client.getConfigurationTemplatesByInteroperabilityRecordId("21.T15", "ir");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getResults()).hasSize(1);
        assertThat(result.getResults().get(0).get("id")).isEqualTo("21.T15/ct");
    }

    @Test
    void getConfigurationTemplatesByInteroperabilityRecordId_notFound_returnsEmptyPaging() {
        mockServer.expect(requestTo(BASE_URL + "/configurationTemplates?interoperability_record_id=21.T15/missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        Paging<Map<String, Object>> result =
                client.getConfigurationTemplatesByInteroperabilityRecordId("21.T15", "missing");

        assertThat(result.getTotal()).isZero();
        assertThat(result.getResults()).isEmpty();
    }

    @Test
    void findSimilar_parsesScoredResults() {
        mockServer.expect(requestTo(BASE_URL + "/services/similar?threshold=0.9&quantity=5"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "[{\"score\":0.97,\"result\":{\"id\":\"21.T15/svc\"}}]", MediaType.APPLICATION_JSON));

        List<ScoredResult<Map<String, Object>>> result =
                client.findSimilar("services", Map.of("name", "candidate"), 0.9f, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore()).isEqualTo(0.97f);
        assertThat(result.get(0).getResult().get("id")).isEqualTo("21.T15/svc");
    }
}
