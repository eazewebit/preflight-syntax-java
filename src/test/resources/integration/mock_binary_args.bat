@echo off
REM Mock binary that echoes all arguments and environment info.
REM Used to verify argument passing and process invocation details.
echo ARGS_START
:loop
if "%~1"=="" goto done
echo ARG:%~1
shift
goto loop
:done
echo ARGS_END
echo CWD:%CD%
echo PID:%RANDOM%
exit /b 0
