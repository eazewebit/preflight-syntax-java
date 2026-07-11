package com.neel.syntaxvalidation.binary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryResolverTest {

    private final BinaryResolver resolver = new BinaryResolver();

    @TempDir
    Path tempDir;

    @Test
    void resolve_usesPreferredPathWhenExecutable() throws IOException {
        Path fakeBinary = tempDir.resolve("custom-node");
        Files.writeString(fakeBinary, "#!/bin/sh");

        Optional<String> resolved = resolver.resolve(fakeBinary.toString(), "node");

        assertThat(resolved).contains(fakeBinary.toString());
    }

    @Test
    void resolve_ignoresBlankPreferredPath() {
        Optional<String> resolved = resolver.resolve("   ", "node");

        // Falls back to PATH; node should be present in this environment.
        assertThat(resolved).isPresent();
    }

    @Test
    void resolve_fallsBackToPathWhenPreferredMissing() {
        Optional<String> resolved = resolver.resolve(tempDir.resolve("does-not-exist").toString(), "node");

        assertThat(resolved).isPresent();
    }

    @Test
    void resolve_findsKnownBinaryOnPath() {
        // 'java' is guaranteed to be on the PATH because we are running on a JVM.
        Optional<String> resolved = resolver.resolve(null, "java");

        assertThat(resolved).isPresent();
        assertThat(Path.of(resolved.get())).exists();
    }

    @Test
    void resolve_returnsEmptyForUnknownBinary() {
        Optional<String> resolved = resolver.resolve(null, "this-binary-definitely-does-not-exist-12345");

        assertThat(resolved).isEmpty();
    }

    @Test
    void resolve_returnsEmptyForBlankBinaryName() {
        assertThat(resolver.resolve(null, "")).isEmpty();
        assertThat(resolver.resolve(null, "   ")).isEmpty();
    }
}
