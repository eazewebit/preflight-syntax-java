@echo off
REM Mock node binary that simulates `node --check <file>` behavior.
REM Reads second argument as file path (--check is first arg).
REM Exit 0 = valid, Exit 1 = syntax error.

if "%~2"=="" (
    echo SyntaxError: Unexpected end of input >&2
    exit /b 1
)

if not exist "%~2" (
    echo Error: Cannot find module '%~2' >&2
    exit /b 1
)

findstr /C:"SYNTAX_ERROR" "%~2" >nul 2>&1
if %errorlevel%==0 (
    echo file:///%~2:3 >&2
    echo     const x = ;  >&2
    echo              ^ >&2
    echo SyntaxError: Unexpected token ';' >&2
    exit /b 1
)

findstr /C:"UNEXPECTED_TOKEN" "%~2" >nul 2>&1
if %errorlevel%==0 (
    echo file:///%~2:5 >&2
    echo     let y = @; >&2
    echo              ^ >&2
    echo SyntaxError: Unexpected token '@' >&2
    exit /b 1
)

exit /b 0
