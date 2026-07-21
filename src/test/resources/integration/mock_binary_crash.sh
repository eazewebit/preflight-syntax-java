#!/bin/bash
# Mock binary that simulates a crashing validation tool on Unix.
# Used to test error handling when binary fails unexpectedly.
echo "Fatal error: segmentation fault" >&2
exit 139
