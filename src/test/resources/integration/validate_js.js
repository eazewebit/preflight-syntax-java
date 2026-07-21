/**
 * Helper script for JavaScript syntax validation via Node.js.
 * Usage: node validate_js.js <file>
 * Exit code 0 = valid, non-zero = syntax error.
 */
const fs = require('fs');
const vm = require('vm');

const filePath = process.argv[2];
if (!filePath) {
    console.error('Usage: node validate_js.js <file>');
    process.exit(2);
}

try {
    const source = fs.readFileSync(filePath, 'utf-8');
    new vm.Script(source, { filename: filePath });
    console.log('OK');
    process.exit(0);
} catch (e) {
    console.error(e.message);
    process.exit(1);
}
