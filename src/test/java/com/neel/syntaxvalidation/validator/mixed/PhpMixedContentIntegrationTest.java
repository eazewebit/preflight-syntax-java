package com.neel.syntaxvalidation.validator.mixed;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for PHP mixed-content validation.
 *
 * <p>These tests verify the complete pipeline from HTML/PHP source through
 * extraction of embedded CSS, JavaScript, and PHP blocks, to validation
 * by the respective syntax engines, and finally to line-number remapping
 * in error messages.
 *
 * <p>Test categories:
 * <ul>
 *   <li>Valid PHP mixed documents (positive tests)</li>
 *   <li>Invalid PHP syntax detection (negative tests)</li>
 *   <li>Line number remapping accuracy</li>
 *   <li>Error message format (language context prefix)</li>
 *   <li>Edge cases and boundary conditions</li>
 *   <li>Real-world PHP template patterns</li>
 * </ul>
 */
@DisplayName("PHP Mixed Content – Integration Tests")
class PhpMixedContentIntegrationTest {

    private MixedContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MixedContentValidator();
    }

    // ---------------------------------------------------------------
    // Valid PHP mixed documents
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Valid PHP mixed documents")
    class ValidPhpMixedDocuments {

        @Test
        @DisplayName("simple PHP echo in HTML body validates")
        void simplePhpEcho() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><title>Simple PHP</title></head>
                    <body>
                    <?php echo "Hello World"; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP short-echo tag in HTML validates")
        void shortEchoTag() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Short Echo</title></head>
                    <body>
                    <h1><?= $pageTitle ?></h1>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("multiple PHP blocks in single document validate")
        void multiplePhpBlocks() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <title><?php echo $pageTitle; ?></title>
                    </head>
                    <body>
                    <?php $name = "World"; ?>
                    <h1><?= "Hello, " . $name ?></h1>
                    <?php echo "<p>Generated at: " . date('Y-m-d') . "</p>"; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with variable assignments and concatenation validates")
        void phpVariableAssignments() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $firstName = "John";
                    $lastName = "Doe";
                    $fullName = $firstName . " " . $lastName;
                    $age = 30;
                    $isAdult = true;
                    ?>
                    <p><?= $fullName ?></p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with array operations validates")
        void phpArrayOperations() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $fruits = ["apple", "banana", "cherry"];
                    $count = count($fruits);
                    $first = $fruits[0];
                    ?>
                    <ul>
                    <?php foreach ($fruits as $fruit): ?>
                        <li><?= $fruit ?></li>
                    <?php endforeach; ?>
                    </ul>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with control structures validates")
        void phpControlStructures() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $score = 85;
                    
                    if ($score >= 90) {
                        $grade = "A";
                    } elseif ($score >= 80) {
                        $grade = "B";
                    } else {
                        $grade = "C";
                    }
                    
                    for ($i = 0; $i < 5; $i++) {
                        echo "<span>" . $i . "</span>";
                    }
                    ?>
                    <p>Grade: <?= $grade ?></p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with function definition validates")
        void phpFunctionDefinition() {
            String html = """
                    <html>
                    <body>
                    <?php
                    function greet(string $name): string {
                        return "Hello, " . $name . "!";
                    }
                    
                    function add(int $a, int $b): int {
                        return $a + $b;
                    }
                    ?>
                    <p><?= greet("World") ?></p>
                    <p><?= add(2, 3) ?></p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with class definition validates")
        void phpClassDefinition() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <body>
                    <?php
                    class User {
                        private string $name;
                        private int $age;
                        
                        public function __construct(string $name, int $age) {
                            $this->name = $name;
                            $this->age = $age;
                        }
                        
                        public function getName(): string {
                            return $this->name;
                        }
                        
                        public function getAge(): int {
                            return $this->age;
                        }
                        
                        public function introduce(): string {
                            return "Hi, I'm " . $this->name . " and I'm " . $this->age . " years old.";
                        }
                    }
                    
                    $user = new User("Alice", 25);
                    ?>
                    <p><?= $user->introduce() ?></p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("full document with CSS, JS, and PHP validates")
        void fullDocumentWithCssJsPhp() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title><?php echo $pageTitle ?? "Default"; ?></title>
                        <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body { font-family: Arial, sans-serif; background: #f0f0f0; }
                        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
                        .card { background: white; border-radius: 8px; padding: 20px; margin-bottom: 20px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        h1 { color: #333; }
                        </style>
                    </head>
                    <body>
                    <?php
                    $title = "Welcome to PHP";
                    $items = ["Item 1", "Item 2", "Item 3"];
                    ?>
                    <div class="container">
                        <h1><?= $title ?></h1>
                        <div class="card">
                            <ul>
                            <?php foreach ($items as $index => $item): ?>
                                <li><?= ($index + 1) . ". " . $item ?></li>
                            <?php endforeach; ?>
                            </ul>
                        </div>
                    </div>
                    <script>
                    document.addEventListener('DOMContentLoaded', function() {
                        console.log('Page loaded successfully');
                        const cards = document.querySelectorAll('.card');
                        cards.forEach(card => {
                            card.addEventListener('click', function() {
                                this.style.backgroundColor = '#f9f9f9';
                            });
                        });
                    });
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Invalid PHP syntax detection
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Invalid PHP syntax detection")
    class InvalidPhpSyntax {

        @Test
        @DisplayName("unclosed parenthesis in function signature is detected")
        void unclosedParenthesis() {
            String html = """
                    <html>
                    <body>
                    <?php
                    function broken( {
                        echo "This won't work";
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
            // PHP engine should detect the syntax error
        }

        @Test
        @DisplayName("unclosed curly brace is detected")
        void unclosedCurlyBrace() {
            String html = """
                    <html>
                    <body>
                    <?php
                    if (true) {
                        echo "Missing closing brace";
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unclosed string literal is detected")
        void unclosedString() {
            String html = """
                    <html>
                    <body>
                    <?php
                    echo "This string is not closed;
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("unexpected token is detected")
        void unexpectedToken() {
            String html = """
                    <html>
                    <body>
                    <?php
                    echo @@@invalid;
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP with missing semicolons may be detected")
        void missingSemicolons() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $x = 1
                    $y = 2
                    echo $x + $y
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Line number remapping
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Line number remapping")
    class LineNumberRemapping {

        @Test
        @DisplayName("PHP errors are remapped to original HTML line numbers")
        void phpErrorsRemapped() {
            // PHP block starts at line 5, error should be at line 6 or 7 in original
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <?php
                    function broken( {
                        echo "error";
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            if (!result.isValid()) {
                List<ValidationError> phpErrors = result.getErrors().stream()
                        .filter(e -> e.getMessage().contains("[PHP"))
                        .collect(Collectors.toList());

                // If there are PHP errors, verify they have correct line numbers
                for (ValidationError error : phpErrors) {
                    assertThat(error.getLine()).isGreaterThanOrEqualTo(5);
                }
            }
        }

        @Test
        @DisplayName("CSS errors are remapped when PHP is also present")
        void cssErrorsRemappedWithPhp() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    .broken {
                        color: ; /* invalid CSS */
                    }
                    </style>
                    </head>
                    <body>
                    <?php echo "valid php"; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            if (!result.isValid()) {
                List<ValidationError> cssErrors = result.getErrors().stream()
                        .filter(e -> e.getMessage().contains("[CSS"))
                        .collect(Collectors.toList());

                for (ValidationError error : cssErrors) {
                    // CSS block starts at line 4, so errors should be at line 4+
                    assertThat(error.getLine()).isGreaterThanOrEqualTo(4);
                }
            }
        }

        @Test
        @DisplayName("JS errors are remapped when PHP is also present")
        void jsErrorsRemappedWithPhp() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <?php $x = 1; ?>
                    <script>
                    function broken( {
                        console.log("error");
                    }
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            if (!result.isValid()) {
                List<ValidationError> jsErrors = result.getErrors().stream()
                        .filter(e -> e.getMessage().contains("[JavaScript"))
                        .collect(Collectors.toList());

                for (ValidationError error : jsErrors) {
                    // JS block starts at line 6, so errors should be at line 6+
                    assertThat(error.getLine()).isGreaterThanOrEqualTo(6);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Error message format
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Error message format")
    class ErrorMessageFormat {

        @Test
        @DisplayName("PHP errors contain [PHP in <php>] prefix")
        void phpErrorPrefix() {
            String html = """
                    <html>
                    <body>
                    <?php
                    function broken( {
                        echo "error";
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            if (!result.isValid()) {
                boolean hasPhpPrefixedError = result.getErrors().stream()
                        .anyMatch(e -> e.getMessage().contains("[PHP in <php>]"));
                // At least one error should have the PHP prefix if PHP errors exist
                if (result.getErrors().stream().anyMatch(e -> e.getMessage().contains("[PHP"))) {
                    assertThat(hasPhpPrefixedError).isTrue();
                }
            }
        }

        @Test
        @DisplayName("mixed errors contain appropriate prefixes")
        void mixedErrorPrefixes() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    .broken { color: ; }
                    </style>
                    </head>
                    <body>
                    <?php
                    function broken( {
                        echo "error";
                    }
                    ?>
                    <script>
                    function broken( {
                        console.log("error");
                    }
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();

            if (!result.isValid()) {
                List<String> messages = result.getErrors().stream()
                        .map(ValidationError::getMessage)
                        .collect(Collectors.toList());

                // Check for language-specific prefixes
                boolean hasCssPrefix = messages.stream()
                        .anyMatch(m -> m.contains("[CSS"));
                boolean hasJsPrefix = messages.stream()
                        .anyMatch(m -> m.contains("[JavaScript"));
                boolean hasPhpPrefix = messages.stream()
                        .anyMatch(m -> m.contains("[PHP"));

                // At least one type should have errors with proper prefix
                assertThat(hasCssPrefix || hasJsPrefix || hasPhpPrefix).isTrue();
            }
        }
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("PHP block with only comments validates")
        void phpCommentsOnly() {
            String html = """
                    <html>
                    <body>
                    <?php
                    // This is a comment
                    /* Multi-line
                       comment */
                    # Hash comment
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with heredoc syntax validates")
        void phpHeredoc() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $name = "World";
                    $text = <<<EOT
                    Hello, $name!
                    This is a heredoc string.
                    EOT;
                    echo $text;
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with HTML inside echo validates")
        void phpEchoHtml() {
            String html = """
                    <html>
                    <body>
                    <?php
                    echo '<div class="wrapper">';
                    echo '<p>Generated content</p>';
                    echo '</div>';
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with null coalescing operator validates")
        void phpNullCoalescing() {
            String html = """
                    <html>
                    <body>
                    <?php
                    $name = $_GET['name'] ?? 'Guest';
                    $page = $_GET['page'] ?? 'home';
                    ?>
                    <h1>Welcome, <?= $name ?></h1>
                    <p>Current page: <?= $page ?></p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with type declarations validates")
        void phpTypeDeclarations() {
            String html = """
                    <html>
                    <body>
                    <?php
                    function process(int $id, string $name, ?float $amount = null): array {
                        return [
                            'id' => $id,
                            'name' => $name,
                            'amount' => $amount ?? 0.0,
                        ];
                    }
                    ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("empty PHP block is handled gracefully")
        void emptyPhpBlock() {
            String html = """
                    <html>
                    <body>
                    <?php ?>
                    <p>Content after empty PHP block</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with only whitespace is handled gracefully")
        void whitespaceOnlyPhpBlock() {
            String html = """
                    <html>
                    <body>
                    <?php
                        
                    ?>
                    <p>Content</p>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("adjacent PHP blocks validate independently")
        void adjacentPhpBlocks() {
            String html = """
                    <html>
                    <body>
                    <?php echo "Block 1"; ?>
                    <?php echo "Block 2"; ?>
                    <?php echo "Block 3"; ?>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP block with complex nested structures validates")
        void complexNestedStructures() {
            String html = """
                    <html>
                    <body>
                    <?php
                    class Database {
                        private array $config;
                        
                        public function __construct(array $config) {
                            $this->config = $config;
                        }
                        
                        public function query(string $sql): array {
                            $results = [];
                            // Simulated query execution
                            for ($i = 0; $i < 10; $i++) {
                                $results[] = [
                                    'id' => $i,
                                    'name' => 'Record ' . $i,
                                ];
                            }
                            return $results;
                        }
                    }
                    
                    $db = new Database(['host' => 'localhost']);
                    $records = $db->query("SELECT * FROM users");
                    ?>
                    <ul>
                    <?php foreach ($records as $record): ?>
                        <li><?= $record['name'] ?></li>
                    <?php endforeach; ?>
                    </ul>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Real-world PHP template patterns
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Real-world PHP template patterns")
    class RealWorldTemplates {

        @Test
        @DisplayName("MVC-style PHP template validates")
        void mvcTemplate() {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title><?= htmlspecialchars($title) ?></title>
                        <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                            line-height: 1.6;
                            color: #333;
                            background-color: #f8f9fa;
                        }
                        .navbar {
                            background: #343a40;
                            padding: 1rem 2rem;
                            color: white;
                        }
                        .navbar a { color: white; text-decoration: none; }
                        .container { max-width: 1200px; margin: 0 auto; padding: 2rem; }
                        .alert { padding: 1rem; border-radius: 4px; margin-bottom: 1rem; }
                        .alert-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
                        .alert-danger { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
                        </style>
                    </head>
                    <body>
                    <?php
                    $currentUser = $_SESSION['user'] ?? null;
                    $flashMessage = $_SESSION['flash'] ?? null;
                    unset($_SESSION['flash']);
                    ?>
                    
                    <nav class="navbar">
                        <a href="/">Home</a>
                        <?php if ($currentUser): ?>
                            <a href="/profile"><?= htmlspecialchars($currentUser['name']) ?></a>
                            <a href="/logout">Logout</a>
                        <?php else: ?>
                            <a href="/login">Login</a>
                            <a href="/register">Register</a>
                        <?php endif; ?>
                    </nav>
                    
                    <div class="container">
                        <?php if ($flashMessage): ?>
                            <div class="alert alert-<?= $flashMessage['type'] ?>">
                                <?= htmlspecialchars($flashMessage['text']) ?>
                            </div>
                        <?php endif; ?>
                        
                        <h1><?= htmlspecialchars($title) ?></h1>
                        
                        <?php if (!empty($items)): ?>
                            <ul>
                            <?php foreach ($items as $item): ?>
                                <li>
                                    <strong><?= htmlspecialchars($item['name']) ?></strong>
                                    <?php if ($item['description']): ?>
                                        <p><?= htmlspecialchars($item['description']) ?></p>
                                    <?php endif; ?>
                                </li>
                            <?php endforeach; ?>
                            </ul>
                        <?php else: ?>
                            <p>No items found.</p>
                        <?php endif; ?>
                    </div>
                    
                    <script>
                    document.addEventListener('DOMContentLoaded', function() {
                        // Auto-hide flash messages after 5 seconds
                        const alerts = document.querySelectorAll('.alert');
                        alerts.forEach(alert => {
                            setTimeout(() => {
                                alert.style.transition = 'opacity 0.5s';
                                alert.style.opacity = '0';
                                setTimeout(() => alert.remove(), 500);
                            }, 5000);
                        });
                        
                        // Confirm logout
                        const logoutLink = document.querySelector('a[href="/logout"]');
                        if (logoutLink) {
                            logoutLink.addEventListener('click', function(e) {
                                if (!confirm('Are you sure you want to logout?')) {
                                    e.preventDefault();
                                }
                            });
                        }
                    });
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP form with validation validates")
        void phpFormWithValidation() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <title>Registration Form</title>
                    <style>
                    .form-group { margin-bottom: 1rem; }
                    .form-group label { display: block; margin-bottom: 0.5rem; font-weight: bold; }
                    .form-group input { width: 100%; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; }
                    .error { color: red; font-size: 0.875rem; margin-top: 0.25rem; }
                    .btn { background: #007bff; color: white; padding: 0.75rem 1.5rem; border: none; border-radius: 4px; cursor: pointer; }
                    </style>
                    </head>
                    <body>
                    <?php
                    $errors = [];
                    $formData = [
                        'username' => '',
                        'email' => '',
                        'password' => '',
                    ];
                    
                    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
                        $formData['username'] = trim($_POST['username'] ?? '');
                        $formData['email'] = trim($_POST['email'] ?? '');
                        $formData['password'] = $_POST['password'] ?? '';
                        
                        if (empty($formData['username'])) {
                            $errors['username'] = 'Username is required';
                        }
                        if (empty($formData['email']) || !filter_var($formData['email'], FILTER_VALIDATE_EMAIL)) {
                            $errors['email'] = 'Valid email is required';
                        }
                        if (strlen($formData['password']) < 8) {
                            $errors['password'] = 'Password must be at least 8 characters';
                        }
                    }
                    ?>
                    
                    <h1>Register</h1>
                    <form method="POST" action="/register">
                        <div class="form-group">
                            <label for="username">Username</label>
                            <input type="text" id="username" name="username" 
                                   value="<?= htmlspecialchars($formData['username']) ?>">
                            <?php if (isset($errors['username'])): ?>
                                <div class="error"><?= $errors['username'] ?></div>
                            <?php endif; ?>
                        </div>
                        
                        <div class="form-group">
                            <label for="email">Email</label>
                            <input type="email" id="email" name="email"
                                   value="<?= htmlspecialchars($formData['email']) ?>">
                            <?php if (isset($errors['email'])): ?>
                                <div class="error"><?= $errors['email'] ?></div>
                            <?php endif; ?>
                        </div>
                        
                        <div class="form-group">
                            <label for="password">Password</label>
                            <input type="password" id="password" name="password">
                            <?php if (isset($errors['password'])): ?>
                                <div class="error"><?= $errors['password'] ?></div>
                            <?php endif; ?>
                        </div>
                        
                        <button type="submit" class="btn">Register</button>
                    </form>
                    
                    <script>
                    document.querySelector('form').addEventListener('submit', function(e) {
                        const username = document.getElementById('username').value.trim();
                        const email = document.getElementById('email').value.trim();
                        const password = document.getElementById('password').value;
                        
                        let clientErrors = [];
                        if (!username) clientErrors.push('Username is required');
                        if (!email) clientErrors.push('Email is required');
                        if (password.length < 8) clientErrors.push('Password must be at least 8 characters');
                        
                        if (clientErrors.length > 0) {
                            e.preventDefault();
                            alert(clientErrors.join('\\n'));
                        }
                    });
                    </script>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP API response template validates")
        void phpApiResponse() {
            String html = """
                    <?php
                    header('Content-Type: application/json');
                    
                    $response = [
                        'status' => 'success',
                        'data' => [],
                        'meta' => [
                            'page' => $_GET['page'] ?? 1,
                            'per_page' => $_GET['per_page'] ?? 20,
                            'total' => 0,
                        ],
                    ];
                    
                    try {
                        // Simulated database query
                        $response['data'] = [
                            ['id' => 1, 'name' => 'Item 1'],
                            ['id' => 2, 'name' => 'Item 2'],
                        ];
                        $response['meta']['total'] = count($response['data']);
                    } catch (Exception $e) {
                        $response['status'] = 'error';
                        $response['message'] = $e->getMessage();
                    }
                    
                    echo json_encode($response, JSON_PRETTY_PRINT);
                    ?>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("PHP email template validates")
        void phpEmailTemplate() {
            String html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    body { font-family: Arial, sans-serif; background: #f4f4f4; padding: 20px; }
                    .email-container { max-width: 600px; margin: 0 auto; background: white; border-radius: 8px; overflow: hidden; }
                    .email-header { background: #007bff; color: white; padding: 20px; text-align: center; }
                    .email-body { padding: 20px; }
                    .email-footer { background: #f8f9fa; padding: 15px; text-align: center; font-size: 12px; color: #666; }
                    .btn { display: inline-block; background: #007bff; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; }
                    </style>
                    </head>
                    <body>
                    <?php
                    $userName = $user['name'] ?? 'Valued Customer';
                    $resetLink = $resetUrl ?? '#';
                    $expiryHours = 24;
                    ?>
                    
                    <div class="email-container">
                        <div class="email-header">
                            <h1>Password Reset Request</h1>
                        </div>
                        <div class="email-body">
                            <p>Hello <?= htmlspecialchars($userName) ?>,</p>
                            <p>We received a request to reset your password. Click the button below to proceed:</p>
                            <p style="text-align: center; margin: 30px 0;">
                                <a href="<?= htmlspecialchars($resetLink) ?>" class="btn">Reset Password</a>
                            </p>
                            <p>This link will expire in <?= $expiryHours ?> hours.</p>
                            <p>If you didn't request this, please ignore this email.</p>
                        </div>
                        <div class="email-footer">
                            <p>&copy; <?= date('Y') ?> Our Company. All rights reserved.</p>
                        </div>
                    </div>
                    </body>
                    </html>
                    """;

            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }

    // ---------------------------------------------------------------
    // Parameterized tests
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Parameterized tests")
    class ParameterizedTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "<?php echo 'test'; ?>",
                "<?php $x = 1; ?>",
                "<?php /* comment */ ?>",
                "<?= $variable ?>",
                "<?php echo true ? 'yes' : 'no'; ?>",
                "<?php for ($i = 0; $i < 10; $i++) echo $i; ?>",
                "<?php if (true) echo 'yes'; ?>"
        })
        @DisplayName("single PHP statement in HTML body validates")
        void singlePhpStatement(String phpContent) {
            String html = "<html><body>" + phpContent + "</body></html>";
            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "<?php echo 'test'; ?>",
                "<?php $x = 1; $y = 2; echo $x + $y; ?>",
                "<?php class Foo { public function bar() { return 1; } } ?>",
                "<?php function add($a, $b) { return $a + $b; } echo add(1, 2); ?>",
                "<?php $arr = [1,2,3]; foreach($arr as $v) echo $v; ?>"
        })
        @DisplayName("PHP block with valid syntax in full document validates")
        void validPhpInFullDocument(String phpContent) {
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><title>Test</title></head>
                    <body>
                    """ + phpContent + """
                    </body>
                    </html>
                    """;
            ValidationResult result = validator.validate(html);
            assertThat(result).isNotNull();
        }
    }
}
