package com.neel.syntaxvalidation.validator.javascript;

import com.neel.syntaxvalidation.model.ValidationError;
import com.neel.syntaxvalidation.model.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for the pure-Java {@link JavaScriptSyntaxEngine}.
 *
 * <p>These tests exercise a wide spectrum of modern JavaScript (ES6+)
 * constructs, verifying that valid code passes and that common syntax errors
 * are caught with accurate, verbose diagnostics.
 */
@DisplayName("JavaScriptSyntaxEngine")
class JavaScriptSyntaxEngineTest {

    private final JavaScriptSyntaxEngine engine = JavaScriptSyntaxEngine.getInstance();

    private ValidationResult validate(String source) {
        return engine.validate(source);
    }

    // ==================================================================
    //  Valid modern JavaScript — must pass without errors
    // ==================================================================

    @Nested
    @DisplayName("valid ES6+ syntax")
    class ValidSyntax {

        @Test
        @DisplayName("arrow functions (expression and block body)")
        void arrowFunctions() {
            assertThat(validate("const add = (a, b) => a + b;").isValid()).isTrue();
            assertThat(validate("const greet = name => `Hello, ${name}!`;").isValid()).isTrue();
            assertThat(validate("const noop = () => {};").isValid()).isTrue();
            assertThat(validate("const curried = a => b => c => a + b + c;").isValid()).isTrue();
            assertThat(validate("const asyncFn = async () => await fetch('/api');").isValid()).isTrue();
            assertThat(validate("const nested = () => (x) => ({ key: x });").isValid()).isTrue();
        }

        @Test
        @DisplayName("object and array destructuring")
        void destructuring() {
            assertThat(validate("const { a, b, c } = obj;").isValid()).isTrue();
            assertThat(validate("const [first, , third] = arr;").isValid()).isTrue();
            assertThat(validate("const { name: n = 'default', ...rest } = data;").isValid()).isTrue();
            assertThat(validate("const [{ x, y }, { z }] = pairs;").isValid()).isTrue();
            assertThat(validate("function f({ a, b }, [c, d]) { return a + b + c + d; }").isValid()).isTrue();
            assertThat(validate("const { deeply: { nested: { value } } } = obj;").isValid()).isTrue();
        }

        @Test
        @DisplayName("async/await structures")
        void asyncAwait() {
            assertThat(validate("async function fetchData() { return await fetch(url); }").isValid()).isTrue();
            assertThat(validate("const main = async () => { const data = await getData(); };").isValid()).isTrue();
            assertThat(validate("class Service { async load() { try { return await this.api(); } catch (e) {} } }").isValid()).isTrue();
            assertThat(validate("const result = await promise.then(async r => await r.json());").isValid()).isTrue();
        }

        @Test
        @DisplayName("template literals with interpolation and nesting")
        void templateLiterals() {
            assertThat(validate("const html = `<div>${content}</div>`;").isValid()).isTrue();
            assertThat(validate("const nested = `outer ${`inner ${value}`} done`;").isValid()).isTrue();
            assertThat(validate("const complex = `${obj?.name ?? 'anonymous'}: ${arr.map(x => x * 2).join(',')}`;").isValid()).isTrue();
            assertThat(validate("const tag = sql`SELECT * FROM users WHERE id = ${id}`;").isValid()).isTrue();
            assertThat(validate("const multiline = `\n  line1\n  line2\n`;").isValid()).isTrue();
        }

        @Test
        @DisplayName("optional chaining")
        void optionalChaining() {
            assertThat(validate("const name = user?.profile?.name;").isValid()).isTrue();
            assertThat(validate("const val = obj?.[dynamicKey];").isValid()).isTrue();
            assertThat(validate("const result = fn?.();").isValid()).isTrue();
            assertThat(validate("const prop = obj?.property?.subProperty?.value;").isValid()).isTrue();
            assertThat(validate("const item = arr?.[0]?.id;").isValid()).isTrue();
        }

        @Test
        @DisplayName("spread syntax in various contexts")
        void spreadSyntax() {
            assertThat(validate("const merged = [...arr1, ...arr2];").isValid()).isTrue();
            assertThat(validate("const { a, ...rest } = obj;").isValid()).isTrue();
            assertThat(validate("fn(...args);").isValid()).isTrue();
            assertThat(validate("const copy = { ...original, override: true };").isValid()).isTrue();
            assertThat(validate("function sum(...nums) { return nums.reduce((a, b) => a + b, 0); }").isValid()).isTrue();
            assertThat(validate("new Date(...parts);").isValid()).isTrue();
        }

