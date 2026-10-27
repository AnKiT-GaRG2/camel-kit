package org.apache.camel.kit.service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Request for validating a generated Camel-Kit workspace.
 */
public record DoctorRequest(Path projectDir) {

    public DoctorRequest {
        Objects.requireNonNull(projectDir, "projectDir");
    }
}
