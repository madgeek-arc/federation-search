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

package gr.uoa.di.madgik.federation.search.aggregator.core;

import gr.uoa.di.madgik.registry.domain.Facet;
import gr.uoa.di.madgik.registry.domain.Paging;

import java.util.List;

public class Page <T> extends Paging<T> {

    Object metadata = null;

    public Page() {
        // no-arg constructor
    }

    public Page(int total, int from, int to, List<T> results, List<Facet> facets) {
        super(total, from, to, results, facets);
    }

    public Page(Paging<T> b, List<T> results, List<Facet> facets) {
        super(b.getTotal(), b.getFrom(), b.getTo(), results, facets);
    }

    public Page(Paging<T> b) {
        super(b.getTotal(), b.getFrom(), b.getTo(), b.getResults(), b.getFacets());
    }

    public Object getMetadata() {
        return metadata;
    }

    public void setMetadata(Object metadata) {
        this.metadata = metadata;
    }
}

