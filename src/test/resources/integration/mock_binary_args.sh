#!/bin/bash
# Mock binary that echoes all arguments and environment info on Unix.
# Used to verify argument passing and process invocation details.
echo "ARGS_START"
for arg in "$@"; do
    echo "ARG:$arg"
done
echo "ARGS_END"
echo "CWD:$(pwd)"
echo "PID:$$"
exit 0
