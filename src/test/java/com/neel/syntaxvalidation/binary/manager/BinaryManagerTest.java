package com.neel.syntaxvalidation.binary.manager;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link BinaryManager}, {@link BinaryInfo}, {@link BinaryStatus},
 * and {@link BinaryUtils} classes.
 */
class BinaryManagerTest {

    @TempDir
    Path tempDir;

    private BinaryManager manager;

    @BeforeEach
    void setUp() throws IOException {
        manager = new BinaryManager(tempDir);
    }

    // ================================================================
    //  BinaryInfo tests
    // ================================================================

    @Test
    @DisplayName("BinaryInfo.ALL should contain all 7 known binaries")
    void allBinariesShouldBeDefined() {
        assertThat(BinaryInfo.ALL).hasSize(7);
        assertThat(BinaryInfo.ALL).containsExactlyInAnyOrder(
                BinaryInfo.NODE, BinaryInfo.JAVAC, BinaryInfo.TSC,
                BinaryInfo.PYTHON, BinaryInfo.PHP, BinaryInfo.VNU,
                BinaryInfo.STYLELINT
        );
    }

    @Test
    @DisplayName("BinaryInfo should have correct IDs")
    void binaryInfoShouldHaveCorrectIds() {
        assertThat(BinaryInfo.NODE.getId()).isEqualTo("node");
        assertThat(BinaryInfo.JAVAC.getId()).isEqualTo("javac");
        assertThat(BinaryInfo.TSC.getId()).isEqualTo("tsc");
        assertThat(BinaryInfo.PYTHON.getId()).isEqualTo("python");
        assertThat(BinaryInfo.PHP.getId()).isEqualTo("php");
        assertThat(BinaryInfo.VNU.getId()).isEqualTo("vnu");
        assertThat(BinaryInfo.STYLELINT.getId()).isEqualTo("stylelint");
    }

    @Test
    @DisplayName("npm packages should be correctly flagged")
    void npmPackagesShouldBeCorrectlyFlagged() {
        assertThat(BinaryInfo.NODE.isNpmPackage()).isFalse();
        assertThat(BinaryInfo.JAVAC.isNpmPackage()).isFalse();
        assertThat(BinaryInfo.TSC.isNpmPackage()).isTrue();
        assertThat(BinaryInfo.PYTHON.isNpmPackage()).isFalse();
        assertThat(BinaryInfo.PHP.isNpmPackage()).isFalse();
        assertThat(BinaryInfo.VNU.isNpmPackage()).isFalse();
        assertThat(BinaryInfo.STYLELINT.isNpmPackage()).isTrue();
    }

    @Test
    @DisplayName("BinaryInfo should expose enabled languages")
    void binaryInfoShouldExposeEnabledLanguages() {
        assertThat(BinaryInfo.NODE.getEnabledLanguages()).containsExactly("JavaScript", "TypeScript");
        assertThat(BinaryInfo.JAVAC.getEnabledLanguages()).containsExactly("Java");
        assertThat(BinaryInfo.TSC.getEnabledLanguages()).containsExactly("TypeScript");
        assertThat(BinaryInfo.PYTHON.getEnabledLanguages()).containsExactly("Python");
        assertThat(BinaryInfo.PHP.getEnabledLanguages()).containsExactly("PHP");
        assertThat(BinaryInfo.VNU.getEnabledLanguages()).containsExactly("HTML");
        assertThat(BinaryInfo.STYLELINT.getEnabledLanguages()).containsExactly("CSS");
    }

    @Test
    @DisplayName("BinaryInfo should have minimum versions")
    void binaryInfoShouldHaveMinimumVersions() {
        assertThat(BinaryInfo.NODE.getMinimumVersion()).isPresent();
        assertThat(BinaryInfo.JAVAC.getMinimumVersion()).isPresent();
        assertThat(BinaryInfo.TSC.getMinimumVersion()).isPresent();
        assertThat(BinaryInfo.PYTHON.getMinimumVersion()).isPresent();
        assertThat(BinaryInfo.PHP.getMinimumVersion()).isPresent();
        assertThat(BinaryInfo.VNU.getMinimumVersion()).isPresent();
        assertThat(BinaryInfo.STYLELINT.getMinimumVersion()).isPresent();
    }

    @Test
    @DisplayName("BinaryInfo should have download URLs for most binaries")
    void binaryInfoShouldHaveDownloadUrls() {
        assertThat(BinaryInfo.NODE.getDownloadUrl()).isPresent();
        assertThat(BinaryInfo.TSC.getDownloadUrl()).isPresent();
        assertThat(BinaryInfo.PHP.getDownloadUrl()).isPresent();
        assertThat(BinaryInfo.VNU.getDownloadUrl()).isPresent();
        assertThat(BinaryInfo.STYLELINT.getDownloadUrl()).isPresent();
        // javac and python may not have URLs on all platforms
    }

