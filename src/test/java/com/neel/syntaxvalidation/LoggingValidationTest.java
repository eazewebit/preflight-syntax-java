package com.neel.syntaxvalidation;

import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.java.JavaValidator;
import com.neel.syntaxvalidation.validator.python.PythonValidator;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;

/**
 * Test class to verify logging output for binary vs fallback validation.
 * Run this to see which validation phase is being used.
 */
public class LoggingValidationTest {

    public static void main(String[] args) {
        System.out.println("=== Testing Java Validation ===");
        testJavaValidation();
        
        System.out.println("\n=== Testing Python Validation ===");
        testPythonValidation();
        
        System.out.println("\n=== Testing JavaScript Validation ===");
        testJavaScriptValidation();
    }

    private static void testJavaValidation() {
        JavaValidator validator = new JavaValidator();
        String validJava = """
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            """;
        
        String invalidJava = """
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello"
                }
            }
            """;
        
        System.out.println("Testing valid Java code...");
        ValidationResult result1 = validator.validate(validJava);
        System.out.println("Result: " + result1);
        
        System.out.println("\nTesting invalid Java code...");
        ValidationResult result2 = validator.validate(invalidJava);
        System.out.println("Result: " + result2);
    }

    private static void testPythonValidation() {
        PythonValidator validator = new PythonValidator();
        String validPython = """
            def hello():
                print("Hello")
            
            hello()
            """;
        
        String invalidPython = """
            def hello():
                print("Hello"
            
            hello()
            """;
        
        System.out.println("Testing valid Python code...");
        ValidationResult result1 = validator.validate(validPython);
        System.out.println("Result: " + result1);
        
        System.out.println("\nTesting invalid Python code...");
        ValidationResult result2 = validator.validate(invalidPython);
        System.out.println("Result: " + result2);
    }

    private static void testJavaScriptValidation() {
        JavaScriptValidator validator = new JavaScriptValidator();
        String validJS = """
            function hello() {
                console.log("Hello");
            }
            
            hello();
            """;
        
        String invalidJS = """
            function hello() {
                console.log("Hello"
            }
            
            hello();
            """;
        
        System.out.println("Testing valid JavaScript code...");
        ValidationResult result1 = validator.validate(validJS);
        System.out.println("Result: " + result1);
        
        System.out.println("\nTesting invalid JavaScript code...");
        ValidationResult result2 = validator.validate(invalidJS);
        System.out.println("Result: " + result2);
    }
}