        @Test
        @DisplayName("modern class definitions")
        void classDefinitions() {
            assertThat(validate("class Animal { constructor(name) { this.name = name; } speak() {} }").isValid()).isTrue();
            assertThat(validate("class Dog extends Animal { #breed; static count = 0; get breed() { return this.#breed; } set breed(v) { this.#breed = v; } }").isValid()).isTrue();
            assertThat(validate("const anon = class { method() {} };").isValid()).isTrue();
            assertThat(validate("class Foo { static #privateStatic() {} #privateMethod() {} async asyncMethod() {} *generatorMethod() {} }").isValid()).isTrue();
        }

        @Test
        @DisplayName("regular expressions")
        void regexLiterals() {
            assertThat(validate("const re = /pattern/gi;").isValid()).isTrue();
            assertThat(validate("const cls = /[abc]+/; const div = a / b / c;").isValid()).isTrue();
            assertThat(validate("const esc = /\\d+\\.\\d+/;").isValid()).isTrue();
            assertThat(validate("const r = /^https?:\\/\\/[\\w.-]+/i;").isValid()).isTrue();
            assertThat(validate("return /regex/.test(str);").isValid()).isTrue();
        }

        @Test
        @DisplayName("nullish coalescing and logical assignment")
        void nullishCoalescing() {
            assertThat(validate("const x = a ?? b ?? c;").isValid()).isTrue();
            assertThat(validate("obj.prop ??= 'default';").isValid()).isTrue();
            assertThat(validate("a ||= b; c &&= d;").isValid()).isTrue();
        }

        @Test
        @DisplayName("exponentiation")
        void exponentiation() {
            assertThat(validate("const sq = 2 ** 10;").isValid()).isTrue();
            assertThat(validate("let x = 5; x **= 2;").isValid()).isTrue();
        }

        @Test
        @DisplayName("number literals in all bases")
        void numberLiterals() {
            assertThat(validate("const hex = 0xFF;").isValid()).isTrue();
            assertThat(validate("const bin = 0b1010;").isValid()).isTrue();
            assertThat(validate("const oct = 0o755;").isValid()).isTrue();
            assertThat(validate("const big = 123456789n;").isValid()).isTrue();
            assertThat(validate("const sep = 1_000_000.5e3;").isValid()).isTrue();
            assertThat(validate("const frac = .5;").isValid()).isTrue();
        }

        @Test
        @DisplayName("import/export module syntax")
        void moduleSyntax() {
            assertThat(validate("import { foo, bar } from 'module';").isValid()).isTrue();
            assertThat(validate("export const value = 42;").isValid()).isTrue();
            assertThat(validate("export default function() {}").isValid()).isTrue();
            assertThat(validate("import * as utils from 'utils';").isValid()).isTrue();
            assertThat(validate("export { name as alias };").isValid()).isTrue();
        }

        @Test
        @DisplayName("ternary with numeric property (?. disambiguation)")
        void ternaryDisambiguation() {
            assertThat(validate("const x = cond ? .5 : .25;").isValid()).isTrue();
            assertThat(validate("const y = a ? .3 : b ? .7 : .1;").isValid()).isTrue();
        }

        @Test
        @DisplayName("complex mixed real-world code")
        void complexMixedCode() {
            String code = """
                const handler = async (req, res) => {
                  const { body: { items = [], ...meta } } = req;
                  try {
                    const results = await Promise.all(
                      items.map(async ({ id, ...data }) => {
                        const existing = await db?.findOne?.({ id });
                        if (existing?.status === 'active') {
                          return { ...existing, ...data, updatedAt: Date.now() };
                        }
                        return db.create({ id, ...data });
                      })
                    );
                    res.json({ data: results, ...meta });
                  } catch (err) {
                    console.error(`Error: ${err?.message ?? 'unknown'}`);
                    res?.status?.(500)?.json?.({ error: 'fail' });
                  }
                };
                """;
            assertThat(validate(code).isValid()).isTrue();
        }

