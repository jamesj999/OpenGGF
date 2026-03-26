@echo off
REM Record a BizHawk trace for Sonic 1 GHZ Act 1.
REM
REM Usage:  record_trace.bat <rom_path> <bk2_path>
REM Example: record_trace.bat "Sonic The Hedgehog (W) (REV01) [!].gen" "Movie\ghz1_fullrun.bk2"
REM
REM Output goes to: trace_output\ (next to this script, or BizHawk working dir)
REM BizHawk path can be overridden with BIZHAWK_EXE env var.

setlocal

set "BIZHAWK_EXE=%BIZHAWK_EXE%"
if "%BIZHAWK_EXE%"=="" set "BIZHAWK_EXE=C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\EmuHawk.exe"

set "LUA_SCRIPT=%~dp0s1_trace_recorder.lua"

if "%~1"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^>
    echo.
    echo   rom_path   Path to Sonic 1 REV01 ROM
    echo   bk2_path   Path to BK2 movie file
    exit /b 1
)
if "%~2"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^>
    exit /b 1
)

set "ROM_PATH=%~1"
set "BK2_PATH=%~2"

echo === BizHawk Trace Recorder ===
echo ROM:    %ROM_PATH%
echo Movie:  %BK2_PATH%
echo Lua:    %LUA_SCRIPT%
echo Output: trace_output\
echo.
echo Starting BizHawk in headless mode...

"%BIZHAWK_EXE%" --chromeless --lua "%LUA_SCRIPT%" --movie "%BK2_PATH%" "%ROM_PATH%"

if %ERRORLEVEL% neq 0 (
    echo BizHawk exited with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo.
echo === Trace recording complete ===
if exist "trace_output\metadata.json" (
    echo Output files:
    dir /b trace_output\
) else (
    echo WARNING: No trace output found. Check BizHawk console for errors.
)
