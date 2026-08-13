package com.neel.syntaxvalidation.binary.manager;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable status report describing the current availability of an external
 * binary dependency.
 *
 * <p>A {@code BinaryStatus} is produced by {@link BinaryManager#getStatus(BinaryInfo)}
 * and captures:
 * <ul>
 *   <li>whether the binary is currently available on the system;</li>
 *   <li>the resolved path to the executable, if found;</li>
 *   <li>the detected version string;</li>
 *   <li>whether the detected version meets the minimum requirement;</li>
 *   <li>an optional diagnostic message (e.g. explaining why detection failed).</li>
 * </ul>
 *
 * @see BinaryManager#getStatus(BinaryInfo)
 * @see BinaryManager#getAllStatuses()
 */
public final class BinaryStatus {

    private final BinaryInfo binaryInfo;
    private final boolean available;
    private final Path resolvedPath;
    private final String detectedVersion;
    private final boolean versionSatisfied;
    private final String diagnosticMessage;

    private BinaryStatus(Builder builder) {
        this.binaryInfo = Objects.requireNonNull(builder.binaryInfo, "binaryInfo");
        this.available = builder.available;
        this.resolvedPath = builder.resolvedPath;
        this.detectedVersion = builder.detectedVersion;
        this.versionSatisfied = builder.versionSatisfied;
        this.diagnosticMessage = builder.diagnosticMessage;
    }

    // ----------------------------------------------------------------
    //  Accessors
    // ----------------------------------------------------------------

    /** The binary this status pertains to. */
    public BinaryInfo getBinaryInfo() { return binaryInfo; }

    /** Whether the binary was found on the system. */
    public boolean isAvailable() { return available; }

    /** The resolved path to the executable, or empty if not found. */
    public Optional<Path> getResolvedPath() { return Optional.ofNullable(resolvedPath); }

    /** The detected version string, or empty if unavailable. */
    public Optional<String> getDetectedVersion() { return Optional.ofNullable(detectedVersion); }

    /**
     * Whether the detected version meets the binary's minimum requirement.
     * Returns {@code false} if no version was detected or if the binary is unavailable.
     */
    public boolean isVersionSatisfied() { return versionSatisfied; }

    /** An optional diagnostic message (error details, installation hints, etc.). */
    public Optional<String> getDiagnosticMessage() { return Optional.ofNullable(diagnosticMessage); }

    /**
     * Returns a human-readable, multi-line summary of this status.
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  %-12s : %s%n", "Binary", binaryInfo.getId()));
        sb.append(String.format("  %-12s : %s%n", "Status", available ? "AVAILABLE" : "NOT FOUND"));

        if (resolvedPath != null) {
            sb.append(String.format("  %-12s : %s%n", "Path", resolvedPath));
        }
        if (detectedVersion != null) {
            sb.append(String.format("  %-12s : %s", "Version", detectedVersion));
            if (binaryInfo.getMinimumVersion().isPresent()) {
                sb.append(versionSatisfied ? " ✓" : " (min: " + binaryInfo.getMinimumVersion().get() + " ✗)");
            }
            sb.append('\n');
        }
        if (diagnosticMessage != null) {
            sb.append(String.format("  %-12s : %s%n", "Diagnostic", diagnosticMessage));
        }

        String[] langs = binaryInfo.getEnabledLanguages();
        if (langs.length > 0) {
            sb.append(String.format("  %-12s : %s%n", "Languages", String.join(", ", langs)));
        }
        sb.append(String.format("  %-12s : %s%n", "NPM", binaryInfo.isNpmPackage() ? "yes" : "no"));

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("BinaryStatus{%s, available=%s, version=%s}",
                binaryInfo.getId(), available, detectedVersion);
    }

    // ----------------------------------------------------------------
    //  Builder
    // ----------------------------------------------------------------

    static Builder builder(BinaryInfo info) {
        return new Builder(info);
    }

    static final class Builder {
        private final BinaryInfo binaryInfo;
        private boolean available;
        private Path resolvedPath;
        private String detectedVersion;
        private boolean versionSatisfied;
        private String diagnosticMessage;

        Builder(BinaryInfo binaryInfo) {
            this.binaryInfo = binaryInfo;
        }

        Builder available(boolean v) { this.available = v; return this; }
        Builder resolvedPath(Path v) { this.resolvedPath = v; return this; }
        Builder detectedVersion(String v) { this.detectedVersion = v; return this; }
        Builder versionSatisfied(boolean v) { this.versionSatisfied = v; return this; }
        Builder diagnosticMessage(String v) { this.diagnosticMessage = v; return this; }

        BinaryStatus build() { return new BinaryStatus(this); }
    }
}
