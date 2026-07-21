@echo off
REM Mock binary that simulates a slow/hanging validation tool.
REM Used to test timeout handling in ProcessExecutor.
ping -n 60 127.0.0.1 >nul
exit /b 0
