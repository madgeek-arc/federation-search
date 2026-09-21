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

import gr.uoa.di.madgik.federation.search.aggregator.service.AggregatingService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    private final AggregatingService aggregatingService;

    public FederationResourceController(AggregatingService aggregatingService) {
        this.aggregatingService = aggregatingService;
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
}
