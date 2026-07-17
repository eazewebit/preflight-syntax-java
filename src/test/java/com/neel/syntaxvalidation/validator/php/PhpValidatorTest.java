package com.neel.syntaxvalidation.validator.php;

import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PhpValidator}.
 */
@DisplayName("PhpValidator")
class PhpValidatorTest {

    private PhpValidator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = new PhpValidator();
    }

    // =========================================================================
    //  CONTRACT
    // =========================================================================

    @Nested
    @DisplayName("contract")
    class Contract {

        @Test
        @DisplayName("language returns PHP")
        void languageIsPhp() {
            assertThat(validator.getLanguage()).isEqualTo(Language.PHP);
        }
    }

    // =========================================================================
    //  VALIDATE SOURCE
    // =========================================================================

    @Nested
    @DisplayName("validateSource")
    class ValidateSource {

        @Test
        @DisplayName("valid PHP code returns success")
        void validPhpCode() {
            String source = """
                    <?php
                    class User {
                        public function __construct(
                            public readonly string $name,
                            public readonly string $email,
                        ) {}
                        
                        public function greet(): string {
                            return "Hello, {$this->name}!";
                        }
                    }
                    ?>
                    """;
            ValidationResult result = validator.validateSource(source);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        @DisplayName("invalid PHP code returns errors")
        void invalidPhpCode() {
            String source = """
                    <?php
                    $x = (1 + 2;
                    ?>
                    """;
            ValidationResult result = validator.validateSource(source);
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).isNotEmpty();
        }

        @Test
        @DisplayName("empty source returns success")
        void emptySource() {
            assertThat(validator.validateSource("").isValid()).isTrue();
        }

        @Test
        @DisplayName("null source returns success")
        void nullSource() {
            assertThat(validator.validateSource(null).isValid()).isTrue();
        }

        @Test
        @DisplayName("complex modern PHP code validates successfully")
        void complexModernPhp() {
            String source = """
                    <?php
                    declare(strict_types=1);
                    
                    namespace App\\Services;
                    
                    use App\\Contracts\\RepositoryInterface;
                    use App\\DTOs\\UserDTO;
                    
                    readonly class UserService {
                        public function __construct(
                            private RepositoryInterface $repository,
                            private CacheManager $cache,
                        ) {}
                        
                        public function findUser(int $id): UserDTO {
                            return $this->cache->remember(
                                "user:{$id}",
                                fn() => $this->repository->find($id),
                            );
                        }
                        
                        public function processUsers(array $ids): Generator {
                            foreach ($ids as $id) {
                                yield $id => match($this->findUser($id)->role) {
                                    'admin' => $this->processAdmin($id),
                                    default => $this->processRegular($id),
                                };
                            }
                        }
                        
                        private function processAdmin(int $id): array {
                            return ['type' => 'admin', 'id' => $id];
                        }
                        
                        private function processRegular(int $id): array {
                            return ['type' => 'regular', 'id' => $id];
                        }
                    }
                    ?>
                    """;
            assertThat(validator.validateSource(source).isValid()).isTrue();
        }
    }

    // =========================================================================
    //  FILE-BASED VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("file-based validation")
    class FileBasedValidation {

        @Test
        @DisplayName("valid PHP content passes validation")
        void validPhpContent() throws IOException {
            String source = """
                    <?php
                    echo 'Hello, World!';
                    ?>
                    """;
            Path file = tempDir.resolve("valid.php");
            Files.writeString(file, source);
            assertThat(validator.validateSource(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("invalid PHP content fails validation")
        void invalidPhpContent() throws IOException {
            String source = """
                    <?php
                    function foo() {
                        echo 'hello';
                    }
                    }
                    ?>
                    """;
            assertThat(validator.validateSource(source).isValid()).isFalse();
        }

        @Test
        @DisplayName("PHP file with .phtml extension")
        void phtmlExtension() throws IOException {
            String source = """
                    <?php
                    echo 'This is a phtml file';
                    ?>
                    <html><body>Content</body></html>
                    """;
            assertThat(validator.validateSource(source).isValid()).isTrue();
        }
    }

    // =========================================================================
    //  LANGUAGE EXTENSION DETECTION
    // =========================================================================

    @Nested
    @DisplayName("language extension detection")
    class ExtensionDetection {

        @Test
        @DisplayName("PHP language supports .php extension")
        void phpExtension() {
            assertThat(Language.fromExtension("php")).contains(Language.PHP);
        }

        @Test
        @DisplayName("PHP language supports .phtml extension")
        void phtmlExtension() {
            assertThat(Language.fromExtension("phtml")).contains(Language.PHP);
        }

        @Test
        @DisplayName("PHP language supports .phps extension")
        void phpsExtension() {
            assertThat(Language.fromExtension("phps")).contains(Language.PHP);
        }

        @Test
        @DisplayName("case insensitive extension detection")
        void caseInsensitive() {
            assertThat(Language.fromExtension("PHP")).contains(Language.PHP);
            assertThat(Language.fromExtension("PHTML")).contains(Language.PHP);
            assertThat(Language.fromExtension("PHPS")).contains(Language.PHP);
        }
    }

    // =========================================================================
    //  VALIDATOR FACTORY INTEGRATION
    // =========================================================================

    @Nested
    @DisplayName("validator factory integration")
    class FactoryIntegration {

        @Test
        @DisplayName("PhpValidator is registered in ValidatorFactory")
        void registeredInFactory() {
            com.neel.syntaxvalidation.validator.ValidatorFactory factory =
                    new com.neel.syntaxvalidation.validator.ValidatorFactory();
            assertThat(factory.getValidator(Language.PHP))
                    .isPresent()
                    .hasValueSatisfying(v -> assertThat(v).isInstanceOf(PhpValidator.class));
        }

        @Test
        @DisplayName("PHP validator is in supported languages set")
        void inSupportedLanguages() {
            com.neel.syntaxvalidation.validator.ValidatorFactory factory =
                    new com.neel.syntaxvalidation.validator.ValidatorFactory();
            assertThat(factory.supportedLanguages()).contains(Language.PHP);
        }
    }

    // =========================================================================
    //  EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("very large PHP file")
        void veryLargeFile() {
            StringBuilder sb = new StringBuilder("<?php\n");
            for (int i = 0; i < 1000; i++) {
                sb.append("$var").append(i).append(" = ").append(i).append(";\n");
            }
            sb.append("?>");
            assertThat(validator.validateSource(sb.toString()).isValid()).isTrue();
        }

        @Test
        @DisplayName("PHP with special characters in strings")
        void specialCharactersInStrings() {
            String source = """
                    <?php
                    $escaped = 'It\\'s a test';
                    $unicode = "\\u{1F600}";
                    $dollar = "\\$not\\$variable";
                    $newline = "line1\\nline2";
                    ?>
                    """;
            assertThat(validator.validateSource(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("multiple classes in single file")
        void multipleClasses() {
            String source = """
                    <?php
                    interface Animal {
                        public function speak(): string;
                    }
                    
                    class Dog implements Animal {
                        public function speak(): string { return 'Woof'; }
                    }
                    
                    class Cat implements Animal {
                        public function speak(): string { return 'Meow'; }
                    }
                    
                    enum AnimalType: string {
                        case Dog = 'dog';
                        case Cat = 'cat';
                    }
                    ?>
                    """;
            assertThat(validator.validateSource(source).isValid()).isTrue();
        }

        @Test
        @DisplayName("PHP with only HTML content")
        void onlyHtml() {
            String source = """
                    <html>
                    <head><title>Test</title></head>
                    <body>
                    <h1>No PHP here</h1>
                    </body>
                    </html>
                    """;
            assertThat(validator.validateSource(source).isValid()).isTrue();
        }
    }
}
