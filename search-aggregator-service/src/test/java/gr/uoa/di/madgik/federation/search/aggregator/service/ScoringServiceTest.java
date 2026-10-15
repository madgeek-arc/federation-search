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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        ScoringService.RrfProperties properties = new ScoringService.RrfProperties();
        properties.setK(20);
        scoringService = new ScoringService(properties);
    }

    @Test
    void testApplyRRF() {
        // Node 1: A1 (#1), A2 (#2)
        HighlightedResult<?> resA1 = mock(HighlightedResult.class);
        when(resA1.getScore()).thenReturn(10.0f);
        when(resA1.getResult()).thenReturn(new HashMap<>(Map.of("id", "A1")));

        HighlightedResult<?> resA2 = mock(HighlightedResult.class);
        when(resA2.getScore()).thenReturn(9.0f);
        when(resA2.getResult()).thenReturn(new HashMap<>(Map.of("id", "A2")));

        // Node 2: B1 (#1), A1 (#2)
        HighlightedResult<?> resB1 = mock(HighlightedResult.class);
        when(resB1.getScore()).thenReturn(10.0f);
        when(resB1.getResult()).thenReturn(new HashMap<>(Map.of("id", "B1")));

        HighlightedResult<?> resA1_again = mock(HighlightedResult.class);
        when(resA1_again.getScore()).thenReturn(8.0f);
        when(resA1_again.getResult()).thenReturn(new HashMap<>(Map.of("id", "A1")));

        Map<String, List<HighlightedResult<?>>> nodeResults = new HashMap<>();
        nodeResults.put("node1", List.of(resA1, resA2));
        nodeResults.put("node2", List.of(resB1, resA1_again));

        List<AggregatedResult> sortedResults = scoringService.applyRRF(nodeResults);

        assertThat(sortedResults).hasSize(3);

        // A1 should be first (sum of ranks)
        AggregatedResult first = sortedResults.get(0);
        assertThat(scoringService.extractId(first)).isEqualTo("A1");
        // A1 score: 1/(20+1) + 1/(20+2) = 1/21 + 1/22 ≈ 0.09307
        assertThat(first.score()).isCloseTo(1.0 / 21.0 + 1.0 / 22.0, org.assertj.core.data.Offset.offset(0.00001));

        // B1 is #1 in node2: 1/(20+1) = 1/21 ≈ 0.04762
        // A2 is #2 in node1: 1/(20+2) = 1/22 ≈ 0.04545
        // So B1 should be second.
        AggregatedResult second = sortedResults.get(1);
        assertThat(scoringService.extractId(second)).isEqualTo("B1");

        AggregatedResult third = sortedResults.get(2);
        assertThat(scoringService.extractId(third)).isEqualTo("A2");
    }

    @Test
    void testApplyRRF_usesScoreNotInputOrder() {
        // Node returns results alphabetically: A-item (score 5) first, B-item (score 9) second.
        // RRF rank must be derived from score, so B-item should be rank 0 and score higher.
        HighlightedResult<?> lowScoreFirst = mock(HighlightedResult.class);
        when(lowScoreFirst.getScore()).thenReturn(5.0f);
        when(lowScoreFirst.getResult()).thenReturn(new HashMap<>(Map.of("id", "A-item")));

        HighlightedResult<?> highScoreSecond = mock(HighlightedResult.class);
        when(highScoreSecond.getScore()).thenReturn(9.0f);
        when(highScoreSecond.getResult()).thenReturn(new HashMap<>(Map.of("id", "B-item")));

        Map<String, List<HighlightedResult<?>>> nodeResults = new HashMap<>();
        nodeResults.put("node1", List.of(lowScoreFirst, highScoreSecond));

        List<AggregatedResult> sortedResults = scoringService.applyRRF(nodeResults);

        // B-item has higher relevance score → rank 0 → higher RRF score
        assertThat(scoringService.extractId(sortedResults.get(0))).isEqualTo("B-item");
        assertThat(sortedResults.get(0).score()).isCloseTo(1.0 / 21.0, org.assertj.core.data.Offset.offset(0.00001));
        assertThat(scoringService.extractId(sortedResults.get(1))).isEqualTo("A-item");
        assertThat(sortedResults.get(1).score()).isCloseTo(1.0 / 22.0, org.assertj.core.data.Offset.offset(0.00001));
    }

    @Test
    void testTieBreaking() {
        // A1 (#1 in node1, score 10)
        HighlightedResult<?> resA1 = mock(HighlightedResult.class);
        when(resA1.getScore()).thenReturn(10.0f);
        when(resA1.getResult()).thenReturn(new HashMap<>(Map.of("id", "A1")));

        // B1 (#1 in node2, score 8)
        HighlightedResult<?> resB1 = mock(HighlightedResult.class);
        when(resB1.getScore()).thenReturn(8.0f);
        when(resB1.getResult()).thenReturn(new HashMap<>(Map.of("id", "B1")));

        Map<String, List<HighlightedResult<?>>> nodeResults = new HashMap<>();
        nodeResults.put("node1", List.of(resA1));
        nodeResults.put("node2", List.of(resB1));

        List<AggregatedResult> sortedResults = scoringService.applyRRF(nodeResults);

        // Both have RRF score 1/(20+1). A1 wins tie-break with 10.0 > 8.0
        assertThat(scoringService.extractId(sortedResults.get(0))).isEqualTo("A1");
        assertThat(scoringService.extractId(sortedResults.get(1))).isEqualTo("B1");
    }
}
