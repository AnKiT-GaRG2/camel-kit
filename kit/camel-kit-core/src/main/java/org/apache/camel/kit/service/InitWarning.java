package org.apache.camel.kit.service;

import java.util.Objects;

/**
 * Non-fatal problem encountered while initializing a workspace.
 */
public record InitWarning(String message) {

    public InitWarning {
        Objects.requireNonNull(message, "message");
    }
}
