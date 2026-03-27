@echo off
REM Record a BizHawk trace for any Sonic 1 zone/act.
REM The Lua script auto-detects zone and act from RAM.
REM
REM Usage:  record_trace.bat <rom_path> <bk2_path>
REM Example: record_trace.bat "Sonic The Hedgehog (W) (REV01) [!].gen" "Movies\s1-mz1.bk2"
REM
REM Output goes to: <bizhawk_dir>\Lua\trace_output\
REM   (BizHawk sets the Lua working directory to its Lua\ folder)
REM
REM BizHawk path can be overridden with BIZHAWK_EXE env var.
REM To see the emulator window during recording, edit HEADLESS_VISIBLE in
REM s1_trace_recorder.lua (set to true).

setlocal

set "BIZHAWK_EXE=%BIZHAWK_EXE%"
if "%BIZHAWK_EXE%"=="" set "BIZHAWK_EXE=C:\Users\farre\Downloads\_Sorted\Emulators\BizHawk-2.11-win-x64\EmuHawk.exe"

set "LUA_SCRIPT=%~dp0s1_trace_recorder.lua"

REM Derive BizHawk directory from EXE path for output location
for %%I in ("%BIZHAWK_EXE%") do set "BIZHAWK_DIR=%%~dpI"
set "OUTPUT_DIR=%BIZHAWK_DIR%Lua\trace_output"

if "%~1"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^>
    echo.
    echo   rom_path   Path to Sonic 1 REV01 ROM
    echo   bk2_path   Path to BK2 movie file
    echo.
    echo The script auto-detects zone and act from the game's RAM.
    echo Output is written to: %OUTPUT_DIR%\
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
echo Output: %OUTPUT_DIR%\
echo.
echo Starting BizHawk in headless mode...

"%BIZHAWK_EXE%" --chromeless --lua "%LUA_SCRIPT%" --movie "%BK2_PATH%" "%ROM_PATH%"

if %ERRORLEVEL% neq 0 (
    echo BizHawk exited with error code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo.
echo === Trace recording complete ===
if exist "%OUTPUT_DIR%\metadata.json" (
    echo Output files:
    dir /b "%OUTPUT_DIR%\"
    echo.
    REM Show metadata summary
    type "%OUTPUT_DIR%\metadata.json"
) else (
    echo WARNING: No trace output found in %OUTPUT_DIR%\
    echo Check BizHawk Lua console for errors.
)
