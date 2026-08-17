package com.neel.syntaxvalidation.validator.html;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.process.ProcessExecutor;
import com.neel.syntaxvalidation.process.ProcessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlValidator")
class HtmlValidatorTest {

    @Test
    @DisplayName("getLanguage() returns HTML")
    void getLanguage_returnsHtml() {
        assertThat(new HtmlValidator().getLanguage()).isEqualTo(Language.HTML);
    }

    @Nested
    @DisplayName("when vnu.jar reports success")
    class VnuReportsSuccess {

        @Test
        @DisplayName("returns valid when vnu produces no errors")
        void returnsValid() {
            String vnuOutput = """
                    {"messages": []}
                    """;
            ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(0, vnuOutput, "", false));
            HtmlValidator validator = validatorWithBinary(stub);
            ValidationResult result = validator.validate("<!DOCTYPE html><html><head></head><body></body></html>");
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("returns valid when vnu produces empty stdout")
        void returnsValid_onEmptyOutput() {
            ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(0, "", "", false));
            HtmlValidator validator = validatorWithBinary(stub);
            ValidationResult result = validator.validate("<p>Hello</p>");
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("when vnu.jar reports errors")
    class VnuReportsErrors {

        @Test
        @DisplayName("returns invalid with parsed errors from vnu JSON output")
        void returnsInvalid_withParsedErrors() {
            String vnuOutput = """
                    {"messages": [
                      {"type": "error", "message": "Stray end tag 'div'.", "lastLine": 5, "lastColumn": 10},
                      {"type": "error", "message": "Element 'p' not allowed as child of 'head'.", "lastLine": 3, "lastColumn": 5},
                      {"type": "info", "message": "The document is valid.", "lastLine": 0, "lastColumn": 0}
                    ]}
                    """;
            ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(1, vnuOutput, "", false));
            HtmlValidator validator = validatorWithBinary(stub);
            ValidationResult result = validator.validate("<html><head><p>Bad</p></head><body></div></body></html>");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors().get(0).getMessage()).contains("Stray end tag");
            assertThat(result.getErrors().get(1).getMessage()).contains("not allowed as child");
        }

