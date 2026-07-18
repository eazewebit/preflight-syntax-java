package com.neel.syntaxvalidation.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Comprehensive tests for {@link ProcessResult}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Record construction and accessor methods</li>
 *   <li>{@code succeeded()} convenience method for all exit-code and timedOut combinations</li>
 *   <li>{@code equals} and {@code hashCode} contracts</li>
 *   <li>{@code toString} representation</li>
 *   <li>Edge cases: empty output, negative exit code, special characters</li>
 *   <li>Null-safety for constructor parameters</li>
 *   <li>Immutability guarantees</li>
 * </ul>
 */
@DisplayName("ProcessResult")
class ProcessResultTest {

    // =========================================================================
    //  CONSTRUCTION AND ACCESSORS
    // =========================================================================

    @Nested
    @DisplayName("construction and accessors")
    class ConstructionAndAccessors {

        @Test
        @DisplayName("record stores exitCode correctly")
        void exitCodeIsStored() {
            ProcessResult result = new ProcessResult(0, "out", "err", false);
            assertThat(result.exitCode()).isZero();
        }

        @Test
        @DisplayName("record stores stdout correctly")
        void stdoutIsStored() {
            ProcessResult result = new ProcessResult(0, "hello world", "", false);
            assertThat(result.stdout()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("record stores stderr correctly")
        void stderrIsStored() {
            ProcessResult result = new ProcessResult(0, "", "error message", false);
            assertThat(result.stderr()).isEqualTo("error message");
        }

        @Test
        @DisplayName("record stores timedOut correctly")
        void timedOutIsStored() {
            ProcessResult result = new ProcessResult(-1, "", "", true);
            assertThat(result.timedOut()).isTrue();
        }

        @Test
        @DisplayName("timedOut false is stored correctly")
        void timedOutFalseIsStored() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result.timedOut()).isFalse();
        }

