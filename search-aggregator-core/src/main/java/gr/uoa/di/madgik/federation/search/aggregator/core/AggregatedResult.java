package gr.uoa.di.madgik.federation.search.aggregator.core;

import gr.uoa.di.madgik.registry.domain.Highlight;

import java.util.List;
import java.util.Map;

public record AggregatedResult(
        double score,
        Map<String, Object> result,
        List<Highlight> highlights,
        double originalScore
) {
}
