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

import gr.uoa.di.madgik.node.registry.client.Node;
import gr.uoa.di.madgik.node.registry.client.NodeRegistryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NodeResolverTest {

    private NodeResolver resolver;
    private NodeRegistryClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new NodeResolver("http://dummy", "dummy-key");
        mockClient = mock(NodeRegistryClient.class);
        Field clientField = NodeResolver.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(resolver, mockClient);
    }

    @Test
    void nullReturnYieldsEmptyList() {
        when(mockClient.fetchNodes()).thenReturn(null);
        assertThat(resolver.fetchNodes()).isEmpty();
    }

    @Test
    void returnsUnmodifiableCopyOfClientList() {
        Node node = new Node();
        node.setId("node-1");
        when(mockClient.fetchNodes()).thenReturn(List.of(node));

        List<Node> result = resolver.fetchNodes();

        assertThat(result).containsExactly(node);
        assertThatThrownBy(() -> result.add(new Node()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
