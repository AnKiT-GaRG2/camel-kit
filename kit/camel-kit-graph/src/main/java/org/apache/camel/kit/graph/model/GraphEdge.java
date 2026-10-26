package org.apache.camel.kit.graph.model;

import java.util.Map;

public record GraphEdge(
        String from,
        String to,
        EdgeType type,
        Map<String, String> properties) {
}