        @Test
        @DisplayName("returns invalid when vnu outputs to stderr instead of stdout")
        void returnsInvalid_withStderrOutput() {
            String vnuStderr = """
                    {"messages": [
                      {"type": "error", "message": "Missing DOCTYPE.", "lastLine": 1, "lastColumn": 1}
                    ]}
                    """;
            ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(1, "", vnuStderr, false));
            HtmlValidator validator = validatorWithBinary(stub);
            ValidationResult result = validator.validate("<html><head></head><body></body></html>");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(1);
            assertThat(result.getErrors().get(0).getMessage()).contains("Missing DOCTYPE");
        }
    }

    @Nested
    @DisplayName("when vnu.jar times out")
    class VnuTimeout {

        @Test
        @DisplayName("returns invalid on timeout")
        void returnsInvalid_onTimeout() {
            ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(-1, "", "timeout", true));
            HtmlValidator validator = validatorWithBinary(stub);
            ValidationResult result = validator.validate("<p>Content</p>");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("timed out");
        }
    }

    @Nested
    @DisplayName("fallback to embedded HTML syntax engine")
    class FallbackValidation {

        @Test
        @DisplayName("falls back to embedded engine when binary not found")
        void fallsBackToEmbedded_whenBinaryNotFound() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());
            ValidationResult result = validator.validate("<p>Valid content</p>");
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("vnu");
            assertThat(result.getMessage()).containsIgnoringCase("not installed");
        }

        @Test
        @DisplayName("embedded engine detects errors when binary not found")
        void embeddedEngine_detectsErrors_whenBinaryNotFound() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());
            ValidationResult result = validator.validate("<div><p>Unclosed</div>");
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("falls back to embedded engine when process throws IOException")
        void fallsBackToEmbedded_onIOException() {
            ProcessExecutor failing = new FailingProcessExecutor(new java.io.IOException("Cannot run process"));
            BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/vnu"));
            HtmlValidator validator = new HtmlValidator(null, present, failing);
            ValidationResult result = validator.validate("<p>Content</p>");
            assertThat(result.isValid()).isTrue();
            assertThat(result.getMessage()).containsIgnoringCase("valid");
        }

        @Test
        @DisplayName("returns valid from embedded engine for valid HTML when binary not found")
        void embeddedEngine_validHtml_returnsValid() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());
            String validHtml = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><meta charset="UTF-8"><title>Test</title></head>
                    <body>
                      <div>
                        <h1>Title</h1>
                        <p>Paragraph with <strong>bold</strong> and <em>italic</em>.</p>
                      </div>
                      <br>
                      <hr>
                    </body>
                    </html>
                    """;
            ValidationResult result = validator.validate(validHtml);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("null content handling")
    class NullContent {

        @Test
        @DisplayName("treats null content as valid")
        void nullContent_isValid() {
            ProcessExecutor stub = new StubProcessExecutor(new ProcessResult(0, "", "", false));
            HtmlValidator validator = validatorWithBinary(stub);
            ValidationResult result = validator.validate(null);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("treats null content as valid via embedded engine fallback")
        void nullContent_fallback_isValid() {
            BinaryResolver missing = new FixedBinaryResolver(Optional.empty());
            HtmlValidator validator = new HtmlValidator(null, missing, new ProcessExecutor());
            ValidationResult result = validator.validate(null);
            assertThat(result.isValid()).isTrue();
        }
    }

    @Nested
    @DisplayName("command construction")
    class CommandConstruction {

        @Test
        @DisplayName("builds java -jar command for .jar binary path")
        void jarPath_buildsJavaJarCommand() {
            CapturingProcessExecutor capturing = new CapturingProcessExecutor(new ProcessResult(0, "", "", false));
            BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/vnu.jar"));
            HtmlValidator validator = new HtmlValidator(null, present, capturing);
            validator.validate("<p>Content</p>");
            List<String> cmd = capturing.lastCommand();
            assertThat(cmd.get(0)).isEqualTo("java");
            assertThat(cmd.get(1)).isEqualTo("-jar");
            assertThat(cmd.get(2)).isEqualTo("/usr/bin/vnu.jar");
            assertThat(cmd).contains("--format", "json");
        }

        @Test
        @DisplayName("builds direct command for wrapper script binary path")
        void wrapperPath_buildsDirectCommand() {
            CapturingProcessExecutor capturing = new CapturingProcessExecutor(new ProcessResult(0, "", "", false));
            BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/local/bin/vnu"));
            HtmlValidator validator = new HtmlValidator(null, present, capturing);
            validator.validate("<p>Content</p>");
            List<String> cmd = capturing.lastCommand();
            assertThat(cmd.get(0)).isEqualTo("/usr/local/bin/vnu");
            assertThat(cmd).contains("--format", "json");
        }
    }

    @Nested
    @DisplayName("stripCustomNamespaceAttributes")
    class StripCustomNamespaceAttributes {

        @Test
        @DisplayName("strips thymeleaf namespace declaration")
        void stripsThymeleafNamespace() {
            String input = "<html xmlns:th=\"http://www.thymeleaf.org\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<html>");
        }

        @Test
        @DisplayName("converts thymeleaf attributes to base with default value")
        void convertsThymeleafAttributes() {
            String input = "<link th:href=\"@{/css/style.css}\" rel=\"stylesheet\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<link href=\"#\" rel=\"stylesheet\">");
        }

        @Test
        @DisplayName("converts multiple namespace attributes in one tag")
        void convertsMultipleNamespaceAttrs() {
            String input = "<script th:src=\"@{/js/app.js}\" th:inline=\"javascript\" defer></script>";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<script src=\"#\" inline=\"#\" defer></script>");
        }

        @Test
        @DisplayName("preserves data-* attributes")
        void preservesDataAttributes() {
            String input = "<div data-key=\"value\" data-bind=\"click: handler\" class=\"test\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("preserves aria-* attributes")
        void preservesAriaAttributes() {
            String input = "<div aria-label=\"Close\" aria-hidden=\"true\" role=\"button\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("preserves standard xmlns without prefix")
        void preservesStandardXmlns() {
            String input = "<html xmlns=\"http://www.w3.org/1999/xhtml\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("does NOT strip content inside comments")
        void preservesCommentContent() {
            String input = "<!-- <div th:text=\"hello\"> --> <p>text</p>";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("handles null content")
        void handlesNull() {
            assertThat(HtmlValidator.stripCustomNamespaceAttributes(null)).isNull();
        }

        @Test
        @DisplayName("handles empty content")
        void handlesEmpty() {
            assertThat(HtmlValidator.stripCustomNamespaceAttributes("")).isEmpty();
        }

        @Test
        @DisplayName("handles mixed comments and tags with namespace attrs")
        void handlesMixedCommentsAndTags() {
            String input = "<html xmlns:th=\"http://www.thymeleaf.org\">\n<!-- This is a comment with th:text -->\n<body>\n  <div th:text=\"${name}\">placeholder</div>\n  <link th:href=\"@{/style.css}\" rel=\"stylesheet\">\n</body>\n</html>";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).contains("<!-- This is a comment with th:text -->");
            assertThat(result).doesNotContain("xmlns:th=");
            assertThat(result).doesNotContain("th:text=");
            assertThat(result).doesNotContain("th:href=");
            assertThat(result).contains("rel=\"stylesheet\"");
            assertThat(result).contains("text=\"#\"");
            assertThat(result).contains("href=\"#\"");
        }

        @Test
        @DisplayName("converts security namespace attributes to base with default value")
        void convertsSecurityNamespace() {
            String input = "<div sec:authorize=\"isAuthenticated()\" class=\"user\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div authorize=\"#\" class=\"user\">");
        }

        @Test
        @DisplayName("converts self-closing tag namespace attributes to base with default value")
        void convertsSelfClosingTag() {
            String input = "<input th:field=\"*{username}\" type=\"text\" />";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<input field=\"#\" type=\"text\" />");
        }

        @Test
        @DisplayName("preserves closing tags unchanged")
        void preservesClosingTags() {
            String input = "</div>";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("converts unquoted namespace attribute values to base with default")
        void convertsUnquotedValues() {
            String input = "<div th:if=condition class=\"test\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div if=\"#\" class=\"test\">");
        }

        // ---- JSTL/Core Framework Tests ----

        @Test
        @DisplayName("JSTL: converts c:if attribute to base name with default value")
        void convertsJstlCoreCif() {
            String input = "<div c:if=\"${user != null}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div if=\"#\">");
        }

        @Test
        @DisplayName("JSTL: c:forEach tag name preserved, non-namespaced attributes unchanged")
        void convertsJstlCoreCforEach() {
            String input = "<c:forEach items=\"${users}\" var=\"user\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<c:forEach items=\"${users}\" var=\"user\">");
        }

        @Test
        @DisplayName("JSTL: converts c:out attribute to base name with default value")
        void convertsJstlCoreCout() {
            String input = "<span c:out=\"${name}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<span out=\"#\">");
        }

        @Test
        @DisplayName("JSTL: c:set tag name preserved, non-namespaced attributes unchanged")
        void convertsJstlCoreCset() {
            String input = "<c:set var=\"count\" value=\"${items.size()}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<c:set var=\"count\" value=\"${items.size()}\">");
        }

        @Test
        @DisplayName("JSTL: converts c:choose attribute to base name with default value")
        void convertsJstlCoreCchoose() {
            String input = "<c:choose c:test=\"${condition}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<c:choose test=\"#\">");
        }

        // ---- JSTL/Fmt Framework Tests ----

        @Test
        @DisplayName("JSTL/FMT: fmt:formatDate tag name preserved")
        void convertsJstlFmtFormatDate() {
            String input = "<fmt:formatDate value=\"${now}\" pattern=\"yyyy-MM-dd\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<fmt:formatDate value=\"${now}\" pattern=\"yyyy-MM-dd\">");
        }

        @Test
        @DisplayName("JSTL/FMT: converts fmt:message attribute to base name")
        void convertsJstlFmtMessage() {
            String input = "<span fmt:message=\"greeting\" key=\"test\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<span message=\"#\" key=\"test\">");
        }

        @Test
        @DisplayName("JSTL/FMT: fmt:bundle tag name preserved")
        void convertsJstlFmtBundle() {
            String input = "<fmt:bundle basename=\"messages\" prefix=\"label.\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<fmt:bundle basename=\"messages\" prefix=\"label.\">");
        }

        // ---- JSTL/Functions Tests ----

        @Test
        @DisplayName("JSTL/FN: converts fn:contains attribute to base name")
        void convertsJstlFnContains() {
            String input = "<div fn:contains=\"${str, 'test'}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div contains=\"#\">");
        }

        @Test
        @DisplayName("JSTL/FN: converts fn:length attribute to base name")
        void convertsJstlFnLength() {
            String input = "<span fn:length=\"${list}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<span length=\"#\">");
        }

        // ---- JSF/Facelets Framework Tests ----

        @Test
        @DisplayName("JSF: f:attribute tag name preserved")
        void convertsJsfFattribute() {
            String input = "<f:attribute name=\"styleClass\" value=\"highlight\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<f:attribute name=\"styleClass\" value=\"highlight\">");
        }

        @Test
        @DisplayName("JSF: h:inputText tag name preserved")
        void convertsJsfHinputText() {
            String input = "<h:inputText value=\"#{bean.name}\" required=\"true\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<h:inputText value=\"#{bean.name}\" required=\"true\">");
        }

        @Test
        @DisplayName("JSF: h:commandButton tag name preserved")
        void convertsJsfHcommandButton() {
            String input = "<h:commandButton value=\"Submit\" action=\"#{bean.save}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<h:commandButton value=\"Submit\" action=\"#{bean.save}\">");
        }

        // ---- Facelets (ui:*) Framework Tests ----

        @Test
        @DisplayName("FACELETS: ui:composition tag name preserved")
        void convertsFaceletsUiComposition() {
            String input = "<ui:composition template=\"/layout.xhtml\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<ui:composition template=\"/layout.xhtml\">");
        }

        @Test
        @DisplayName("FACELETS: ui:include tag name preserved")
        void convertsFaceletsUiInclude() {
            String input = "<ui:include src=\"/fragments/header.xhtml\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<ui:include src=\"/fragments/header.xhtml\">");
        }

        @Test
        @DisplayName("FACELETS: ui:define tag name preserved")
        void convertsFaceletsUiDefine() {
            String input = "<ui:define name=\"content\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<ui:define name=\"content\">");
        }

        @Test
        @DisplayName("FACELETS: ui:decorate tag name preserved")
        void convertsFaceletsUiDecorate() {
            String input = "<ui:decorate template=\"/layout.xhtml\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<ui:decorate template=\"/layout.xhtml\">");
        }

        // ---- PrimeFaces Tests ----

        @Test
        @DisplayName("PRIMEFACES: p:dialog tag name preserved")
        void convertsPrimeFacesDialog() {
            String input = "<p:dialog header=\"Login\" visible=\"#{bean.visible}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<p:dialog header=\"Login\" visible=\"#{bean.visible}\">");
        }

        @Test
        @DisplayName("PRIMEFACES: p:dataTable tag name preserved")
        void convertsPrimeFacesDataTable() {
            String input = "<p:dataTable value=\"#{bean.items}\" var=\"item\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<p:dataTable value=\"#{bean.items}\" var=\"item\">");
        }

        // ---- xml:lang/xml:space Preservation Tests ----

        @Test
        @DisplayName("preserves xml:lang attribute and strips xmlns:th")
        void preservesXmlLang() {
            String input = "<html xml:lang=\"en\" lang=\"en\" xmlns:th=\"http://www.thymeleaf.org\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<html xml:lang=\"en\" lang=\"en\">");
        }

        @Test
        @DisplayName("preserves xml:space attribute and strips th:text")
        void preservesXmlSpace() {
            String input = "<pre xml:space=\"preserve\" th:text=\"${code}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<pre xml:space=\"preserve\" text=\"#\">");
        }

        @Test
        @DisplayName("preserves xml:base attribute and strips xmlns:ui")
        void preservesXmlBase() {
            String input = "<html xml:base=\"http://example.com/\" xmlns:ui=\"http://xmlns.jcp.org/jsf/facelets\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<html xml:base=\"http://example.com/\">");
        }

        @Test
        @DisplayName("preserves xml:id attribute and strips th:text")
        void preservesXmlId() {
            String input = "<div xml:id=\"unique123\" th:text=\"${msg}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div xml:id=\"unique123\" text=\"#\">");
        }

        // ---- Mixed Framework Tests ----

        @Test
        @DisplayName("MIXED: Thymeleaf + JSTL + Facelets in same template")
        void mixedFrameworksTemplate() {
            String input = "<div th:if=\"${show}\" c:if=\"${user != null}\" ui:fragment=\"content\" class=\"main\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div if=\"#\" if=\"#\" fragment=\"#\" class=\"main\">");
        }

        @Test
        @DisplayName("MIXED: Spring Security + JSTL in same template")
        void mixedSecurityAndJstl() {
            String input = "<div sec:authorize=\"isAuthenticated()\" c:if=\"${hasRole}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div authorize=\"#\" if=\"#\">");
        }

        @Test
        @DisplayName("MIXED: Custom vendor namespace prefix")
        void customVendorNamespace() {
            String input = "<div acme:widget=\"config\" acme:theme=\"dark\" class=\"custom\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div widget=\"#\" theme=\"#\" class=\"custom\">");
        }

        @Test
        @DisplayName("MIXED: Multiple namespaces in same tag with complex values")
        void multipleNamespacesWithComplexValues() {
            String input = "<a th:href=\"@{/url}\" th:text=\"${link}\" sec:authorize=\"isAuthenticated()\" class=\"btn\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<a href=\"#\" text=\"#\" authorize=\"#\" class=\"btn\">");
        }

        // ---- Edge Cases ----

        @Test
        @DisplayName("EDGE: Namespace with complex expression value")
        void complexExpressionValue() {
            String input = "<div th:with=\"isUser=${user.role == 'ADMIN'}\" th:if=\"${isUser}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div with=\"#\" if=\"#\">");
        }

        @Test
        @DisplayName("EDGE: Single-quoted attribute values with colons preserved")
        void singleQuotedValuesPreserved() {
            String input = "<link th:href='@{/css/style.css}' rel='stylesheet' />";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<link href=\"#\" rel='stylesheet' />");
        }

        @Test
        @DisplayName("EDGE: CSS colon in style value preserved")
        void cssColonInStylePreserved() {
            String input = "<div style=\"display:none; color:red\" th:text=\"${msg}\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div style=\"display:none; color:red\" text=\"#\">");
        }

        @Test
        @DisplayName("EDGE: Malformed tag (no closing >) handled gracefully")
        void malformedTagHandled() {
            String input = "<div th:if=\"${show}\"";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<div th:if=\"${show}\"");
        }

        @Test
        @DisplayName("EDGE: Plain HTML without namespaces unchanged")
        void plainHtmlUnchanged() {
            String input = "<div class=\"container\" id=\"main\"><p>Hello World</p></div>";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo(input);
        }

        @Test
        @DisplayName("EDGE: Self-closing tags with namespaces")
        void selfClosingTagsWithNamespaces() {
            String input = "<input type=\"text\" th:field=\"*{email}\" />";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<input type=\"text\" field=\"#\" />");
        }

        @Test
        @DisplayName("EDGE: Rocket Faces r:outputScript tag name preserved")
        void rocketFacesNamespace() {
            String input = "<r:outputScript name=\"app.js\" target=\"head\">";
            String result = HtmlValidator.stripCustomNamespaceAttributes(input);
            assertThat(result).isEqualTo("<r:outputScript name=\"app.js\" target=\"head\">");
        }
    }

    // -----------------------------------------------------------------
    //  Test helpers
    // -----------------------------------------------------------------

    private HtmlValidator validatorWithBinary(ProcessExecutor executor) {
        BinaryResolver present = new FixedBinaryResolver(Optional.of("/usr/bin/vnu"));
        return new HtmlValidator(null, present, executor);
    }

    private static class StubProcessExecutor extends ProcessExecutor {
        private final ProcessResult result;
        StubProcessExecutor(ProcessResult result) { this.result = result; }
        @Override
        public ProcessResult execute(List<String> command) { return result; }
    }

    private static class CapturingProcessExecutor extends ProcessExecutor {
        private final ProcessResult result;
        private List<String> lastCommand;
        CapturingProcessExecutor(ProcessResult result) { this.result = result; }
        @Override
        public ProcessResult execute(List<String> command) { this.lastCommand = command; return result; }
        List<String> lastCommand() { return lastCommand; }
    }

    private static class FixedBinaryResolver extends BinaryResolver {
        private final Optional<String> path;
        FixedBinaryResolver(Optional<String> path) { this.path = path; }
        @Override
        public Optional<String> resolve(String preferredPath, String binaryName) { return path; }
    }

    private static class FailingProcessExecutor extends ProcessExecutor {
        private final java.io.IOException exception;
        FailingProcessExecutor(java.io.IOException exception) { this.exception = exception; }
        @Override
        public ProcessResult execute(List<String> command) throws java.io.IOException { throw exception; }
    }
}