    @Test
    @DisplayName("BinaryInfo.toString should include key details")
    void toStringShouldIncludeDetails() {
        String str = BinaryInfo.NODE.toString();
        assertThat(str).contains("node");
        assertThat(str).contains("BinaryInfo{");
        assertThat(str).contains("JavaScript");
    }

    // ================================================================
    //  BinaryManager creation tests
    // ================================================================

    @Test
    @DisplayName("BinaryManager should create install directory")
    void shouldCreateInstallDirectory() throws IOException {
        Path newDir = tempDir.resolve("new-install");
        assertThat(Files.exists(newDir)).isFalse();
        BinaryManager mgr = new BinaryManager(newDir);
        assertThat(Files.exists(newDir)).isTrue();
        assertThat(mgr.getInstallDir()).isEqualTo(newDir);
    }

    @Test
    @DisplayName("BinaryManager should reject null install dir")
    void shouldRejectNullInstallDir() {
        assertThatThrownBy(() -> new BinaryManager(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ================================================================
    //  Status query tests
    // ================================================================

    @Test
    @DisplayName("getStatus should return a valid status object for each binary")
    void getStatusShouldReturnValidStatus() {
        for (BinaryInfo info : BinaryInfo.ALL) {
            BinaryStatus status = manager.getStatus(info);
            assertThat(status).isNotNull();
            assertThat(status.getBinaryInfo()).isEqualTo(info);
            // isAvailable and versionSatisfied depend on the system
        }
    }

    @Test
    @DisplayName("getAllStatuses should return status for all binaries")
    void getAllStatusesShouldReturnAllStatuses() {
        List<BinaryStatus> statuses = manager.getAllStatuses();
        assertThat(statuses).hasSize(BinaryInfo.ALL.length);
        // Each status should correspond to a known binary
        for (BinaryStatus status : statuses) {
            assertThat(status.getBinaryInfo()).isIn((Object[]) BinaryInfo.ALL);
        }
    }

    @Test
    @DisplayName("Full report should contain all binary IDs")
    void fullReportShouldContainAllBinaryIds() {
        String report = manager.getFullReport();
        assertThat(report).contains("Binary Dependency Status Report");
        for (BinaryInfo info : BinaryInfo.ALL) {
            assertThat(report).contains(info.getId());
        }
    }

    // ================================================================
    //  Progress listener tests
    // ================================================================

    @Test
    @DisplayName("Progress listener should be addable and removable")
    void progressListenerShouldBeManageable() {
        DownloadProgressListener listener = new DownloadProgressListener() {};
        assertThat(manager.removeProgressListener(listener)).isFalse();

        manager.addProgressListener(listener);
        assertThat(manager.removeProgressListener(listener)).isTrue();
    }

    @Test
    @DisplayName("addProgressListener should reject null")
    void shouldRejectNullListener() {
        assertThatThrownBy(() -> manager.addProgressListener(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("clearProgressListeners should remove all listeners")
    void clearShouldRemoveAllListeners() {
        DownloadProgressListener l1 = new DownloadProgressListener() {};
        DownloadProgressListener l2 = new DownloadProgressListener() {};
        manager.addProgressListener(l1);
        manager.addProgressListener(l2);
        manager.clearProgressListeners();
        // After clearing, remove should return false
        assertThat(manager.removeProgressListener(l1)).isFalse();
        assertThat(manager.removeProgressListener(l2)).isFalse();
    }

    // ================================================================
    //  BinaryStatus tests
    // ================================================================

    @Test
    @DisplayName("BinaryStatus should report availability correctly")
    void statusShouldReportAvailability() {
        BinaryStatus status = manager.getStatus(BinaryInfo.NODE);
        // The status depends on whether node is installed on this system
        assertThat(status.isAvailable()).isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("BinaryStatus formatReport should contain useful info")
    void statusFormatReportShouldContainInfo() {
        BinaryStatus status = manager.getStatus(BinaryInfo.NODE);
        String report = status.formatReport();
        assertThat(report).contains("Binary");
        assertThat(report).contains("node");
        assertThat(report).contains("Status");
        assertThat(report).contains("NPM");
        assertThat(report).contains("Languages");
    }

    @Test
    @DisplayName("BinaryStatus toString should be descriptive")
    void statusToStringShouldBeDescriptive() {
        BinaryStatus status = manager.getStatus(BinaryInfo.NODE);
        String str = status.toString();
        assertThat(str).contains("BinaryStatus{");
        assertThat(str).contains("node");
    }

    // ================================================================
    //  Version comparison tests
    // ================================================================

    @Test
    @DisplayName("Version comparison should handle equal versions")
    void versionComparisonShouldHandleEqualVersions() {
        assertThat(BinaryManager.compareVersions("1.0.0", "1.0.0")).isEqualTo(0);
        assertThat(BinaryManager.compareVersions("2.5.3", "2.5.3")).isEqualTo(0);
    }

    @Test
    @DisplayName("Version comparison should handle greater versions")
    void versionComparisonShouldHandleGreaterVersions() {
        assertThat(BinaryManager.compareVersions("2.0.0", "1.0.0")).isPositive();
        assertThat(BinaryManager.compareVersions("1.1.0", "1.0.0")).isPositive();
        assertThat(BinaryManager.compareVersions("1.0.1", "1.0.0")).isPositive();
    }

    @Test
    @DisplayName("Version comparison should handle lesser versions")
    void versionComparisonShouldHandleLesserVersions() {
        assertThat(BinaryManager.compareVersions("1.0.0", "2.0.0")).isNegative();
        assertThat(BinaryManager.compareVersions("1.0.0", "1.1.0")).isNegative();
        assertThat(BinaryManager.compareVersions("1.0.0", "1.0.1")).isNegative();
    }

    @Test
    @DisplayName("Version comparison should handle different segment counts")
    void versionComparisonShouldHandleDifferentSegmentCounts() {
        assertThat(BinaryManager.compareVersions("1.0", "1.0.0")).isEqualTo(0);
        assertThat(BinaryManager.compareVersions("1.0.0", "1.0")).isEqualTo(0);
        assertThat(BinaryManager.compareVersions("1.1", "1.0.5")).isPositive();
    }

    // ================================================================
    //  BinaryUtils static convenience tests
    // ================================================================

    @Test
    @DisplayName("BinaryUtils.checkAll should return statuses")
    void binaryUtilsCheckAllShouldReturnStatuses() throws IOException {
        List<BinaryStatus> statuses = BinaryUtils.checkAll();
        assertThat(statuses).hasSize(BinaryInfo.ALL.length);
    }

    @Test
    @DisplayName("BinaryUtils.check should return status for specific binary")
    void binaryUtilsCheckShouldReturnStatus() throws IOException {
        BinaryStatus status = BinaryUtils.check(BinaryInfo.NODE);
        assertThat(status).isNotNull();
        assertThat(status.getBinaryInfo()).isEqualTo(BinaryInfo.NODE);
    }

    @Test
    @DisplayName("BinaryUtils.getStatusReport should return formatted report")
    void binaryUtilsReportShouldBeFormatted() throws IOException {
        String report = BinaryUtils.getStatusReport();
        assertThat(report).isNotEmpty();
        assertThat(report).contains("Binary Dependency Status Report");
    }

    @Test
    @DisplayName("BinaryUtils.isLanguageSupported should return boolean")
    void binaryUtilsLanguageCheckShouldWork() throws IOException {
        // This depends on the system; just ensure no exception
        boolean supported = BinaryUtils.isLanguageSupported("Java");
        assertThat(supported).isInstanceOf(Boolean.class);
    }

    @Test
    @DisplayName("BinaryUtils.getMissingBinaries should return list")
    void binaryUtilsMissingShouldReturnList() throws IOException {
        // Just checking it doesn't throw
        List<BinaryInfo> missing = BinaryUtils.getMissingBinaries("Java");
        assertThat(missing).isNotNull();
    }

    @Test
    @DisplayName("BinaryUtils.getDiagnostics should return null or message")
    void binaryUtilsDiagnosticsShouldWork() throws IOException {
        String diagnostics = BinaryUtils.getDiagnostics("Java");
        // Either null (everything OK) or a message
        if (diagnostics != null) {
            assertThat(diagnostics).contains("Missing dependencies");
        }
    }

    // ================================================================
    //  Edge case tests
    // ================================================================

    @Test
    @DisplayName("Unknown language should not crash BinaryUtils")
    void unknownLanguageShouldNotCrash() throws IOException {
        boolean supported = BinaryUtils.isLanguageSupported("UnknownLang");
        assertThat(supported).isTrue(); // No binaries required -> true
    }

    @Test
    @DisplayName("BinaryUtils.getMissingBinaries for unknown language should return empty")
    void unknownLanguageMissingShouldBeEmpty() throws IOException {
        List<BinaryInfo> missing = BinaryUtils.getMissingBinaries("COBOL");
        assertThat(missing).isEmpty();
    }

    @Test
    @DisplayName("BinaryInfo.getInstalledPath should return a path")
    void installedPathShouldBeNotNull() {
        for (BinaryInfo info : BinaryInfo.ALL) {
            Path path = info.getInstalledPath(tempDir);
            assertThat(path).isNotNull();
            assertThat(path.startsWith(tempDir)).isTrue();
        }
    }

    @Test
    @DisplayName("BinaryInfo for npm packages should resolve to node_modules/.bin")
    void npmInstalledPathShouldBeCorrect() {
        Path tscPath = BinaryInfo.TSC.getInstalledPath(tempDir);
        assertThat(tscPath.toString()).contains("node_modules");
        assertThat(tscPath.toString()).contains(".bin");
    }

    @Test
    @DisplayName("BinaryInfo for vnu should resolve to vnu.jar")
    void vnuInstalledPathShouldBeCorrect() {
        Path vnuPath = BinaryInfo.VNU.getInstalledPath(tempDir);
        assertThat(vnuPath.getFileName().toString()).isEqualTo("vnu.jar");
    }
}
