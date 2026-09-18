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

package gr.uoa.di.madgik.federation.search.aggregator.util;

import gr.uoa.di.madgik.registry.domain.HighlightedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Temporary utility for normalising resource payloads returned by federated API nodes
 * that wrap their response under a property named after the resource type
 * (e.g. {@code {"service": {...}}} instead of the resource object directly).
 *
 * <p>This class exists to paper over an API inconsistency and is expected to be removed
 * once the upstream APIs are aligned to return unwrapped payloads.
 * {@link #ENCLOSED_RESOURCE_TYPES} serves as a checklist of APIs that still need fixing.
 */
public class BundledResourceUnwrapper {

    private static final Logger logger = LoggerFactory.getLogger(BundledResourceUnwrapper.class);

    /**
     * Resource types whose API nodes are known to enclose the payload under a same-named property.
     */
    public static final Set<String> ENCLOSED_RESOURCE_TYPES = Set.of(
            "adapter",
            "catalogue",
            "datasource",
            "deployableApplication",
            "interoperabilityRecord",
            "organisation",
            "service",
            "trainingResource"
    );

    private BundledResourceUnwrapper() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> unwrapSingleIfEnclosed(Map<String, Object> result, String resourceType) {
        if (!ENCLOSED_RESOURCE_TYPES.contains(resourceType)) return result;
        Object nested = result.get(resourceType);
        if (nested instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return result;
    }

    /**
     * Tests the first result from an API node to detect whether the resource payload is enclosed
     * under a property named after the resource type (e.g. {@code {"service": {...}}}).
     * If enclosure is detected, all results in the list are unwrapped; otherwise the original
     * list is returned as-is. Only resource types listed in {@link #ENCLOSED_RESOURCE_TYPES}
     * are eligible for this check.
     */
    public static List<HighlightedResult<?>> unwrapIfEnclosed(List<HighlightedResult<?>> results, String resourceType, String url) {
        if (results.isEmpty() || !ENCLOSED_RESOURCE_TYPES.contains(resourceType)) return results;
        // Test only the first result; all items from the same API share the same structure.
        if (!isEnclosed(results.get(0), resourceType)) return results;
        logger.debug("Detected enclosed '{}' payload from {}, unwrapping", resourceType, url);
        return results.stream()
                .map(r -> unwrap(r, resourceType))
                .collect(Collectors.toList());
    }

    /**
     * Returns {@code true} if the result's payload is a {@link Map} that contains the
     * {@code resourceType} key mapped to another {@link Map}, indicating an enclosed structure.
     */
    private static boolean isEnclosed(HighlightedResult<?> result, String resourceType) {
        return result.getResult() instanceof Map<?, ?> map && map.get(resourceType) instanceof Map<?, ?>;
    }

    /**
     * Extracts the nested resource map from an enclosed {@link HighlightedResult}, preserving
     * the original score and highlights.
     */
    @SuppressWarnings("unchecked")
    private static HighlightedResult<?> unwrap(HighlightedResult<?> result, String resourceType) {
        Map<?, ?> map = (Map<?, ?>) result.getResult();
        return HighlightedResult.of(result.getScore(), map.get(resourceType), result.getHighlights());
    }
}
