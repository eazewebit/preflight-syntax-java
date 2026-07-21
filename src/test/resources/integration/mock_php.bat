@echo off
REM Mock php binary that simulates `php -l <file>` behavior.
REM Second argument is the file path (-l is first arg).
REM Exit 0 = valid, Exit 255 = syntax error.

if "%~1"=="-l" goto lint
if "%~1"=="--version" (
    echo PHP 8.3.14 (cli) (built: Dec 10 2024 10:00:00) (NTS)
    exit /b 0
)
echo Unknown option: %~1 >&2
exit /b 1

:lint
if "%~2"=="" (
    echo No syntax errors detected in Standard input >&2
    exit /b 0
)

if not exist "%~2" (
    echo Could not open input file '%~2'. >&2
    exit /b 255
)

findstr /C:"SYNTAX_ERROR" "%~2" >nul 2>&1
if %errorlevel%==0 (
    echo PHP Parse error:  syntax error, unexpected ';' in %~2 on line 4 >&2
    echo Errors parsing %~2 >&2
    exit /b 255
)

findstr /C:"UNEXPECTED_TOKEN" "%~2" >nul 2>&1
if %errorlevel%==0 (
    echo PHP Parse error:  syntax error, unexpected '@' in %~2 on line 7 >&2
    echo Errors parsing %~2 >&2
    exit /b 255
)

echo No syntax errors detected in %~2
exit /b 0
