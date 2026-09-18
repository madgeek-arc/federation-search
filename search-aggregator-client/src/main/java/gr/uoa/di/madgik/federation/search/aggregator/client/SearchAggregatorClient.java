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
import gr.uoa.di.madgik.registry.domain.ScoredResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin, typed HTTP client for the federation search aggregator's REST API (see
 * {@code FederationResourceController} in the search-aggregator app). {@code baseUrl} is expected
 * to already include the aggregator's {@code federation} path segment (e.g.
 * {@code https://host/api/federation}) - every method here builds its path relative to that root,
 * matching how the aggregator's controller is mounted.
 * <p>
 * Every method throws on a genuine transport/5xx failure - retry, circuit-breaker, and fail-open
 * policy is a caller concern, not this client's. A clean 404/no-match is reported as
 * {@link Optional#empty()} or an empty list, never an exception.
 */
public class SearchAggregatorClient {

    private final RestClient restClient;

    public SearchAggregatorClient(String baseUrl, Duration timeout) {
        this(RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(timeout))
                .build());
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    SearchAggregatorClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Every resource of {@code collection} across the federation, as a de-duplicated,
     * name-sorted {@code {id, name}} list. {@code query} is an optional free-text filter.
     */
    public List<ResourceIdName> listResourceIds(String collection, String query) {
        List<ResourceIdName> body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/{collection}/ids")
                        .queryParamIfPresent("query", Optional.ofNullable(query))
                        .build(collection))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ResourceIdName>>() {
                });
        return body != null ? body : Collections.emptyList();
    }

    /**
     * A page of {@code collection} across the federation, merged and Reciprocal-Rank-Fusion
     * scored, matching one of {@code AggregationController}'s {@code getAllPublic*} endpoints
     * (e.g. {@code collection = "services"} for {@code /federation/services}). {@code queryParams}
     * is forwarded as-is (paging, sort, facet-filter and any other browse params the aggregator
     * accepts). {@code Page.getMetadata()} carries the contributing nodes for this page (keyed
     * {@code "nodes"}) - the same {@code {"nodes": [...]}} map the aggregator puts on the wire.
     */
    public Page<AggregatedResult> browse(String collection, MultiValueMap<String, String> queryParams) {
        Page<AggregatedResult> body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/{collection}")
                        .queryParams(queryParams)
                        .build(collection))
                .retrieve()
                .body(new ParameterizedTypeReference<Page<AggregatedResult>>() {
                });
        return body != null ? body : new Page<>();
    }

    /**
     * A single resource by id, from whichever node owns it. Also serves as the "does this id
     * exist anywhere in the federation" check - the aggregator has no separate exists route.
     */
    public Optional<Map<String, Object>> getById(String collection, String prefix, String suffix) {
        return getMap("/{collection}/{prefix}/{suffix}", collection, prefix, suffix);
    }

    /**
     * A single Configuration Template by id, from whichever node owns it.
     */
    public Optional<Map<String, Object>> getConfigurationTemplate(String prefix, String suffix) {
        return getMap("/configurationTemplates/{prefix}/{suffix}", prefix, suffix);
    }

    /**
     * The dynamic-form Model bound to a Configuration Template, from whichever node owns it.
     */
    public Optional<Map<String, Object>> getConfigurationTemplateModel(String prefix, String suffix) {
        return getMap("/configurationTemplates/{prefix}/{suffix}/model", prefix, suffix);
    }

    /**
     * Every Configuration Template of an Interoperability Record, from whichever node owns it.
     * Unwraps the aggregator's {@code Paging} envelope.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getConfigurationTemplatesByInteroperabilityRecordId(String prefix, String suffix) {
        Optional<Map<String, Object>> body = getMap(
                "/configurationTemplates/getAllByInteroperabilityRecordId/{prefix}/{suffix}", prefix, suffix);
        if (body.isEmpty()) {
            return Collections.emptyList();
        }
        Object results = body.get().get("results");
        return results instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : Collections.emptyList();
    }

    /**
     * Resources across the federation similar to the given candidate {@code resource}.
     */
    public List<ScoredResult<Map<String, Object>>> findSimilar(String collection, Map<String, Object> resource,
                                                               float threshold, int quantity) {
        List<ScoredResult<Map<String, Object>>> body = restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/{collection}/similar")
                        .queryParam("threshold", threshold)
                        .queryParam("quantity", quantity)
                        .build(collection))
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ScoredResult<Map<String, Object>>>>() {
                });
        return body != null ? body : Collections.emptyList();
    }

    private Optional<Map<String, Object>> getMap(String pathTemplate, Object... pathVars) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(pathTemplate, pathVars)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return Optional.ofNullable(body);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }
}
