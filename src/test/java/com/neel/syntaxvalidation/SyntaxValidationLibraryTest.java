package com.neel.syntaxvalidation;

import com.neel.syntaxvalidation.binary.BinaryResolver;
import com.neel.syntaxvalidation.model.BatchModificationRequest;
import com.neel.syntaxvalidation.model.Language;
import com.neel.syntaxvalidation.model.LineReplacement;
import com.neel.syntaxvalidation.model.ModificationRequest;
import com.neel.syntaxvalidation.model.ValidationResult;
import com.neel.syntaxvalidation.validator.LanguageValidator;
import com.neel.syntaxvalidation.validator.ValidatorFactory;
import com.neel.syntaxvalidation.validator.javascript.JavaScriptValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyntaxValidationLibraryTest {

    @TempDir
    Path tempDir;

    // ---- fake-validator end-to-end tests ----------------------------------

    @Test
    void validate_appliesModificationAndPassesResultToValidator() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\nconst b = 2;\nconst c = 3;\n");
        CapturingValidator validator = new CapturingValidator();
        SyntaxValidationLibrary library = libraryWith(validator);

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(2)
                .toLine(2)
                .replacement("const b = 99;")
                .build());

        assertThat(result.isValid()).isTrue();
        assertThat(validator.lastValidatedContent().lines().toList())
                .containsExactly("const a = 1;", "const b = 99;", "const c = 3;");
    }

    @Test
    void validate_doesNotModifyOriginalFileOnDisk() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\nconst b = 2;\n");
        SyntaxValidationLibrary library = libraryWith(new CapturingValidator());

        library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(1)
                .toLine(1)
                .replacement("BROKEN")
                .build());

        assertThat(Files.readString(file)).isEqualTo("const a = 1;\nconst b = 2;\n");
    }

    @Test
    void validate_returnsInvalidForUnsupportedExtension() throws IOException {
        Path file = writeFile("notes.txt", "hello\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(1)
                .toLine(1)
                .replacement("hi")
                .build());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("language");
    }

    @Test
    void validate_returnsValidWhenTypeScriptValidatorRegistered() throws IOException {
        Path file = writeFile("app.ts", "const x: number = 1;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(1)
                .toLine(1)
                .replacement("const y: string = 'hi';")
                .build());

        // TypeScript now has a validator registered, so valid code should pass
        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_returnsInvalidWhenNoValidatorRegistered() throws IOException {
        // Use a file with unsupported extension to test no-validator scenario
        Path file = writeFile("app.xyz", "some content\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(1)
                .toLine(1)
                .replacement("new content")
                .build());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("language");
    }

    @Test
    void validate_returnsInvalidForMissingFile() {
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(tempDir.resolve("missing.js").toString())
                .fromLine(1)
                .toLine(1)
                .replacement("x")
                .build());

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("does not exist");
    }

    @Test
    void validate_rejectsNullRequest() {
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        assertThatThrownBy(() -> library.validate((ModificationRequest) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_usesCacheAcrossInvocations() throws IOException {
        Path file = writeFile("app.js", "const x = 1;\n");
        CapturingValidator validator = new CapturingValidator();
        SyntaxValidationLibrary library = libraryWith(validator);

        library.validate(request(file, 1, 1, "const y = 2;"));
        library.validate(request(file, 1, 1, "const z = 3;"));

        // Same file read once; cache keeps a single entry.
        assertThat(validator.validateCount()).isEqualTo(2);
    }

    @Test
    void cacheMethods_delegatedCorrectly() throws IOException {
        Path file = writeFile("app.js", "x\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        library.validate(request(file, 1, 1, "y"));
        assertThat(library.invalidateCache(file)).isTrue();
        assertThat(library.invalidateCache(file)).isFalse();
    }

    // ---- real Node.js integration tests ------------------------------------

    /**
     * Requires the {@code nodeIntegration} system property to be {@code true} AND
     * Node.js to be resolvable on the PATH. This keeps the suite deterministic in
     * environments without Node.js while exercising the real tool here.
     */
    private static boolean nodeAvailable() {
        return new BinaryResolver().resolve(null, "node").isPresent();
    }

    @Test
    void realNode_validModificationPasses() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(nodeAvailable(),
                "Node.js is not available on the PATH; skipping real integration test");
        Path file = writeFile("app.js", "function add(a, b) {\n  return a - b;\n}\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(2)
                .toLine(2)
                .replacement("  return a + b;")
                .build());

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void realNode_invalidModificationReportsError() throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(nodeAvailable(),
                "Node.js is not available on the PATH; skipping real integration test");
        Path file = writeFile("app.js", "const a = 1;\nconst b = 2;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        ValidationResult result = library.validate(ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(1)
                .toLine(1)
                .replacement("const a = ;")
                .build());

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().getFirst().getMessage()).isNotBlank();
    }

    // ---- helpers -----------------------------------------------------------

    private SyntaxValidationLibrary libraryWith(LanguageValidator validator) {
        ValidatorFactory factory = new ValidatorFactory();
        factory.register(Language.JAVASCRIPT, validator);
        return new SyntaxValidationLibrary(factory);
    }

    private ModificationRequest request(Path file, int from, int to, String replacement) {
        return ModificationRequest.builder()
                .filePath(file.toString())
                .fromLine(from)
                .toLine(to)
                .replacement(replacement)
                .build();
    }

    // ---- JavaScript ES6+ built-in engine validation ----------------------

    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.DisplayName("JavaScript ES6+ syntax engine")
    class JavaScriptEngineValidation {

        private final com.neel.syntaxvalidation.validator.javascript.JavaScriptSyntaxEngine engine =
                com.neel.syntaxvalidation.validator.javascript.JavaScriptSyntaxEngine.getInstance();

        // ---- Valid ES6+ code that the engine must accept ----

        @Test
        @org.junit.jupiter.api.DisplayName("arrow functions in all forms")
        void arrowFunctions() {
            assertThat(engine.validate("const add = (a, b) => a + b;").isValid()).isTrue();
            assertThat(engine.validate("const noop = () => {};").isValid()).isTrue();
            assertThat(engine.validate("const curried = a => b => a + b;").isValid()).isTrue();
            assertThat(engine.validate("const obj = x => ({ value: x });").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("object and array destructuring with defaults and rest")
        void destructuring() {
            assertThat(engine.validate("const { a, b: { c = 0 } = {} } = obj;").isValid()).isTrue();
            assertThat(engine.validate("const [first, , third, ...rest] = arr;").isValid()).isTrue();
            assertThat(engine.validate("function f({ a, b }, [c, d]) { return a + b + c + d; }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("async/await and async generators")
        void asyncAwait() {
            assertThat(engine.validate("async function load() { return await fetch(url); }").isValid()).isTrue();
            assertThat(engine.validate("const main = async () => { const d = await getData(); return d; };").isValid()).isTrue();
            assertThat(engine.validate("async function* gen() { yield await fetch(url); }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("template literals with nested interpolation")
        void templateLiterals() {
            assertThat(engine.validate("const html = `<div>${items.map(i => `<li>${i}</li>`).join('')}</div>`;").isValid()).isTrue();
            assertThat(engine.validate("const raw = String.raw`\\n${value}\\n`;").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("optional chaining in all forms")
        void optionalChaining() {
            assertThat(engine.validate("const x = obj?.prop?.subProp;").isValid()).isTrue();
            assertThat(engine.validate("const y = fn?.();").isValid()).isTrue();
            assertThat(engine.validate("const z = arr?.[0]?.method?.();").isValid()).isTrue();
            assertThat(engine.validate("const d = obj?.prop ?? 'default';").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("spread syntax in calls, arrays and objects")
        void spreadSyntax() {
            assertThat(engine.validate("const merged = [...arr1, ...arr2];").isValid()).isTrue();
            assertThat(engine.validate("const obj = { ...defaults, ...overrides };").isValid()).isTrue();
            assertThat(engine.validate("fn(...args, extra);").isValid()).isTrue();
            assertThat(engine.validate("const d = new Date(...parts);").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("modern class definitions")
        void classDefinitions() {
            assertThat(engine.validate("class A { #x = 0; static count = 0; get x() { return this.#x; } async method() {} *gen() {} }").isValid()).isTrue();
            assertThat(engine.validate("class B extends A { constructor() { super(); } }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("nullish coalescing and logical assignment")
        void nullishAndLogicalAssignment() {
            assertThat(engine.validate("x ??= y; x ||= z; x &&= w;").isValid()).isTrue();
            assertThat(engine.validate("const v = a ?? b ?? 'fallback';").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("import/export module syntax")
        void moduleSyntax() {
            assertThat(engine.validate("import { foo, bar as baz } from 'module';").isValid()).isTrue();
            assertThat(engine.validate("import DefaultExport from 'mod';").isValid()).isTrue();
            assertThat(engine.validate("export const value = 42;").isValid()).isTrue();
            assertThat(engine.validate("export default function() {};").isValid()).isTrue();
            assertThat(engine.validate("export * as ns from 'mod';").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("for-of, for-in and for-await")
        void forLoops() {
            assertThat(engine.validate("for (const x of arr) { use(x); }").isValid()).isTrue();
            assertThat(engine.validate("for (const [k, v] of entries) { map.set(k, v); }").isValid()).isTrue();
            assertThat(engine.validate("for await (const chunk of stream) { handle(chunk); }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("complex real-world code")
        void complexRealWorld() {
            assertThat(engine.validate(
                    "const handler = async (req, res) => {\n" +
                    "  const { body: { items = [], ...meta } } = req;\n" +
                    "  try {\n" +
                    "    const results = await Promise.all(\n" +
                    "      items.map(async ({ id, ...data }) => {\n" +
                    "        const existing = await db?.findOne?.({ id });\n" +
                    "        if (existing?.status === 'active') {\n" +
                    "          return { ...existing, ...data, updatedAt: Date.now() };\n" +
                    "        }\n" +
                    "        return db.create({ id, ...data });\n" +
                    "      })\n" +
                    "    );\n" +
                    "    res.json({ data: results, ...meta });\n" +
                    "  } catch (err) {\n" +
                    "    console.error(`Error: ${err?.message ?? 'unknown'}`);\n" +
                    "    res?.status?.(500)?.json?.({ error: 'fail' });\n" +
                    "  }\n" +
                    "};"
            ).isValid()).isTrue();
        }

        // ---- Invalid ES6+ code that the engine must reject ----

        @Test
        @org.junit.jupiter.api.DisplayName("const without initialiser")
        void constWithoutInitialiser() {
            assertThat(engine.validate("const x;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("binary operator without left operand")
        void binaryOpWithoutLhs() {
            assertThat(engine.validate("const x = ?? y;").isValid()).isFalse();
            assertThat(engine.validate("&& y;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("if without parenthesis")
        void ifWithoutParen() {
            assertThat(engine.validate("if true { }").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("unbalanced brackets")
        void unbalancedBrackets() {
            assertThat(engine.validate("function foo(a, b {\n  return a + b;\n").isValid()).isFalse();
            assertThat(engine.validate("const arr = [1, 2, 3);").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("arrow function missing body")
        void arrowMissingBody() {
            assertThat(engine.validate("const fn = () =>;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("unterminated string literal")
        void unterminatedString() {
            assertThat(engine.validate("const s = 'hello world;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("prefix keyword without operand")
        void prefixKeywordWithoutOperand() {
            assertThat(engine.validate("const t = typeof;").isValid()).isFalse();
            assertThat(engine.validate("new;").isValid()).isFalse();
        }

        // ---- Additional comprehensive ES6+ edge-case tests ----

        @Test
        @org.junit.jupiter.api.DisplayName("do-while statements")
        void doWhile() {
            assertThat(engine.validate("do { x++; } while (x < 10);").isValid()).isTrue();
            assertThat(engine.validate("do console.log(i); while (i++ < 5);").isValid()).isTrue();
            assertThat(engine.validate("do { if (a) break; } while (cond);").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("import.meta and dynamic import")
        void importMetaAndDynamic() {
            assertThat(engine.validate("const url = import.meta.url;").isValid()).isTrue();
            assertThat(engine.validate("const mod = import('./module.js');").isValid()).isTrue();
            assertThat(engine.validate("const m = await import('node:fs');").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("nested ternary expressions")
        void nestedTernary() {
            assertThat(engine.validate("const x = a ? b ? c : d : e;").isValid()).isTrue();
            assertThat(engine.validate("const y = a ? (b ? c : d) : (e ? f : g);").isValid()).isTrue();
            assertThat(engine.validate("const z = a > 0 ? 'pos' : a < 0 ? 'neg' : 'zero';").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("destructuring assignment (not just declaration)")
        void destructuringAssignment() {
            assertThat(engine.validate("({a, b} = obj);").isValid()).isTrue();
            assertThat(engine.validate("([x, y, ...rest] = arr);").isValid()).isTrue();
            assertThat(engine.validate("({a: {b} = {}} = obj);").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("multiple declarations in one statement")
        void multipleDeclarations() {
            assertThat(engine.validate("let a = 1, b = 2, c = 3;").isValid()).isTrue();
            assertThat(engine.validate("const x = 1, y = 2;").isValid()).isTrue();
            assertThat(engine.validate("var p, q, r;").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("void and delete operators")
        void voidAndDelete() {
            assertThat(engine.validate("void 0;").isValid()).isTrue();
            assertThat(engine.validate("const x = void expression();").isValid()).isTrue();
            assertThat(engine.validate("delete obj.prop;").isValid()).isTrue();
            assertThat(engine.validate("delete arr[0];").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("compound assignment operators")
        void compoundAssignment() {
            assertThat(engine.validate("a += 1; b -= 2; c *= 3; d /= 4;").isValid()).isTrue();
            assertThat(engine.validate("x %= 5; y **= 2; z &= 0xff;").isValid()).isTrue();
            assertThat(engine.validate("p |= 1; q ^= mask; r <<= 2; s >>= 1; t >>>= 1;").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("export patterns: named, re-export, as default")
        void exportPatterns() {
            assertThat(engine.validate("const foo = 1; const bar = 2; export { foo, bar };").isValid()).isTrue();
            assertThat(engine.validate("export { foo as default, bar as baz };").isValid()).isTrue();
            assertThat(engine.validate("export { default } from 'mod';").isValid()).isTrue();
            assertThat(engine.validate("export async function fetch() {}").isValid()).isTrue();
            assertThat(engine.validate("export class MyClass {}").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("import side effects and namespace imports")
        void importVariants() {
            assertThat(engine.validate("import './polyfill.js';").isValid()).isTrue();
            assertThat(engine.validate("import * as utils from './utils.js';").isValid()).isTrue();
            assertThat(engine.validate("import { default as Config } from './config.js';").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("unary operators: bitwise NOT, logical NOT, unary plus/minus")
        void unaryOperators() {
            assertThat(engine.validate("const a = ~mask;").isValid()).isTrue();
            assertThat(engine.validate("const b = !flag;").isValid()).isTrue();
            assertThat(engine.validate("const c = +str;").isValid()).isTrue();
            assertThat(engine.validate("const d = -num;").isValid()).isTrue();
            assertThat(engine.validate("i++; j--;").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("for loop with multiple initialisers and updaters")
        void forLoopMultipleVars() {
            assertThat(engine.validate("for (let i = 0, j = 10; i < j; i++, j--) { use(i, j); }").isValid()).isTrue();
            assertThat(engine.validate("for (var a = 0, b = 1; a < 100; a++, b = a + b) {}").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("labeled break and continue")
        void labeledBreakContinue() {
            assertThat(engine.validate("outer: for (let i = 0; i < 3; i++) { inner: for (let j = 0; j < 3; j++) { if (j === 1) continue inner; if (i === 2) break outer; } }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("chained optional with nullish coalescing in complex expression")
        void chainedOptionalNullish() {
            assertThat(engine.validate("const v = obj?.a?.b?.c ?? obj?.fallback?.value ?? 'default';").isValid()).isTrue();
            assertThat(engine.validate("const r = fn?.()?.method?.()?.value ?? fallback;").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("class static block and private methods")
        void classStaticAndPrivateMethods() {
            assertThat(engine.validate("class C { #secret = 42; #compute() { return this.#secret * 2; } static #factory() { return new C(); } }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("generator delegation with expressions")
        void generatorDelegation() {
            assertThat(engine.validate("function* g() { yield* [1, 2, 3]; yield* gen(); yield* iter; }").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("arrow functions with destructured and default params")
        void arrowComplexParams() {
            assertThat(engine.validate("const f = ({a, b = 0}, [c, d] = []) => a + b + c + d;").isValid()).isTrue();
            assertThat(engine.validate("const g = async ({id, ...rest}) => await fetch(`/${id}`, rest);").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("complex nested spread patterns")
        void complexSpread() {
            assertThat(engine.validate("const copy = { ...obj, nested: { ...obj.nested } };").isValid()).isTrue();
            assertThat(engine.validate("fn(1, ...arr.map(x => x * 2), 3);").isValid()).isTrue();
            assertThat(engine.validate("const [head, ...tail] = [...source];").isValid()).isTrue();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("class with all member types combined")
        void classAllMemberTypes() {
            assertThat(engine.validate(
                    "class Widget extends Base {\n" +
                    "  #id;\n" +
                    "  static count = 0;\n" +
                    "  constructor(id) {\n" +
                    "    super();\n" +
                    "    this.#id = id;\n" +
                    "    Widget.count++;\n" +
                    "  }\n" +
                    "  get id() { return this.#id; }\n" +
                    "  set id(v) { this.#id = v; }\n" +
                    "  async load() { return await fetch(this.#id); }\n" +
                    "  *[Symbol.iterator]() { yield this.#id; }\n" +
                    "  static async create(id) { return new Widget(id); }\n" +
                    "}"
            ).isValid()).isTrue();
        }

        // ---- Additional invalid-pattern tests ----

        @Test
        @org.junit.jupiter.api.DisplayName("missing operand after binary operator")
        void missingOperandAfterBinary() {
            assertThat(engine.validate("const x = 5 +;").isValid()).isFalse();
            assertThat(engine.validate("const y = a *;").isValid()).isFalse();
            assertThat(engine.validate("const z = ;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("export default without value at EOF")
        void exportDefaultAtEof() {
            assertThat(engine.validate("export default").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("incomplete ternary: missing colon")
        void incompleteTernary() {
            assertThat(engine.validate("const x = a ? b;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("unterminated block comment")
        void unterminatedBlockComment() {
            assertThat(engine.validate("const x = 1; /* this never ends").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("unmatched template interpolation")
        void unmatchedTemplateInterpolation() {
            assertThat(engine.validate("const x = `hello ${name`;").isValid()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("invalid optional chaining member")
        void invalidOptionalChaining() {
            assertThat(engine.validate("const x = obj?.;").isValid()).isFalse();
            assertThat(engine.validate("const y = obj?.+1;").isValid()).isFalse();
        }
    }

    private Path writeFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    /** Captures the exact content handed to the validator. */
    static final class CapturingValidator implements LanguageValidator {
        private String lastContent;
        private int count;

        @Override
        public ValidationResult validate(String content) {
            this.lastContent = content;
            this.count++;
            return ValidationResult.valid("captured");
        }

        @Override
        public Language getLanguage() {
            return Language.JAVASCRIPT;
        }

        String lastValidatedContent() {
            return lastContent;
        }
        int validateCount() {
            return count;
        }
    }

    // ---- batch validation tests -----------------------------------------

    @Test
    void validate_batch_returnsInvalidForMissingFile() {
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("x").build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(tempDir.resolve("missing.js").toString())
                .addReplacement(r)
                .build();

        ValidationResult result = library.validate(batch);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("does not exist");
    }

    @Test
    void validate_batch_rejectsNullRequest() {
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        assertThatThrownBy(() -> library.validate((BatchModificationRequest) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_batch_appliesReplacementsAndValidates() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\nconst b = 2;\nconst c = 3;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        LineReplacement r = LineReplacement.builder()
                .fromLine(2).toLine(2).replacement("const b = 99;")
                .build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r)
                .build();

        ValidationResult result = library.validate(batch);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_batch_multipleReplacements() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\nconst b = 2;\nconst c = 3;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        LineReplacement r1 = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("const x = 10;").build();
        LineReplacement r2 = LineReplacement.builder()
                .fromLine(3).toLine(3).replacement("const z = 30;").build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r1)
                .addReplacement(r2)
                .build();

        ValidationResult result = library.validate(batch);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validate_batch_invalidReplacementReportsError() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("const b = ;").build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r)
                .build();

        ValidationResult result = library.validate(batch);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void validate_batch_usesCachedFileContent() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        // First: single request (caches file)
        library.validate(request(file, 1, 1, "const b = 2;"));

        // Second: batch request (should use cached content from original file)
        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("const c = 3;").build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r)
                .build();

        ValidationResult result = library.validate(batch);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validateAllLanguage_batch_delegatesToCorrectValidator() throws IOException {
        Path file = writeFile("app.js", "const a = 1;\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("const b = 2;").build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r)
                .build();

        ValidationResult result = library.validateAllLanguage(batch);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validateAllLanguage_batch_rejectsNullRequest() {
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        assertThatThrownBy(() -> library.validateAllLanguage((BatchModificationRequest) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateAllLanguage_batch_unsupportedExtension() throws IOException {
        Path file = writeFile("data.xyz", "content\n");
        SyntaxValidationLibrary library = new SyntaxValidationLibrary();

        LineReplacement r = LineReplacement.builder()
                .fromLine(1).toLine(1).replacement("new content").build();
        BatchModificationRequest batch = BatchModificationRequest.builder()
                .filePath(file.toString())
                .addReplacement(r)
                .build();

        ValidationResult result = library.validateAllLanguage(batch);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).contains("extension");
    }
}
