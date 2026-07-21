<?php
/**
 * Helper script for PHP syntax validation.
 * Usage: php validate_php.php <file>
 * Exit code 0 = valid, non-zero = syntax error.
 */
if ($argc < 2) {
    fwrite(STDERR, "Usage: php validate_php.php <file>\n");
    exit(2);
}

$filePath = $argv[1];
if (!file_exists($filePath)) {
    fwrite(STDERR, "File not found: $filePath\n");
    exit(2);
}

$output = [];
$returnCode = 0;
exec('php -l ' . escapeshellarg($filePath) . ' 2>&1', $output, $returnCode);

if ($returnCode === 0) {
    echo "OK\n";
    exit(0);
} else {
    fwrite(STDERR, implode("\n", $output) . "\n");
    exit(1);
}
