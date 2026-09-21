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

package gr.uoa.di.madgik.federation.search.aggregator.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableScheduling
public class Config {

    @Value("${node.cache.ttl-minutes:10}")
    private int cacheTtlMinutes;

    @Value("${federation.resource-ids.cache-ttl-minutes:5}")
    private int resourceIdsCacheTtlMinutes;

    @Value("${federation.resource-by-id.cache-ttl-minutes:5}")
    private int resourceByIdCacheTtlMinutes;

    @Value("${node.request.connect-timeout-ms:2000}")
    private long connectTimeoutMs;

    @Value("${node.request.read-timeout-ms:5000}")
    private long readTimeoutMs;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOrigins("*").allowedMethods("*");
            }
        };
    }

    /*
     * Every AggregatingService call (search fan-out, id lookup, cross-federation similarity
     * check) fans out to every registered node in parallel and already treats a failed/timed-out
     * node as simply "unavailable" - it's excluded from the aggregate response, nothing more.
     * Without an explicit timeout here, a node that accepts the connection but never responds
     * doesn't fail that way: it hangs the calling thread indefinitely instead, stalling the whole
     * aggregated response (and, since the fan-out uses the JVM-wide common ForkJoinPool, tying up
     * a shared thread beyond just this one request). Read timeout is higher than connect timeout
     * because the search fan-out does two round-trips per node (metadata + data page) against a
     * full-text/highlighting search, which is heavier than a single id/similarity lookup.
     */
    @Bean
    public RestClient restClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /*
     * The caches change on different timescales and carry very different payloads, so each gets
     * its own Caffeine spec via registerCustomCache rather than sharing the manager-wide one:
     *  - "nodes"                  the registry node list
     *  - "federationResourceIds"  the per-resource-type {id,name} inventory behind dropdowns
     *  - "federationResourceById" single-resource lookups (incl. cached misses) used by a node's
     *                             cross-node reference validation
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes)));
        cacheManager.registerCustomCache("nodes",
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(cacheTtlMinutes)).build());
        cacheManager.registerCustomCache("federationResourceIds",
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(resourceIdsCacheTtlMinutes)).build());
        cacheManager.registerCustomCache("federationResourceById",
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(resourceByIdCacheTtlMinutes)).build());
        return cacheManager;
    }
}