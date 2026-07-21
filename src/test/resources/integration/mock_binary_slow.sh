#!/bin/bash
# Mock binary that simulates a slow/hanging validation tool on Unix.
# Used to test timeout handling in ProcessExecutor.
sleep 60
exit 0
