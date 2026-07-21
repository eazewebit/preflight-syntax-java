@echo off
REM Mock binary that simulates a validation tool.
REM Reads first argument as file path, checks if file contains "INVALID" marker.
REM Exit 0 = valid, Exit 1 = invalid, Exit 2 = usage error.

if "%~1"=="" (
    echo Usage: mock_binary.bat ^<file^> >&2
    exit /b 2
)

if not exist "%~1" (
    echo File not found: %~1 >&2
    exit /b 2
)

findstr /C:"SYNTAX_ERROR_MARKER" "%~1" >nul 2>&1
if %errorlevel%==0 (
    echo Syntax error detected in %~1 >&2
    exit /b 1
)

echo OK
exit /b 0
