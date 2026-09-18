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

import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NodeCacheRefresher {

    private static final System.Logger LOGGER = System.getLogger(NodeCacheRefresher.class.getName());

    private final NodeResolver nodeResolver;
    private final CacheManager cacheManager;

    public NodeCacheRefresher(NodeResolver nodeResolver, CacheManager cacheManager) {
        this.nodeResolver = nodeResolver;
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedRateString = "#{${node.cache.refresh-rate-minutes:5} * 60000}")
    public void refresh() {
        LOGGER.log(System.Logger.Level.INFO, "Refreshing nodes cache");
        var cache = cacheManager.getCache("nodes");
        if (cache != null) {
            cache.clear();
        }
        nodeResolver.fetchNodes();
    }
}