        @Test
        @DisplayName("hashbang comment at start of file")
        void hashbang() {
            assertThat(validate("#!/usr/bin/env node\nconsole.log('hi');").isValid()).isTrue();
        }

        @Test
        @DisplayName("empty or blank input")
        void emptyInput() {
            assertThat(validate("").isValid()).isTrue();
            assertThat(validate("   \n\t  ").isValid()).isTrue();
            assertThat(validate(null).isValid()).isTrue();
        }

        @Test
        @DisplayName("generator functions and yield")
        void generators() {
            assertThat(validate("function* gen() { yield 1; yield* [2, 3]; }").isValid()).isTrue();
            assertThat(validate("const g = function* () { yield 'hello'; };").isValid()).isTrue();
        }

        @Test
        @DisplayName("for-of and for-in loops")
        void loops() {
            assertThat(validate("for (const item of items) { process(item); }").isValid()).isTrue();
            assertThat(validate("for (const key in obj) { console.log(key); }").isValid()).isTrue();
            assertThat(validate("for await (const chunk of stream) { handle(chunk); }").isValid()).isTrue();
        }

        @Test
        @DisplayName("let/var without initialiser (valid — only const requires one)")
        void letVarWithoutInitialiser() {
            assertThat(validate("let x;").isValid()).isTrue();
            assertThat(validate("var y;").isValid()).isTrue();
            assertThat(validate("let a, b, c;").isValid()).isTrue();
        }

        @Test
        @DisplayName("const in for-of/for-in without initialiser (valid)")
        void constInForLoop() {
            assertThat(validate("for (const x of items) { use(x); }").isValid()).isTrue();
            assertThat(validate("for (const key in obj) { use(key); }").isValid()).isTrue();
            assertThat(validate("for await (const chunk of stream) { handle(chunk); }").isValid()).isTrue();
        }

        @Test
        @DisplayName("typeof / delete / void with operands")
        void prefixKeywordOperators() {
            assertThat(validate("const t = typeof x;").isValid()).isTrue();
            assertThat(validate("delete obj.prop;").isValid()).isTrue();
            assertThat(validate("const u = void 0;").isValid()).isTrue();
            assertThat(validate("if (typeof s === 'string') { log(s); }").isValid()).isTrue();
        }

        @Test
        @DisplayName("new expressions")
        void newExpressions() {
            assertThat(validate("const d = new Date();").isValid()).isTrue();
            assertThat(validate("const arr = new Array(5);").isValid()).isTrue();
            assertThat(validate("const dt = new Date(...parts);").isValid()).isTrue();
        }

        @Test
        @DisplayName("binary operators with valid operands")
        void binaryOperatorsValid() {
            assertThat(validate("const x = a ?? b;").isValid()).isTrue();
            assertThat(validate("const y = a || b || c;").isValid()).isTrue();
            assertThat(validate("const z = a && b && c;").isValid()).isTrue();
            assertThat(validate("const eq = a === b;").isValid()).isTrue();
            assertThat(validate("const cmp = a <= b;").isValid()).isTrue();
            assertThat(validate("const shift = a << b >> c;").isValid()).isTrue();
            assertThat(validate("const pow = 2 ** 10;").isValid()).isTrue();
        }

        @Test
        @DisplayName("optional chaining with nullish coalescing")
        void optionalChainingWithNullish() {
            assertThat(validate("const x = obj?.prop ?? 'default';").isValid()).isTrue();
            assertThat(validate("const y = fn?.() ?? fallback();").isValid()).isTrue();
            assertThat(validate("const z = arr?.[0] ?? 'empty';").isValid()).isTrue();
        }

        @Test
        @DisplayName("logical assignment operators")
        void logicalAssignment() {
            assertThat(validate("x ||= y;").isValid()).isTrue();
            assertThat(validate("x &&= y;").isValid()).isTrue();
            assertThat(validate("x ??= y;").isValid()).isTrue();
        }

        @Test
        @DisplayName("destructuring with defaults and rest")
        void destructuringDefaults() {
            assertThat(validate("const { a = 1, b = 2 } = obj;").isValid()).isTrue();
            assertThat(validate("const [first = 0, ...rest] = arr;").isValid()).isTrue();
            assertThat(validate("const { x: { y = 0 } = {} } = data;").isValid()).isTrue();
        }

