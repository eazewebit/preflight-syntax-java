package com.neel.syntaxvalidation.validator.typescript;

import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test to understand tokenizer behavior.
 */
public class TokenizerDebugTest {

    @Test
    void debugEnumDeclaration() {
        TypeScriptSyntaxTokenizer tokenizer = new TypeScriptSyntaxTokenizer();
        String enumCode = "enum Direction {\n    Up = 'UP',\n    Down = 'DOWN'\n}";
        List<TsToken> tokens = tokenizer.tokenize(enumCode);
        
        System.out.println("=== Enum Declaration Tokens ===");
        for (TsToken t : tokens) {
            System.out.println("  " + t.type() + ": '" + t.lexeme() + "' at line " + t.line());
        }
        
        // Check validation
        TypeScriptSyntaxEngine engine = new TypeScriptSyntaxEngine();
        ValidationResult result = engine.validate(enumCode);
        System.out.println("Enum validation valid: " + result.isValid());
        if (!result.isValid()) {
            System.out.println("Errors: " + result.getErrors());
        }
    }

    @Test
    void debugTypeAlias() {
        TypeScriptSyntaxTokenizer tokenizer = new TypeScriptSyntaxTokenizer();
        String typeCode = "type Result<T> = { data: T; error?: string; };";
        List<TsToken> tokens = tokenizer.tokenize(typeCode);
        
        System.out.println("=== Type Alias Tokens ===");
        for (TsToken t : tokens) {
            System.out.println("  " + t.type() + ": '" + t.lexeme() + "' at line " + t.line());
        }
        
        // Check validation
        TypeScriptSyntaxEngine engine = new TypeScriptSyntaxEngine();
        ValidationResult result = engine.validate(typeCode);
        System.out.println("Type alias validation valid: " + result.isValid());
        if (!result.isValid()) {
            System.out.println("Errors: " + result.getErrors());
        }
    }

    @Test
    void debugJsxWithAttributes() {
        TypeScriptSyntaxTokenizer tokenizer = new TypeScriptSyntaxTokenizer();
        tokenizer.enableJsxMode();
        String jsxCode = "<div className=\"test\">hello</div>";
        List<TsToken> tokens = tokenizer.tokenize(jsxCode);
        
        System.out.println("=== JSX with Attributes Tokens ===");
        for (TsToken t : tokens) {
            System.out.println("  " + t.type() + ": '" + t.lexeme() + "' at line " + t.line());
        }
        
        // Check validation
        TypeScriptSyntaxEngine engine = new TypeScriptSyntaxEngine();
        engine.enableJsxMode();
        ValidationResult result = engine.validate(jsxCode);
        System.out.println("JSX validation valid: " + result.isValid());
        if (!result.isValid()) {
            System.out.println("Errors: " + result.getErrors());
        }
    }

    @Test
    void debugConditionalType() {
        TypeScriptSyntaxTokenizer tokenizer = new TypeScriptSyntaxTokenizer();
        String code = "type IsString<T> = T extends string ? true : false;";
        List<TsToken> tokens = tokenizer.tokenize(code);
        
        System.out.println("=== Conditional Type Tokens ===");
        for (TsToken t : tokens) {
            System.out.println("  " + t.type() + ": '" + t.lexeme() + "' at line " + t.line());
        }
        
        // Check validation
        TypeScriptSyntaxEngine engine = new TypeScriptSyntaxEngine();
        ValidationResult result = engine.validate(code);
        System.out.println("Conditional type validation valid: " + result.isValid());
        if (!result.isValid()) {
            System.out.println("Errors: " + result.getErrors());
        }
    }

    @Test
    void debugMappedType() {
        TypeScriptSyntaxTokenizer tokenizer = new TypeScriptSyntaxTokenizer();
        String code = "type ReadOnly<T> = {\n    readonly [P in keyof T]: T[P];\n};";
        List<TsToken> tokens = tokenizer.tokenize(code);
        
        System.out.println("=== Mapped Type Tokens ===");
        for (TsToken t : tokens) {
            System.out.println("  " + t.type() + ": '" + t.lexeme() + "' at line " + t.line());
        }
        
        // Check validation
        TypeScriptSyntaxEngine engine = new TypeScriptSyntaxEngine();
        ValidationResult result = engine.validate(code);
        System.out.println("Mapped type validation valid: " + result.isValid());
        if (!result.isValid()) {
            System.out.println("Errors: " + result.getErrors());
        }
    }
}
