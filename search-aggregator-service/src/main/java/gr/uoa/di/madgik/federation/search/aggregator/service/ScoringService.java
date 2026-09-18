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

import gr.uoa.di.madgik.federation.search.aggregator.core.AggregatedResult;
import gr.uoa.di.madgik.registry.domain.HighlightedResult;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for ranking search results using Reciprocal Rank Fusion (RRF).
 */
@Service
public class ScoringService {

    private final RrfProperties rrfProperties;

    private static final String ID_FIELD = "id";
    private static final String PID_FIELD = "pid";

    public ScoringService(RrfProperties rrfProperties) {
        this.rrfProperties = rrfProperties;
    }

    public List<AggregatedResult> applyRRF(Map<String, List<HighlightedResult<?>>> nodeResults) {
        Map<String, Double> rrfScores = new ConcurrentHashMap<>();
        Map<String, HighlightedResult<?>> resultsById = new ConcurrentHashMap<>();

        // sequential stream is preferred for small result sets to avoid context-switching overhead
        nodeResults.values().forEach(results -> { // iterates one node at a time
            if (results == null) return;
            // Sort by relevance score descending so RRF rank reflects relevance, not request order
            List<HighlightedResult<?>> byRelevance = results.stream()
                    .filter(Objects::nonNull)
                    .sorted((r1, r2) -> Float.compare(r2.getScore(), r1.getScore()))
                    .toList();
            for (int rank = 0; rank < byRelevance.size(); rank++) { // ranks within that node only
                HighlightedResult<?> res = byRelevance.get(rank);
                String id = extractId(res);
                double rankScore = 1.0 / (rrfProperties.getK() + (rank + 1));
                rrfScores.merge(id, rankScore, Double::sum); // accumulates across nodes
                resultsById.merge(id, res, (oldRes, newRes) ->
                        newRes.getScore() > oldRes.getScore() ? newRes : oldRes);
            }
        });

        // Sort by RRF score with tie-breaking by original node score
        return resultsById.values().stream()
                .sorted((a, b) -> {
                    double rrfA = rrfScores.getOrDefault(extractId(a), 0.0);
                    double rrfB = rrfScores.getOrDefault(extractId(b), 0.0);
                    int cmp = Double.compare(rrfB, rrfA);
                    if (cmp == 0) {
                        return Double.compare(b.getScore(), a.getScore());
                    }
                    return cmp;
                })
                .map(res -> {
                    String id = extractId(res);
                    double finalScore = rrfScores.getOrDefault(id, 0.0);
                    double originalScore = res.getScore();
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (res.getResult() instanceof Map)
                            ? (Map<String, Object>) res.getResult()
                            : Collections.emptyMap();
                            
                    return new AggregatedResult(finalScore, resultMap, res.getHighlights(), originalScore);
                })
                .collect(Collectors.toList());
    }

    public String extractId(Object doc) {
        if (doc == null) {
            return "null";
        }
        Object result = null;
        if (doc instanceof AggregatedResult d) {
            result = d.result();
        } else if (doc instanceof HighlightedResult<?> hr) {
            result = hr.getResult();
        } else if (doc instanceof Map) {
            result = doc;
        }

        if (result instanceof Map<?, ?> map) {
            Object id = map.get(ID_FIELD);
            if (id == null) {
                id = map.get(PID_FIELD);
            }
            if (id != null) {
                return id.toString();
            }
        }
        // Fallback to identity hash if no stable ID is found.
        // Risk of collision is minimal given typical federated result sizes.
        return "hash:" + System.identityHashCode(doc);
    }

    @ConfigurationProperties(prefix = "scoring.rrf")
    public static class RrfProperties {
        /**
         * Smoothing constant 'k' for RRF calculation.
         * <p>
         * Low values (10-20) aggressively favor results at the very top of local lists.
         * Recommended when fetching few results (e.g. 10 per node).
         * <p>
         * High values (60+) dampen the advantage of being #1 vs #5, favoring "consensus"
         * (results appearing in many lists). Standard default is 60.
         */
        private int k = 20;

        public int getK() {
            return k;
        }

        public void setK(int k) {
            this.k = k;
        }
    }
}