        @Test
        @DisplayName("async class methods and static fields")
        void asyncClassFeatures() {
            assertThat(validate("class S { static count = 0; async fetch() { return await this.api(); } }").isValid()).isTrue();
        }

        @Test
        @DisplayName("tagged template literals")
        void taggedTemplates() {
            assertThat(validate("const raw = String.raw`\\n${value}\\n`;").isValid()).isTrue();
            assertThat(validate("html`<div>${content}</div>`;").isValid()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "const a = 1; let b = 2; var c = 3;",
                "function foo(a, b, c) { return a + b + c; }",
                "const obj = { a: 1, b: 2, c() { return 3; }, get d() { return 4; } };",
                "try { risky(); } catch (e) { handle(e); } finally { cleanup(); }",
                "switch (x) { case 1: break; case 2: return; default: fallback(); }",
                "const [a, b] = [1, 2]; const { c, d } = { c: 3, d: 4 };",
                "typeof x === 'undefined' ? null : x;",
                "const v = new Date(...args) ?? fallback;",
                "const r = a >= b ? a : b;",
                "const t = `result: ${a ?? 'n/a'}`;",
                "for (const [k, v] of entries) { map.set(k, v); }",
                "class Queue { #items = []; enqueue(v) { this.#items.push(v); } }"
        })
        @DisplayName("various valid snippets")
        void validSnippets(String code) {
            assertThat(validate(code).isValid()).isTrue();
        }
    }

    // ==================================================================
    //  Syntax errors — must be caught
    // ==================================================================

    @Nested
    @DisplayName("syntax error detection")
    class SyntaxErrors {

        @Test
        @DisplayName("unbalanced opening parenthesis")
        void unbalancedParen() {
            ValidationResult r = validate("function foo(a, b {\n  return a + b;\n}");
            assertThat(r.isValid()).isFalse();
            assertThat(r.hasErrors()).isTrue();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("(", "balance", "close");
        }

        @Test
        @DisplayName("unbalanced opening brace")
        void unbalancedBrace() {
            ValidationResult r = validate("function foo() {\n  return 1;\n");
            assertThat(r.isValid()).isFalse();
            assertThat(r.hasErrors()).isTrue();
        }

        @Test
        @DisplayName("mismatched closing bracket")
        void mismatchedClosingBracket() {
            ValidationResult r = validate("const arr = [1, 2, 3);");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsIgnoringCase("mismatch");
        }

        @Test
        @DisplayName("stray closing bracket")
        void strayClosingBracket() {
            ValidationResult r = validate("const x = 1; }");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("unexpected", "stray");
        }