        @Test
        @DisplayName("null stdout throws NullPointerException")
        void nullStdoutThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProcessResult(0, null, "", false));
        }

        @Test
        @DisplayName("null stderr throws NullPointerException")
        void nullStderrThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new ProcessResult(0, "", null, false));
        }
    }

    // =========================================================================
    //  SUCCEEDED
    // =========================================================================

    @Nested
        @DisplayName("succeeded")
        class Succeeded {

            @Test
            @DisplayName("exit code 0 and not timed out means succeeded")
            void exitCodeZeroNotTimedOutMeansSucceeded() {
                ProcessResult result = new ProcessResult(0, "", "", false);
                assertThat(result.succeeded()).isTrue();
            }

            @Test
            @DisplayName("exit code 0 but timed out means failed")
            void exitCodeZeroButTimedOutMeansFailed() {
                ProcessResult result = new ProcessResult(0, "", "", true);
                assertThat(result.succeeded()).isFalse();
            }

            @Test
            @DisplayName("positive exit code means failed")
            void positiveExitCodeMeansFailed() {
                ProcessResult result = new ProcessResult(1, "", "", false);
                assertThat(result.succeeded()).isFalse();
            }

            @Test
            @DisplayName("negative exit code means failed")
            void negativeExitCodeMeansFailed() {
                ProcessResult result = new ProcessResult(-1, "", "", false);
                assertThat(result.succeeded()).isFalse();
            }

            @Test
            @DisplayName("large positive exit code means failed")
            void largePositiveExitCodeMeansFailed() {
                ProcessResult result = new ProcessResult(127, "", "", false);
                assertThat(result.succeeded()).isFalse();
            }

            @Test
            @DisplayName("exit code 2 means failed (common error code)")
            void exitCode2MeansFailed() {
                ProcessResult result = new ProcessResult(2, "", "", false);
                assertThat(result.succeeded()).isFalse();
            }

            @Test
            @DisplayName("exit code 137 (SIGKILL) and timed out means failed")
            void exitCode137TimedOutMeansFailed() {
                ProcessResult result = new ProcessResult(137, "", "", true);
                assertThat(result.succeeded()).isFalse();
                assertThat(result.timedOut()).isTrue();
            }
        }

    // =========================================================================
    //  TIMED OUT
    // =========================================================================

    @Nested
    @DisplayName("timedOut")
    class TimedOut {

        @Test
        @DisplayName("timedOut true is reflected correctly")
        void timedOutTrue() {
            ProcessResult result = new ProcessResult(-1, "", "", true);
            assertThat(result.timedOut()).isTrue();
        }

        @Test
        @DisplayName("timedOut false is reflected correctly")
        void timedOutFalse() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result.timedOut()).isFalse();
        }

        @Test
        @DisplayName("timedOut with non-zero exit code")
        void timedOutWithNonZeroExitCode() {
            ProcessResult result = new ProcessResult(137, "", "", true);
            assertThat(result.timedOut()).isTrue();
            assertThat(result.succeeded()).isFalse();
        }

        @Test
        @DisplayName("not timedOut with zero exit code is the only succeeded case")
        void notTimedOutZeroExitCodeSucceeded() {
            ProcessResult result = new ProcessResult(0, "output", "", false);
            assertThat(result.timedOut()).isFalse();
            assertThat(result.succeeded()).isTrue();
        }
    }

    // =========================================================================
    //  EQUALS AND HASHCODE
    // =========================================================================

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("equal results are equal")
        void equalResultsAreEqual() {
            ProcessResult a = new ProcessResult(0, "out", "err", false);
            ProcessResult b = new ProcessResult(0, "out", "err", false);
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("equal results have same hashCode")
        void equalResultsHaveSameHashCode() {
            ProcessResult a = new ProcessResult(0, "out", "err", false);
            ProcessResult b = new ProcessResult(0, "out", "err", false);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("results with different exit codes are not equal")
        void differentExitCodesNotEqual() {
            ProcessResult a = new ProcessResult(0, "", "", false);
            ProcessResult b = new ProcessResult(1, "", "", false);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("results with different stdout are not equal")
        void differentStdoutNotEqual() {
            ProcessResult a = new ProcessResult(0, "out1", "", false);
            ProcessResult b = new ProcessResult(0, "out2", "", false);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("results with different stderr are not equal")
        void differentStderrNotEqual() {
            ProcessResult a = new ProcessResult(0, "", "err1", false);
            ProcessResult b = new ProcessResult(0, "", "err2", false);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("results with different timedOut flags are not equal")
        void differentTimedOutNotEqual() {
            ProcessResult a = new ProcessResult(0, "", "", false);
            ProcessResult b = new ProcessResult(0, "", "", true);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("result is not equal to null")
        void notEqualToNull() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result).isNotEqualTo(null);
        }

        @Test
        @DisplayName("result is not equal to different type")
        void notEqualToDifferentType() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result).isNotEqualTo("a string");
        }

        @Test
        @DisplayName("result equals itself (reflexive)")
        void reflexive() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result).isEqualTo(result);
        }

        @Test
        @DisplayName("equals is symmetric")
        void symmetric() {
            ProcessResult a = new ProcessResult(0, "out", "err", false);
            ProcessResult b = new ProcessResult(0, "out", "err", false);
            assertThat(a).isEqualTo(b);
            assertThat(b).isEqualTo(a);
        }

        @Test
        @DisplayName("equals is transitive")
        void transitive() {
            ProcessResult a = new ProcessResult(0, "out", "err", false);
            ProcessResult b = new ProcessResult(0, "out", "err", false);
            ProcessResult c = new ProcessResult(0, "out", "err", false);
            assertThat(a).isEqualTo(b);
            assertThat(b).isEqualTo(c);
            assertThat(a).isEqualTo(c);
        }
    }

    // =========================================================================
    //  TOSTRING
    // =========================================================================

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains exit code")
        void containsExitCode() {
            ProcessResult result = new ProcessResult(42, "", "", false);
            assertThat(result.toString()).contains("42");
        }

        @Test
        @DisplayName("toString contains stdout")
        void containsStdout() {
            ProcessResult result = new ProcessResult(0, "hello", "", false);
            assertThat(result.toString()).contains("hello");
        }

        @Test
        @DisplayName("toString contains stderr")
        void containsStderr() {
            ProcessResult result = new ProcessResult(0, "", "error", false);
            assertThat(result.toString()).contains("error");
        }

        @Test
        @DisplayName("toString contains timedOut flag")
        void containsTimedOut() {
            ProcessResult result = new ProcessResult(0, "", "", true);
            assertThat(result.toString()).contains("true");
        }

        @Test
        @DisplayName("toString is not null or empty")
        void notNullOrEmpty() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result.toString()).isNotNull().isNotEmpty();
        }
    }

    // =========================================================================
    //  EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty stdout and stderr")
        void emptyOutput() {
            ProcessResult result = new ProcessResult(0, "", "", false);
            assertThat(result.stdout()).isEmpty();
            assertThat(result.stderr()).isEmpty();
            assertThat(result.succeeded()).isTrue();
        }

        @Test
        @DisplayName("very large stdout")
        void veryLargeStdout() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("line").append(i).append("\n");
            }
            String stdout = sb.toString();
            ProcessResult result = new ProcessResult(0, stdout, "", false);
            assertThat(result.stdout()).hasSize(stdout.length());
        }

        @Test
        @DisplayName("very large stderr")
        void veryLargeStderr() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("error").append(i).append("\n");
            }
            String stderr = sb.toString();
            ProcessResult result = new ProcessResult(1, "", stderr, false);
            assertThat(result.stderr()).hasSize(stderr.length());
        }

        @Test
        @DisplayName("negative exit code (-1 typically means process failed to start)")
        void negativeExitCode() {
            ProcessResult result = new ProcessResult(-1, "", "Process failed to start", false);
            assertThat(result.exitCode()).isEqualTo(-1);
            assertThat(result.succeeded()).isFalse();
        }

        @Test
        @DisplayName("exit code 126 (command not executable)")
        void exitCode126() {
            ProcessResult result = new ProcessResult(126, "", "Permission denied", false);
            assertThat(result.exitCode()).isEqualTo(126);
            assertThat(result.succeeded()).isFalse();
        }

        @Test
        @DisplayName("exit code 127 (command not found)")
        void exitCode127() {
            ProcessResult result = new ProcessResult(127, "", "Command not found", false);
            assertThat(result.exitCode()).isEqualTo(127);
            assertThat(result.succeeded()).isFalse();
        }

        @Test
        @DisplayName("stdout with special characters")
        void stdoutWithSpecialChars() {
            String stdout = "Output with special chars: àáâãäåæçèéêë";
            ProcessResult result = new ProcessResult(0, stdout, "", false);
            assertThat(result.stdout()).isEqualTo(stdout);
        }

        @Test
        @DisplayName("stderr with special characters")
        void stderrWithSpecialChars() {
            String stderr = "Error with special chars: ñòóôõöøùúûüýþÿ";
            ProcessResult result = new ProcessResult(1, "", stderr, false);
            assertThat(result.stderr()).isEqualTo(stderr);
        }

        @Test
        @DisplayName("multiline stdout")
        void multilineStdout() {
            String stdout = "line1\nline2\nline3\nline4\nline5";
            ProcessResult result = new ProcessResult(0, stdout, "", false);
            assertThat(result.stdout()).isEqualTo(stdout);
        }

        @Test
        @DisplayName("multiline stderr")
        void multilineStderr() {
            String stderr = "error1\nerror2\nerror3";
            ProcessResult result = new ProcessResult(1, "", stderr, false);
            assertThat(result.stderr()).isEqualTo(stderr);
        }

        @Test
        @DisplayName("both stdout and stderr have content")
        void bothOutputsHaveContent() {
            ProcessResult result = new ProcessResult(0, "output", "warning", false);
            assertThat(result.stdout()).isEqualTo("output");
            assertThat(result.stderr()).isEqualTo("warning");
        }

        @Test
        @DisplayName("Integer.MAX_VALUE exit code")
        void maxIntExitCode() {
            ProcessResult result = new ProcessResult(Integer.MAX_VALUE, "", "", false);
            assertThat(result.exitCode()).isEqualTo(Integer.MAX_VALUE);
            assertThat(result.succeeded()).isFalse();
        }

        @Test
        @DisplayName("Integer.MIN_VALUE exit code")
        void minIntExitCode() {
            ProcessResult result = new ProcessResult(Integer.MIN_VALUE, "", "", false);
            assertThat(result.exitCode()).isEqualTo(Integer.MIN_VALUE);
            assertThat(result.succeeded()).isFalse();
        }
    }

    // =========================================================================
    //  IMMUTABILITY
    // =========================================================================

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("record fields cannot be modified (record immutability)")
        void recordIsImmutable() {
            ProcessResult result = new ProcessResult(0, "out", "err", false);
            // Records are immutable by design — accessors return the same values
            assertThat(result.exitCode()).isZero();
            assertThat(result.stdout()).isEqualTo("out");
            assertThat(result.stderr()).isEqualTo("err");
            assertThat(result.timedOut()).isFalse();

            // Create another result and verify original is unchanged
            ProcessResult other = new ProcessResult(1, "other", "other", true);
            assertThat(result.exitCode()).isZero();
            assertThat(result.stdout()).isEqualTo("out");
            assertThat(result.stderr()).isEqualTo("err");
            assertThat(result.timedOut()).isFalse();
        }
    }
}