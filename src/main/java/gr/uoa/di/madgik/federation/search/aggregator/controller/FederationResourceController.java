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

package gr.uoa.di.madgik.federation.search.aggregator.controller;

import gr.uoa.di.madgik.federation.search.aggregator.dto.ResourceIdName;
import gr.uoa.di.madgik.federation.search.aggregator.service.AggregatingService;
import gr.uoa.di.madgik.registry.domain.ScoredResult;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "federation", produces = {MediaType.APPLICATION_JSON_VALUE})
public class FederationResourceController {

    private static final Map<String, String> COLLECTION_TO_RESOURCE_TYPE = Map.of(
            "adapters",                        "adapter",
            "catalogues",                      "catalogue",
            "configurationTemplateInstances",  "configurationTemplateInstance",
            "datasources",                     "datasource",
            "deployableApplications",          "deployableApplication",
            "interoperabilityRecords",         "interoperabilityRecord",
            "organisations",                   "organisation",
            "resourceInteroperabilityRecords", "resourceInteroperabilityRecord",
            "services",                        "service",
            "trainingResources",               "trainingResource"
    );

    // The resourceType each node's own generic-resource service registers internally
    // (matches every *Manager#getResourceTypeName() in resource-catalogue), needed for the
    // dedup/{resourceType}/check/local fan-out. This is distinct from
    // COLLECTION_TO_RESOURCE_TYPE above, which is the camelCase segment resource-catalogue's
    // public REST controllers are mapped under -- the two conventions coincide for
    // single-word types but diverge wherever a resource type name has more than one word.
    private static final Map<String, String> COLLECTION_TO_DEDUP_RESOURCE_TYPE = Map.of(
            "adapters",                        "adapter",
            "catalogues",                      "catalogue",
            "configurationTemplateInstances",  "configuration_template_instance",
            "datasources",                     "datasource",
            "deployableApplications",          "deployable_application",
            "interoperabilityRecords",         "interoperability_record",
            "organisations",                   "organisation",
            "resourceInteroperabilityRecords", "resource_interoperability_record",
            "services",                        "service",
            "trainingResources",               "training_resource"
    );

    private final AggregatingService aggregatingService;

    public FederationResourceController(AggregatingService aggregatingService) {
        this.aggregatingService = aggregatingService;
    }

    @Operation(summary = "List every resource of the given collection across the federation as "
            + "lightweight {id, name} pairs, for populating relational-field dropdowns. "
            + "De-duplicated and name-sorted; cached; optional free-text 'query' filter.")
    @GetMapping(path = "{collection}/ids")
    public ResponseEntity<List<ResourceIdName>> listResourceIds(@PathVariable String collection,
                                                               @RequestParam(required = false) String query) {
        String resourceType = COLLECTION_TO_RESOURCE_TYPE.get(collection);
        if (resourceType == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(aggregatingService.listResourceIdsAndNames(resourceType, query));
    }

    @Operation(summary = "Get a single resource by ID.")
    @GetMapping(path = "{collection}/{prefix}/{suffix}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String collection,
                                                       @PathVariable String prefix,
                                                       @PathVariable String suffix) {
        String resourceType = COLLECTION_TO_RESOURCE_TYPE.get(collection);
        if (resourceType == null) {
            return ResponseEntity.badRequest().build();
        }
        return aggregatingService.getResourceById(resourceType, prefix, suffix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get a single Configuration Template by id from whichever node owns it.")
    @GetMapping(path = "configurationTemplates/{prefix}/{suffix}")
    public ResponseEntity<Map<String, Object>> getConfigurationTemplate(@PathVariable String prefix,
                                                                        @PathVariable String suffix) {
        return aggregatingService.getConfigurationTemplateById(prefix, suffix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get all Configuration Templates of an Interoperability Record from whichever node owns it.")
    @GetMapping(path = "configurationTemplates/getAllByInteroperabilityRecordId/{prefix}/{suffix}")
    public ResponseEntity<Map<String, Object>> getConfigurationTemplatesByInteroperabilityRecordId(
            @PathVariable String prefix, @PathVariable String suffix) {
        return aggregatingService.getConfigurationTemplatesByInteroperabilityRecordId(prefix, suffix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get the Model bound to a Configuration Template from whichever node owns it.")
    @GetMapping(path = "configurationTemplates/{prefix}/{suffix}/model")
    public ResponseEntity<Map<String, Object>> getConfigurationTemplateModel(@PathVariable String prefix,
                                                                             @PathVariable String suffix) {
        return aggregatingService.getConfigurationTemplateModel(prefix, suffix)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Find resources across the federation similar to the given candidate resource.")
    @PostMapping(path = "{collection}/similar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ScoredResult<Map<String, Object>>>> findSimilar(@PathVariable String collection,
                                                           @RequestParam(required = false, defaultValue = "0.95") Float threshold,
                                                           @RequestParam(defaultValue = "5") int quantity,
                                                           @RequestBody Map<String, Object> resource) {
        String resourceType = COLLECTION_TO_DEDUP_RESOURCE_TYPE.get(collection);
        if (resourceType == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(aggregatingService.findSimilarAcrossFederation(resourceType, resource, threshold, quantity));
    }
}
