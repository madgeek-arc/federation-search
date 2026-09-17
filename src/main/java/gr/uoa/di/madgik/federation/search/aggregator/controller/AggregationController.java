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

import gr.uoa.di.madgik.federation.search.aggregator.dto.AggregatedResult;
import gr.uoa.di.madgik.federation.search.aggregator.service.AggregatingService;
import gr.uoa.di.madgik.registry.annotation.BrowseParameters;
import gr.uoa.di.madgik.registry.domain.FacetFilter;
import gr.uoa.di.madgik.registry.domain.Paging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "federation", produces = {MediaType.APPLICATION_JSON_VALUE})
public class AggregationController {

    private final AggregatingService aggregatingService;

    public AggregationController(AggregatingService aggregatingService) {
        this.aggregatingService = aggregatingService;
    }

    @Operation(summary = "Get all Public Adapters from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "adapters")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicAdapters(@Parameter(hidden = true)
                                                               @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "adapter");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Catalogues from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "catalogues")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicCatalogues(@Parameter(hidden = true)
                                                                 @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "catalogue");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Configuration Templates from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "configurationTemplates")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicConfigurationTemplates(@Parameter(hidden = true)
                                                               @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "configurationTemplate");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Configuration Template Instances from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "configurationTemplateInstances")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicCTI(@Parameter(hidden = true)
                                                          @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "configurationTemplateInstance");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Datasources from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "datasources")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicDatasources(@Parameter(hidden = true)
                                                                  @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "datasource");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Deployable Applications from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "deployableApplications")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicDeployableApplications(@Parameter(hidden = true)
                                                                             @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "deployableApplication");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Interoperability Records from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "interoperabilityRecords")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicInteroperabilityRecords(@Parameter(hidden = true)
                                                                              @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "interoperabilityRecord");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Organisations from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "organisations")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicOrganisations(@Parameter(hidden = true)
                                                                    @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "organisation");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Resource Interoperability Records from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "resourceInteroperabilityRecords")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicResourceInteroperabilityRecord(@Parameter(hidden = true)
                                                                                     @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "resourceInteroperabilityRecord");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Services from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "services")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicServices(@Parameter(hidden = true)
                                                               @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "service");
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get all Public Training Resources from a list of predefined nodes.")
    @BrowseParameters
    @Parameter(name = "suspended", description = "Suspended",
            content = @Content(schema = @Schema(type = "boolean", defaultValue = "false")))
    @GetMapping(path = "trainingResources")
    public ResponseEntity<Paging<AggregatedResult>> getAllPublicTrainingResources(@Parameter(hidden = true)
                                                                        @RequestParam MultiValueMap<String, Object> allRequestParams) {
        FacetFilter ff = FacetFilter.from(allRequestParams);
        Paging<AggregatedResult> result = aggregatingService.getMergedPagedResults(ff, "trainingResource");
        return ResponseEntity.ok(result);
    }
}