        @Test
        @DisplayName("deeply nested mismatch")
        void deeplyNestedMismatch() {
            ValidationResult r = validate("if (true) {\n  while (x) {\n    doSomething();\n  ]\n}");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("unterminated string literal")
        void unterminatedString() {
            ValidationResult r = validate("const x = \"unterminated;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsIgnoringCase("unterminated");
        }

        @Test
        @DisplayName("unterminated single-quoted string")
        void unterminatedSingleString() {
            ValidationResult r = validate("const s = 'hello world;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("unterminated template literal")
        void unterminatedTemplate() {
            ValidationResult r = validate("const html = `<div>${content}</div>`;");
            assertThat(r.isValid()).isTrue(); // properly closed

            r = validate("const html = `<div>");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsIgnoringCase("template");
        }

        @Test
        @DisplayName("unterminated template interpolation")
        void unterminatedInterpolation() {
            ValidationResult r = validate("const x = `${value;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("unterminated block comment")
        void unterminatedBlockComment() {
            ValidationResult r = validate("/* this comment never ends...");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsIgnoringCase("comment");
        }

        @Test
        @DisplayName("arrow function missing body")
        void arrowMissingBody() {
            ValidationResult r = validate("const fn = () =>;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("arrow", "body", "=>");
        }

        @Test
        @DisplayName("arrow function at end of file")
        void arrowAtEof() {
            ValidationResult r = validate("const fn = x =>");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("spread without operand")
        void spreadWithoutOperand() {
            ValidationResult r = validate("fn(...);");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("spread", "operand", "...");
        }

        @Test
        @DisplayName("spread at end of file")
        void spreadAtEof() {
            ValidationResult r = validate("const x = ...");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("optional chaining without member")
        void optionalChainingWithoutMember() {
            ValidationResult r = validate("const x = obj?.;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("optional", "chaining", "?");
        }

        @Test
        @DisplayName("const declaration without binding")
        void constWithoutBinding() {
            ValidationResult r = validate("const ;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("const", "declaration", "missing", "variable");
        }

        @Test
        @DisplayName("let declaration without binding")
        void letWithoutBinding() {
            ValidationResult r = validate("let = 5;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("assignment without right-hand side")
        void assignmentWithoutRhs() {
            ValidationResult r = validate("const a = ;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("right-hand", "missing", "=");
        }

        @Test
        @DisplayName("compound assignment without right-hand side")
        void compoundAssignmentWithoutRhs() {
            ValidationResult r = validate("x += ;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("class extends without name")
        void classExtendsWithouName() {
            ValidationResult r = validate("class extends Base {}");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("class", "name", "extends");
        }

        @Test
        @DisplayName("throw without expression")
        void throwWithoutExpression() {
            ValidationResult r = validate("throw;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage()).containsAnyOf("throw", "expression");
        }

        @Test
        @DisplayName("function without body at EOF")
        void functionWithoutBody() {
            ValidationResult r = validate("function");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("class without body at EOF")
        void classWithoutBody() {
            ValidationResult r = validate("class");
            assertThat(r.isValid()).isFalse();
        }

        // ---- New ES6+ structural checks ----

        @Test
        @DisplayName("const without initialiser")
        void constWithoutInitialiser() {
            ValidationResult r = validate("const x;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("const", "initialiser", "initializer", "assignment");
        }

        @Test
        @DisplayName("const destructuring without initialiser")
        void constDestructuringWithoutInitialiser() {
            assertThat(validate("const { a, b };").isValid()).isFalse();
            assertThat(validate("const [first];").isValid()).isFalse();
        }

        @Test
        @DisplayName("typeof without operand")
        void typeofWithoutOperand() {
            ValidationResult r = validate("typeof;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("typeof", "operand", "missing");
        }

        @Test
        @DisplayName("delete without operand")
        void deleteWithoutOperand() {
            assertThat(validate("delete;").isValid()).isFalse();
        }

        @Test
        @DisplayName("void without operand")
        void voidWithoutOperand() {
            assertThat(validate("void;").isValid()).isFalse();
        }

        @Test
        @DisplayName("new without operand")
        void newWithoutOperand() {
            ValidationResult r = validate("const x = new;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("new", "operand", "missing");
        }

        @Test
        @DisplayName("await without operand")
        void awaitWithoutOperand() {
            ValidationResult r = validate("async function f() { await; }");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("await", "operand", "missing");
        }

        @Test
        @DisplayName("logical AND without left operand")
        void logicalAndWithoutLhs() {
            ValidationResult r = validate("&& y;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("left-hand", "operand", "&&");
        }

        @Test
        @DisplayName("nullish coalescing without left operand")
        void nullishWithoutLhs() {
            ValidationResult r = validate("const x = ?? y;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("strict equality without left operand")
        void equalityWithoutLhs() {
            ValidationResult r = validate("=== y;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("compound assignment without left operand")
        void compoundAssignmentWithoutLhs() {
            ValidationResult r = validate("+= 5;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("if without parenthesis")
        void ifWithoutParen() {
            ValidationResult r = validate("if true { }");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("if", "parenthesis", "(");
        }

        @Test
        @DisplayName("while without parenthesis")
        void whileWithoutParen() {
            assertThat(validate("while true { }").isValid()).isFalse();
        }

        @Test
        @DisplayName("switch without parenthesis")
        void switchWithoutParen() {
            assertThat(validate("switch x { }").isValid()).isFalse();
        }

        @Test
        @DisplayName("for without parenthesis")
        void forWithoutParen() {
            ValidationResult r = validate("for x of arr { }");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("for", "parenthesis", "(");
        }

        @Test
        @DisplayName("catch without binding or brace")
        void catchWithoutBinding() {
            ValidationResult r = validate("try { } catch e { }");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("catch");
        }

        @Test
        @DisplayName("try without brace")
        void tryWithoutBrace() {
            ValidationResult r = validate("try x { }");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("finally without brace")
        void finallyWithoutBrace() {
            ValidationResult r = validate("try { } finally x { }");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("import without specifier")
        void importWithoutSpecifier() {
            ValidationResult r = validate("import;");
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getMessage())
                    .containsAnyOf("import", "specifier", "missing");
        }

        @Test
        @DisplayName("export without declaration")
        void exportWithoutDeclaration() {
            ValidationResult r = validate("export;");
            assertThat(r.isValid()).isFalse();
        }

        @Test
        @DisplayName("binary operator without right-hand side")
        void binaryOpWithoutRhs() {
            assertThat(validate("const x = a &&;").isValid()).isFalse();
            assertThat(validate("const x = a ||;").isValid()).isFalse();
            assertThat(validate("const x = a ??;").isValid()).isFalse();
        }
    }

    // ==================================================================
    //  Error detail quality
    // ==================================================================

    @Nested
    @DisplayName("error reporting quality")
    class ErrorDetailQuality {

        @Test
        @DisplayName("errors include line numbers")
        void errorsHaveLineNumbers() {
            String code = "const a = 1;\nconst b = 2;\nconst c = [1, 2);\n";
            ValidationResult r = validate(code);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().getFirst().getLine()).isPositive();
        }

        @Test
        @DisplayName("errors include column numbers")
        void errorsHaveColumnNumbers() {
            ValidationResult r = validate("const x = [1, 2);");
            assertThat(r.isValid()).isFalse();
            ValidationError first = r.getErrors().getFirst();
            assertThat(first.getColumn()).isPositive();
        }

        @Test
        @DisplayName("bracket error references the opener position")
        void bracketErrorReferencesOpener() {
            String code = "const fn = function() {\n  return [1, 2);\n};";
            ValidationResult r = validate(code);
            assertThat(r.isValid()).isFalse();
            // The mismatched ) should reference the [ opener
            String msg = r.getErrors().stream()
                    .map(ValidationError::getMessage)
                    .reduce("", (a, b) -> a + " " + b);
            assertThat(msg).containsAnyOf("[", "mismatch", "expected");
        }

        @Test
        @DisplayName("multiple errors are reported")
        void multipleErrors() {
            String code = "const a = ;\nconst b = ;";
            ValidationResult r = validate(code);
            assertThat(r.isValid()).isFalse();
            assertThat(r.getErrors().size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("valid result message is descriptive")
        void validMessageIsDescriptive() {
            ValidationResult r = validate("const x = 1;");
            assertThat(r.isValid()).isTrue();
            assertThat(r.getMessage()).isNotBlank();
        }
    }

    // ==================================================================
    //  Tricky edge cases — brackets inside strings/comments must not confuse
    // ==================================================================

    @Nested
    @DisplayName("tricky edge cases")
    class TrickyEdgeCases {

        @Test
        @DisplayName("brackets inside strings are ignored")
        void bracketsInStrings() {
            assertThat(validate("const s = '({[)}]'; const arr = [1];").isValid()).isTrue();
        }

        @Test
        @DisplayName("brackets inside comments are ignored")
        void bracketsInComments() {
            assertThat(validate("// ({[)}]\nconst x = (1);").isValid()).isTrue();
            assertThat(validate("/* ({[)}] */ const y = [1];").isValid()).isTrue();
        }

        @Test
        @DisplayName("brackets inside regex are ignored")
        void bracketsInRegex() {
            assertThat(validate("const re = /[({[})]/; const arr = [1, 2];").isValid()).isTrue();
        }

        @Test
        @DisplayName("brackets inside template interpolation are tracked")
        void bracketsInTemplateInterpolation() {
            assertThat(validate("const x = `${arr[0]}`;").isValid()).isTrue();
            assertThat(validate("const x = `${fn(a, b)}`;").isValid()).isTrue();
            assertThat(validate("const x = `${ {a: 1} }`;").isValid()).isTrue();
        }

        @Test
        @DisplayName("nested template literals")
        void nestedTemplates() {
            assertThat(validate("const x = `outer ${`mid ${`inner`}`} end`;").isValid()).isTrue();
        }

        @Test
        @DisplayName("string escapes don't confuse the tokenizer")
        void stringEscapes() {
            assertThat(validate("const s = 'It\\'s a test';").isValid()).isTrue();
            assertThat(validate("const d = \"He said \\\"hi\\\"\";").isValid()).isTrue();
            assertThat(validate("const t = `${'can\\'t'}`;").isValid()).isTrue();
        }

        @Test
        @DisplayName("division vs regex disambiguation")
        void divisionVsRegex() {
            assertThat(validate("const x = a / b / c;").isValid()).isTrue();
            assertThat(validate("const re = /pattern/; x = re.test(s);").isValid()).isTrue();
            assertThat(validate("return /regex/.test(str);").isValid()).isTrue();
            assertThat(validate("const ratio = 10 / 2;").isValid()).isTrue();
        }

        @Test
        @DisplayName("ternary with leading-dot numbers")
        void ternaryWithDotNumbers() {
            assertThat(validate("const v = flag ? .5 : .25;").isValid()).isTrue();
        }

        @Test
        @DisplayName("method chaining on numbers")
        void numberMethodChaining() {
            assertThat(validate("const s = (42).toString();").isValid()).isTrue();
            assertThat(validate("const f = 3.14.toFixed(2);").isValid()).isTrue();
        }

        @Test
        @DisplayName("comment at end of file without newline")
        void commentAtEof() {
            assertThat(validate("const x = 1; // trailing comment").isValid()).isTrue();
            assertThat(validate("const x = 1; /* block */").isValid()).isTrue();
        }

        @Test
        @DisplayName("labels in statements")
        void labels() {
            assertThat(validate("outer: for (const a of items) { inner: for (const b of a) { break outer; } }").isValid()).isTrue();
        }

        @Test
        @DisplayName("comma operator")
        void commaOperator() {
            assertThat(validate("const x = (1, 2, 3);").isValid()).isTrue();
        }

        @Test
        @DisplayName("empty arrow function returning object")
        void arrowReturningObject() {
            assertThat(validate("const f = () => ({ key: 'value' });").isValid()).isTrue();
        }

        @Test
        @DisplayName("new.target")
        void newTarget() {
            assertThat(validate("function Foo() { if (new.target) { this.x = 1; } }").isValid()).isTrue();
        }

        @Test
        @DisplayName("computed property names")
        void computedProperties() {
            assertThat(validate("const obj = { [Symbol.iterator]: function* () {}, ['dynamic-' + key]: value };").isValid()).isTrue();
        }

        @Test
        @DisplayName("async generator functions")
        void asyncGenerators() {
            assertThat(validate("async function* gen() { yield await fetch(url); }").isValid()).isTrue();
        }

        @Test
        @DisplayName("chained method calls with template literals")
        void chainedTemplateCalls() {
            assertThat(validate("const x = arr.map(v => `${v}`).filter(Boolean).join(', ');").isValid()).isTrue();
        }

        @Test
        @DisplayName("class with computed methods, generators and async")
        void classAdvanced() {
            assertThat(validate("class C { *[Symbol.iterator]() { yield* this.items; } static async create() { return new C(); } get length() { return this.items.length; } }").isValid()).isTrue();
        }

        @Test
        @DisplayName("deeply nested destructuring with defaults and spread")
        void deeplyNestedDestructuring() {
            assertThat(validate("const { a: { b: { c, ...rest } = {} } = {} } = obj;").isValid()).isTrue();
        }

        @Test
        @DisplayName("typeof in ternary after optional chaining")
        void typeofAfterOptionalChaining() {
            assertThat(validate("const x = typeof obj?.method?.() === 'function' ? obj.method() : null;").isValid()).isTrue();
        }

        @Test
        @DisplayName("spread in new expression with await")
        void spreadNewWithAwait() {
            assertThat(validate("const instance = new Model(...(await fetchConfig()));").isValid()).isTrue();
        }

        @Test
        @DisplayName("exponentiation chained with nullish")
        void exponentiationWithNullish() {
            assertThat(validate("const v = (base ** exp) ?? fallback;").isValid()).isTrue();
        }

        @Test
        @DisplayName("generator yield with binary operators")
        void generatorYieldWithOps() {
            assertThat(validate("function* g() { yield 1 + 2 * 3; yield* [1, 2] ?? []; }").isValid()).isTrue();
        }

        @Test
        @DisplayName("optional catch binding (ES2019)")
        void optionalCatchBinding() {
            assertThat(validate("try { risky(); } catch { fallback(); }").isValid()).isTrue();
            assertThat(validate("try { a(); } catch { b(); } finally { c(); }").isValid()).isTrue();
        }
    }
}
