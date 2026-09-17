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

import gr.uoa.di.madgik.federation.search.aggregator.dto.AggregatedResult;
import gr.uoa.di.madgik.federation.search.aggregator.dto.Page;
import gr.uoa.di.madgik.federation.search.aggregator.dto.ResourceIdName;
import gr.uoa.di.madgik.federation.search.aggregator.util.BundledResourceUnwrapper;
import gr.uoa.di.madgik.node.registry.client.Node;
import gr.uoa.di.madgik.registry.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AggregatingService {

    private static final Logger logger = LoggerFactory.getLogger(AggregatingService.class);

    private final RestClient restClient;
    private final NodeEndpointService nodeEndpointService;
    private final NodeResolver nodeResolver;
    private final ScoringService scoringService;

    /**
     * Upper bound on how many records the {@link #listResourceIdsAndNames} fan-out pulls from a
     * single node. A relational-field dropdown needs the whole federation-wide inventory, so this
     * is deliberately high; it exists only to stop a misbehaving node from streaming an unbounded
     * response into the aggregator.
     */
    @org.springframework.beans.factory.annotation.Value("${federation.resource-ids.max-per-node:10000}")
    private int resourceIdsMaxPerNode;

    public AggregatingService(RestClient restClient,
                              NodeEndpointService nodeEndpointService,
                              NodeResolver nodeResolver,
                              ScoringService scoringService) {
        this.restClient = restClient;
        this.nodeEndpointService = nodeEndpointService;
        this.nodeResolver = nodeResolver;
        this.scoringService = scoringService;
    }

    public Page<AggregatedResult> getMergedPagedResults(FacetFilter ff, String resourceType) {
        int from = ff.getFrom();
        int quantity = ff.getQuantity();
        int to = from + quantity;

        List<String> endpoints = nodeEndpointService.getResourceCatalogueEndpoints().stream()
                .map(base -> String.join("/", base, "public", resourceType, "search"))
                .toList();

        List<APIPageMetadata> apiMetadataList = Collections.synchronizedList(new ArrayList<>());
        List<Facet> allFacets = Collections.synchronizedList(new ArrayList<>());

        endpoints.parallelStream().forEach(endpoint -> {
            fetchPageMetadata(endpoint, ff).ifPresent(metadata -> {
                apiMetadataList.add(metadata);
                synchronized (allFacets) {
                    allFacets.addAll(metadata.facets);
                }
            });
        });

        int totalAvailable = apiMetadataList.stream()
                .mapToInt(metadata -> metadata.size)
                .sum();

        // Fetches top results from every node to compute globally meaningful scores.
        Map<String, List<HighlightedResult<?>>> nodeResults = new ConcurrentHashMap<>();
        apiMetadataList.parallelStream().forEach(meta -> {
            if (meta.size > 0) {
                String dataUrl = buildUrlWithFacetFilter(meta.url, ff, 0, to);
                fetchResultsPage(dataUrl).ifPresent(page -> {
                    List<HighlightedResult<?>> results = page.getResults();
                    if (results != null) {
                        nodeResults.put(meta.url, BundledResourceUnwrapper.unwrapIfEnclosed(results, resourceType, meta.url));
                    }
                });
            }
        });

        // rank results using ScoringService
        List<AggregatedResult> scoredResults = scoringService.applyRRF(nodeResults);

        // re-sort by user's requested order if specified; RRF scores are preserved
        List<AggregatedResult> sortedResults = ff.getOrderBy() != null && !ff.getOrderBy().isEmpty()
                ? applySortOrder(scoredResults, ff.getOrderBy(), resourceType)
                : scoredResults;

        // Slice
        int start = Math.min(from, sortedResults.size());
        int end = Math.min(to, sortedResults.size());
        List<AggregatedResult> finalResults = new ArrayList<>(sortedResults.subList(start, end));

        // merge facets
        List<Facet> mergedFacets = mergeFacets(allFacets);

        List<Node> nodes = nodeResolver.fetchNodes();

        return createPage(from, finalResults.size(), totalAvailable, finalResults, mergedFacets, nodes);
    }

    /**
     * Returns every resource of {@code resourceType} across the federation as a de-duplicated,
     * name-sorted list of {@code {id, name}} pairs - the minimum a node needs to populate a
     * relational-field dropdown that can reference resources living on other nodes.
     * <p>
     * Unlike {@link #getMergedPagedResults}, this does a single fan-out round (no metadata phase),
     * projects each node's hits down to id + name <em>before</em> merging so full payloads are
     * never held in aggregate, and skips Reciprocal Rank Fusion / facet merging entirely (a
     * picker wants a stable alphabetical list, not a relevance ranking). The result is cached
     * per {@code resourceType}/{@code query} so the fan-out runs once per TTL for the whole
     * federation rather than once per dropdown open.
     *
     * @param query optional free-text filter passed through to each node's search as a keyword;
     *              {@code null}/blank returns the full inventory.
     */
    @Cacheable(cacheNames = "federationResourceIds",
            key = "#resourceType + '|' + (#query == null ? '' : #query.trim())")
    public List<ResourceIdName> listResourceIdsAndNames(String resourceType, String query) {
        FacetFilter ff = new FacetFilter();
        ff.setFrom(0);
        ff.setQuantity(resourceIdsMaxPerNode);
        if (query != null && !query.isBlank()) {
            ff.setKeyword(query.trim());
        }

        List<String> endpoints = nodeEndpointService.getResourceCatalogueEndpoints().stream()
                .map(base -> String.join("/", base, "public", resourceType, "search"))
                .toList();

        List<ResourceIdName> all = endpoints.parallelStream()
                .flatMap(endpoint -> fetchIdNames(
                        buildUrlWithFacetFilter(endpoint, ff, 0, resourceIdsMaxPerNode), resourceType).stream())
                .toList();

        Map<String, ResourceIdName> byId = new LinkedHashMap<>();
        for (ResourceIdName r : all) {
            byId.putIfAbsent(r.id(), r);
        }
        return byId.values().stream()
                .sorted(Comparator.comparing(r -> r.name() == null ? "" : r.name(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ResourceIdName> fetchIdNames(String url, String resourceType) {
        return fetchPage(url, FetchPhase.DATA)
                .map(page -> {
                    List<HighlightedResult<?>> results = page.getResults();
                    if (results == null || results.isEmpty()) {
                        return Collections.<ResourceIdName>emptyList();
                    }
                    List<HighlightedResult<?>> unwrapped =
                            BundledResourceUnwrapper.unwrapIfEnclosed(results, resourceType, url);
                    List<ResourceIdName> out = new ArrayList<>(unwrapped.size());
                    for (HighlightedResult<?> r : unwrapped) {
                        if (r.getResult() instanceof Map<?, ?> map) {
                            Object id = map.get("id");
                            if (id == null) {
                                continue;
                            }
                            Object name = map.get("name");
                            out.add(new ResourceIdName(id.toString(), name != null ? name.toString() : id.toString()));
                        }
                    }
                    return out;
                })
                .orElseGet(Collections::emptyList);
    }

    /**
     * Resolves a single resource by id from whichever node owns it. Cached per
     * {@code resourceType/prefix/suffix} (misses included, as negative results) so repeated
     * lookups - notably the write-path existence checks a node runs when validating a
     * cross-node reference - do not re-fan-out to every node on every call.
     */
    @Cacheable(cacheNames = "federationResourceById",
            key = "#resourceType + '/' + #prefix + '/' + #suffix")
    public Optional<Map<String, Object>> getResourceById(String resourceType, String prefix, String suffix) {
        return nodeEndpointService.getResourceCatalogueEndpoints().parallelStream()
                .map(base -> String.join("/", base, "public", resourceType, prefix, suffix))
                .map(url -> {
                    try {
                        Map<String, Object> result = restClient.get()
                                .uri(url)
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .body(new ParameterizedTypeReference<>() {});
                        if (result == null) return Optional.<Map<String, Object>>empty();
                        return Optional.of(BundledResourceUnwrapper.unwrapSingleIfEnclosed(result, resourceType));
                    } catch (Exception e) {
                        logger.warn("Skipping unavailable node during id fetch: {} ({})", url, describeException(e));
                        logger.debug("Unavailable node details for {}", url, e);
                        return Optional.<Map<String, Object>>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Fetches a single Configuration Template by id from whichever node owns it, from that node's
     * public layer - a cross-node read only ever sees public layers, and the id that flows through
     * the federation is the template's public (bare) PID.
     */
    public Optional<Map<String, Object>> getConfigurationTemplateById(String prefix, String suffix) {
        return firstNonNullFromNodes(base ->
                String.join("/", base, "public", "configurationTemplate", prefix, suffix));
    }

    /**
     * Fetches the dynamic-form Model bound to a Configuration Template, from whichever node owns
     * the template. The Model itself is a node-local resource; the owning node serves it through
     * its {@code public/configurationTemplate/{prefix}/{suffix}/model} route, keyed by the
     * template's public PID.
     */
    public Optional<Map<String, Object>> getConfigurationTemplateModel(String prefix, String suffix) {
        return firstNonNullFromNodes(base ->
                String.join("/", base, "public", "configurationTemplate", prefix, suffix, "model"));
    }

    /**
     * Fetches all Configuration Templates of an Interoperability Record, from whichever node owns
     * it. Returns the raw {@code Paging} body of the first node whose {@code results} is non-empty.
     * <p>
     * Targets each node's {@code public/configurationTemplate/getAllByInteroperabilityRecordId}
     * route: a cross-node read only ever sees public layers, and the public layer keys the
     * template-to-record link by the Interoperability Record's public PID (the id form that flows
     * through the federation), whereas the private layer keys it by the record's node-local id.
     */
    public Optional<Map<String, Object>> getConfigurationTemplatesByInteroperabilityRecordId(String prefix, String suffix) {
        return nodeEndpointService.getResourceCatalogueEndpoints().parallelStream()
                .map(base -> String.join("/", base, "public", "configurationTemplate",
                        "getAllByInteroperabilityRecordId", prefix, suffix))
                .map(url -> fetchMap(url, "configuration template list fetch"))
                .filter(body -> body.isPresent() && hasNonEmptyResults(body.get()))
                .map(Optional::get)
                .findFirst();
    }

    private Optional<Map<String, Object>> firstNonNullFromNodes(java.util.function.Function<String, String> urlForBase) {
        return nodeEndpointService.getResourceCatalogueEndpoints().parallelStream()
                .map(urlForBase)
                .map(url -> fetchMap(url, "federated resource fetch"))
                .filter(body -> body.isPresent() && !body.get().isEmpty())
                .map(Optional::get)
                .findFirst();
    }

    private Optional<Map<String, Object>> fetchMap(String url, String opLabel) {
        try {
            Map<String, Object> result = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return Optional.ofNullable(result);
        } catch (Exception e) {
            logger.warn("Skipping unavailable node during {}: {} ({})", opLabel, url, describeException(e));
            logger.debug("Unavailable node details for {}", url, e);
            return Optional.empty();
        }
    }

    private boolean hasNonEmptyResults(Map<String, Object> pagingBody) {
        Object results = pagingBody.get("results");
        return results instanceof List<?> list && !list.isEmpty();
    }

    /**
     * Fans a candidate resource out, in parallel, to every node's embedding-based
     * {@code POST /dedup/{resourceType}/check/local}. Unlike {@link #getMergedPagedResults}, the
     * per-node scores here are already directly comparable (every node runs the same
     * cosine-similarity recommendation logic), so results are merged by a plain sort on score
     * rather than rank fusion.
     * <p>
     * This deliberately targets each node's <em>local-only</em> {@code check/local} route rather
     * than {@code check}: {@code check} itself calls back into this aggregator to get a
     * federation-wide view, so fanning out to it here would call back into every node's
     * {@code check}, which would call this aggregator again, recursing without bound.
     */
    public List<ScoredResult<Map<String, Object>>> findSimilarAcrossFederation(String resourceType, Map<String, Object> resource,
                                                                                Float threshold, int quantity) {
        List<String> endpoints = nodeEndpointService.getResourceCatalogueEndpoints().stream()
                .map(base -> String.join("/", base, "dedup", resourceType, "check", "local"))
                .toList();

        return endpoints.parallelStream()
                .flatMap(endpoint -> fetchSimilar(endpoint, resource, threshold, quantity).stream())
                .sorted((a, b) -> Float.compare(b.getScore(), a.getScore()))
                .limit(quantity)
                .toList();
    }

    private List<ScoredResult<Map<String, Object>>> fetchSimilar(String endpoint, Map<String, Object> resource,
                                                                   Float threshold, int quantity) {
        String url = UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("threshold", threshold)
                .queryParam("quantity", quantity)
                .toUriString();
        try {
            List<ScoredResult<Map<String, Object>>> results = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(resource)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ScoredResult<Map<String, Object>>>>() {
                    });
            return results != null ? results : List.of();
        } catch (Exception e) {
            logger.warn("Skipping unavailable node during similarity fetch: {} ({})", url, describeException(e));
            logger.debug("Unavailable node details for {}", url, e);
            return List.of();
        }
    }

    private Optional<APIPageMetadata> fetchPageMetadata(String endpoint, FacetFilter ff) {
        String url = buildUrlWithFacetFilter(endpoint, ff, 0, 0);

        return fetchPage(url, FetchPhase.METADATA)
                .map(page -> new APIPageMetadata(
                        endpoint,
                        page.getTotal(),
                        Optional.ofNullable(page.getFacets()).orElse(Collections.emptyList())
                ));
    }

    private Optional<Paging<HighlightedResult<?>>> fetchResultsPage(String url) {
        return fetchPage(url, FetchPhase.DATA);
    }

    private Optional<Paging<HighlightedResult<?>>> fetchPage(String url, FetchPhase phase) {
        try {
            Paging<HighlightedResult<?>> page = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            return Optional.ofNullable(page);
        } catch (Exception e) {
            logger.warn("Skipping unavailable node during {} fetch: {} ({})",
                    phase.logLabel, url, describeException(e));
            logger.debug("Unavailable node details for {}", url, e);
            return Optional.empty();
        }
    }

    private String describeException(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + message;
    }

    private String buildUrlWithFacetFilter(String url, FacetFilter ff, int from, int quantity) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url)
                .queryParam("from", from)
                .queryParam("quantity", quantity);

        if (ff.getKeyword() != null && !ff.getKeyword().isEmpty()) {
            builder.queryParam("keyword", ff.getKeyword());
        }

        if (ff.getOrderBy() != null && !ff.getOrderBy().isEmpty()) {
            for (Map.Entry<String, Object> entry : ff.getOrderBy().entrySet()) {
                String sortField = entry.getKey();
                String order = "asc";

                Object value = entry.getValue();

                if (value instanceof Map<?, ?> innerMap) {
                    Object orderVal = innerMap.get("order");
                    if (orderVal != null) {
                        order = orderVal.toString().toLowerCase();
                    }
                } else if (value instanceof String) {
                    order = ((String) value).toLowerCase();
                }

                builder.queryParam("sort", sortField);
                builder.queryParam("order", order);
                break; // only first entry used
            }
        }

        if (ff.getFilter() != null && !ff.getFilter().isEmpty()) {
            for (Map.Entry<String, Object> entry : ff.getFilter().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        builder.queryParam(entry.getKey(), item.toString());
                    }
                } else {
                    builder.queryParam(entry.getKey(), value.toString());
                }
            }
        }

        return builder.toUriString();
    }

    private List<Facet> mergeFacets(List<Facet> allFacets) {
        Map<String, Facet> mergedFacetMap = new LinkedHashMap<>();

        for (Facet facet : allFacets) {
            String field = facet.getField();
            String label = facet.getLabel();

            Facet existingFacet = mergedFacetMap.computeIfAbsent(field, f -> {
                Facet newFacet = new Facet();
                newFacet.setField(field);
                newFacet.setLabel(label);
                newFacet.setValues(new ArrayList<>());
                return newFacet;
            });

            // merge facet values
            Map<String, Value> existingValuesMap = existingFacet.getValues().stream()
                    .collect(Collectors.toMap(Value::getValue, fv -> fv, (a, b) -> a));

            for (Value incoming : facet.getValues()) {
                existingValuesMap.merge(
                        incoming.getValue(),
                        incoming,
                        (existing, inc) -> {
                            existing.setCount(existing.getCount() + inc.getCount());
                            return existing;
                        }
                );
            }

            existingFacet.setValues(new ArrayList<>(existingValuesMap.values()));
        }

        return new ArrayList<>(mergedFacetMap.values());
    }

    private Page<AggregatedResult> createPage(int from, int resultsSize, int total, List<AggregatedResult> results, List<Facet> facets, List<Node> nodes) {
        Page<AggregatedResult> page = new Page<>(total, from, from + resultsSize, results, facets);
        page.setMetadata(Map.of("nodes", nodes));
        return page;
    }

    private List<AggregatedResult> applySortOrder(List<AggregatedResult> results, Map<String, Object> orderBy, String resourceType) {
        Map.Entry<String, Object> entry = orderBy.entrySet().iterator().next();
        String sortField = entry.getKey();
        String order = "asc";
        Object value = entry.getValue();
        if (value instanceof Map<?, ?> innerMap) {
            Object orderVal = innerMap.get("order");
            if (orderVal != null) order = orderVal.toString().toLowerCase();
        } else if (value instanceof String s) {
            order = s.toLowerCase();
        }

        final boolean ascending = "asc".equals(order);
        return results.stream()
                .sorted((a, b) -> {
                    Object valA = resolveFieldValue(a.result(), sortField, resourceType);
                    Object valB = resolveFieldValue(b.result(), sortField, resourceType);
                    if (valA == null && valB == null) return 0;
                    if (valA == null) return ascending ? 1 : -1;
                    if (valB == null) return ascending ? -1 : 1;
                    int cmp;
                    if (valA instanceof Number numA && valB instanceof Number numB) {
                        cmp = Double.compare(numA.doubleValue(), numB.doubleValue());
                    } else {
                        cmp = valA.toString().compareToIgnoreCase(valB.toString());
                    }
                    return ascending ? cmp : -cmp;
                })
                .collect(Collectors.toList());
    }

    // Checks top-level first, then the nested resource-type object (e.g. result["service"]["name"])
    private Object resolveFieldValue(Map<String, Object> result, String field, String resourceType) {
        if (result == null) return null;
        Object val = getFieldCaseInsensitive(result, field);
        if (val != null) return val;
        Object nested = getFieldCaseInsensitive(result, resourceType);
        if (nested instanceof Map<?, ?> nestedMap) {
            return getFieldCaseInsensitive((Map<String, Object>) nestedMap, field);
        }
        return null;
    }

    private Object getFieldCaseInsensitive(Map<String, Object> map, String field) {
        if (map == null) return null;
        Object val = map.get(field);
        if (val != null) return val;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) return entry.getValue();
        }
        return null;
    }

    private static class APIPageMetadata {
        String url;
        int size;
        List<Facet> facets;

        APIPageMetadata(String url, int size, List<Facet> facets) {
            this.url = url;
            this.size = size;
            this.facets = facets;
        }
    }

    private enum FetchPhase {
        METADATA("metadata"),
        DATA("data");

        private final String logLabel;

        FetchPhase(String logLabel) {
            this.logLabel = logLabel;
        }
    }
}
