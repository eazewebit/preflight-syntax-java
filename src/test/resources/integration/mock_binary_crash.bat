@echo off
REM Mock binary that simulates a crashing validation tool.
REM Used to test error handling when binary fails unexpectedly.
echo Fatal error: segmentation fault >&2
exit /b 139
