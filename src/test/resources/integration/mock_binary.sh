#!/bin/bash
# Mock binary that simulates a validation tool on Unix systems.
# Reads first argument as file path, checks if file contains "SYNTAX_ERROR_MARKER".
# Exit 0 = valid, Exit 1 = invalid, Exit 2 = usage error.

if [ -z "$1" ]; then
    echo "Usage: mock_binary.sh <file>" >&2
    exit 2
fi

if [ ! -f "$1" ]; then
    echo "File not found: $1" >&2
    exit 2
fi

if grep -q "SYNTAX_ERROR_MARKER" "$1"; then
    echo "Syntax error detected in $1" >&2
    exit 1
fi

echo "OK"
exit 0
