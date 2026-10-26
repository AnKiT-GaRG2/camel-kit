package org.apache.camel.kit.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonaResourceInstallerTest {

    @Test
    void derivesMarkdownDirectoryWithCanonicalSeparators() {
        assertEquals(
                ".opencode/camel-kit-personas",
                PersonaResourceInstaller.parentTarget(
                        ".opencode/camel-kit-personas/catalog-researcher.md"));
    }
}
