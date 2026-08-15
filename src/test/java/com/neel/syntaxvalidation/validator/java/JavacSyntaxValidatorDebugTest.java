package com.neel.syntaxvalidation.validator.java;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.Test;

/**
 * Debug test to understand what errors JavacTask.parse() produces
 * for specific source code inputs.
 */
class JavacSyntaxValidatorDebugTest {

    private final JavacSyntaxValidator validator = new JavacSyntaxValidator();

    @Test
    void debugPatternMatchingInstanceof() {
        String source = """
                class Foo {
                    void test(Object obj) {
                        if (obj instanceof String s) {
                            System.out.println(s.length());
                        }
                    }
                }
                """;
        ValidationResult result = validator.validate(source);
        System.out.println("=== Pattern Matching instanceof ===");
        System.out.println("valid: " + result.isValid());
        System.out.println("message: " + result.getMessage());
        for (var e : result.getErrors()) {
            System.out.printf("  error: line=%d col=%d msg=%s toolOutput=%s%n",
                    e.getLine(), e.getColumn(), e.getMessage(), e.getToolOutput());
        }
    }

    @Test
    void debugConflictingModifiers() {
        String source = """
                class Foo {
                    public private void test() {}
                }
                """;
        ValidationResult result = validator.validate(source);
        System.out.println("=== Conflicting Modifiers ===");
        System.out.println("valid: " + result.isValid());
        System.out.println("message: " + result.getMessage());
        for (var e : result.getErrors()) {
            System.out.printf("  error: line=%d col=%d msg=%s toolOutput=%s%n",
                    e.getLine(), e.getColumn(), e.getMessage(), e.getToolOutput());
        }
    }

    @Test
    void debugTextBlocks() {
        String source = "class Foo {\n"
                + "    String html = \"\"\"\n"
                + "            <html>\n"
                + "            <body>Hello</body>\n"
                + "            </html>\n"
                + "            \"\"\";\n"
                + "}\n";
        ValidationResult result = validator.validate(source);
        System.out.println("=== Text Blocks ===");
        System.out.println("valid: " + result.isValid());
        System.out.println("message: " + result.getMessage());
        for (var e : result.getErrors()) {
            System.out.printf("  error: line=%d col=%d msg=%s toolOutput=%s%n",
                    e.getLine(), e.getColumn(), e.getMessage(), e.getToolOutput());
        }
    }
}
