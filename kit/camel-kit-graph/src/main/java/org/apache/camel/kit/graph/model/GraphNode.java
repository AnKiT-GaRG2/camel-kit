package org.apache.camel.kit.graph.model;

import java.util.Map;

public record GraphNode(
        String id,
        NodeType type,
        Map<String, String> properties) {
}
