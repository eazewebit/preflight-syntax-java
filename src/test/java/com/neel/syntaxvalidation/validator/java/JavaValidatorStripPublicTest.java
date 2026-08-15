package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JavaValidator - stripPublicModifier")
class JavaValidatorStripPublicTest {

    private final JavaValidator validator = new JavaValidator();

    @Test
    @DisplayName("stripPublicModifier removes 'public' from 'public class ...'")
    void stripPublicModifier_removesPublicFromClassDeclaration() throws Exception {
        Method m = JavaValidator.class.getDeclaredMethod("stripPublicModifier", String.class);
        m.setAccessible(true);

        String input = "public class InvalidJava {\n    int x = 1;\n}";
        String result = (String) m.invoke(validator, input);

        assertThat(result).startsWith("class InvalidJava {");
        assertThat(result).doesNotContain("public class");
    }

    @Test
    @DisplayName("stripPublicModifier is idempotent on already-declared class")
    void stripPublicModifier_idempotentWhenNoPublic() throws Exception {
        Method m = JavaValidator.class.getDeclaredMethod("stripPublicModifier", String.class);
        m.setAccessible(true);

        String input = "class ValidJava {\n    int x = 1;\n}";
        String result = (String) m.invoke(validator, input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("stripPublicModifier handles indented public class lines")
    void stripPublicModifier_handlesIndentedPublicClass() throws Exception {
        Method m = JavaValidator.class.getDeclaredMethod("stripPublicModifier", String.class);
        m.setAccessible(true);

        String input = "  public class Foo {}";
        String result = (String) m.invoke(validator, input);

        // The regex ^\s*public\s+class\s+ also consumes leading whitespace
        assertThat(result).isEqualTo("class Foo {}");
        assertThat(result).doesNotContain("public");
    }

    @Test
    @DisplayName("stripPublicModifier preserves non-public class declarations")
    void stripPublicModifier_preservesNonPublicClasses() throws Exception {
        Method m = JavaValidator.class.getDeclaredMethod("stripPublicModifier", String.class);
        m.setAccessible(true);

        String input = "public class Outer {\n    public class Inner {\n    }\n}";
        String result = (String) m.invoke(validator, input);

        // Both public class declarations should be stripped
        assertThat(result).contains("class Outer");
        assertThat(result).contains("class Inner");
        assertThat(result).doesNotContain("public class");
    }

    @Test
    @DisplayName("built-in engine still rejects genuinely invalid code after stripping")
    void builtInEngine_rejectsInvalidCodeAfterStrip() {
        // Missing closing brace - the stripPublicModifier should make the
        // content pass through to the built-in engine without javac filename errors
        String invalid = "public class Broken {";
        ValidationResult result = validator.validate(invalid);

        // The engine should detect the missing brace, not a filename mismatch
        assertThat(result.isValid()).isFalse();
        // The errors should be about syntax (missing brace), not about filename
        assertThat(result.getErrors())
                .noneMatch(e -> e.getMessage().contains("should be declared in a file named"));
    }

    @Test
    @DisplayName("built-in engine accepts valid code after stripping public")
    void builtInEngine_acceptsValidCodeAfterStrip() {
        String valid = "public class Simple { int x = 1; }";
        ValidationResult result = validator.validate(valid);

        assertThat(result.isValid()).isTrue();
    }
}