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
