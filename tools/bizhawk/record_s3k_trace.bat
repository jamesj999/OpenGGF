@echo off
REM Record a BizHawk trace for any Sonic 3&K zone/act.
REM The Lua script auto-detects zone and act from RAM.
REM
REM Usage:  record_s3k_trace.bat <rom_path> <bk2_path> [trace_profile]
REM Example: record_s3k_trace.bat "Sonic and Knuckles & Sonic 3 (W) [!].gen" "Movies\s3k-aiz1.bk2"
REM Example: record_s3k_trace.bat "Sonic and Knuckles & Sonic 3 (W) [!].gen" "src\test\resources\traces\s3k\aiz1_to_hcz_fullrun\s3k-aiz1-aiz2-sonictails.bk2" aiz_end_to_end
REM
REM Output goes to: <repo>\tools\bizhawk\trace_output\
REM   (BizHawk resolves the script's relative trace_output folder from the
REM    recorder script location)
REM
REM BizHawk path can be overridden with BIZHAWK_EXE env var.
REM To see the emulator window during recording, edit HEADLESS_VISIBLE in
REM s3k_trace_recorder.lua (set to true).

setlocal

set "BIZHAWK_EXE=%BIZHAWK_EXE%"
if "%BIZHAWK_EXE%"=="" set "BIZHAWK_EXE=C:\Users\farre\IdeaProjects\sonic-engine\docs\BizHawk-2.11-win-x64\EmuHawk.exe"

set "LUA_SCRIPT=%~dp0s3k_trace_recorder.lua"

set "OUTPUT_DIR=%~dp0trace_output"

if "%~1"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^> [trace_profile]
    echo.
    echo   rom_path   Path to Sonic 3 ^& Knuckles locked-on ROM
    echo   bk2_path   Path to BK2 movie file
    echo   trace_profile  Optional. Defaults to gameplay_unlock. Use aiz_end_to_end for the AIZ intro through HCZ fixture.
    echo.
    echo The script auto-detects zone and act from the game's RAM.
    echo Output is written to: %OUTPUT_DIR%\
    exit /b 1
)
if "%~2"=="" (
    echo Usage: %~nx0 ^<rom_path^> ^<bk2_path^> [trace_profile]
    exit /b 1
)

for %%I in ("%~1") do set "ROM_PATH=%%~fI"
for %%I in ("%~2") do set "BK2_PATH=%%~fI"
set "TRACE_PROFILE=%~3"
if "%TRACE_PROFILE%"=="" set "TRACE_PROFILE=%OGGF_S3K_TRACE_PROFILE%"
if "%TRACE_PROFILE%"=="" set "TRACE_PROFILE=gameplay_unlock"
set "OGGF_S3K_TRACE_PROFILE=%TRACE_PROFILE%"

echo === BizHawk Sonic 3^&K Trace Recorder ===
echo ROM:    %ROM_PATH%
echo Movie:  %BK2_PATH%
echo Profile: %TRACE_PROFILE%
echo Lua:    %LUA_SCRIPT%
echo Output: %OUTPUT_DIR%\
echo.
echo Starting BizHawk in headless mode...

set "POWERSHELL_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"

"%POWERSHELL_EXE%" -NoProfile -ExecutionPolicy Bypass -Command ^
  "Add-Type -AssemblyName System.IO.Compression.FileSystem;" ^
  "$zip = [System.IO.Compression.ZipFile]::OpenRead($env:BK2_PATH);" ^
  "$entry = $zip.Entries | Where-Object { $_.FullName -eq 'Input Log.txt' };" ^
  "if ($entry -ne $null) {" ^
  "  $reader = New-Object System.IO.StreamReader($entry.Open());" ^
  "  $frameCount = 0;" ^
  "  while (($line = $reader.ReadLine()) -ne $null) { if ($line.StartsWith('|')) { $frameCount++ } }" ^
  "  $reader.Dispose();" ^
  "  $env:OGGF_BK2_FRAME_COUNT = [string]$frameCount;" ^
  "}" ^
  "$zip.Dispose();" ^
  "$psi = New-Object System.Diagnostics.ProcessStartInfo;" ^
  "$psi.FileName = $env:BIZHAWK_EXE;" ^
  "$psi.WorkingDirectory = [System.IO.Path]::GetDirectoryName($env:BIZHAWK_EXE);" ^
  "$psi.UseShellExecute = $false;" ^
  "$psi.RedirectStandardOutput = $true;" ^
  "$psi.RedirectStandardError = $true;" ^
  "$psi.Arguments = ('--chromeless --lua \"' + $env:LUA_SCRIPT + '\" --movie \"' + $env:BK2_PATH + '\" \"' + $env:ROM_PATH + '\"');" ^
  "$proc = [System.Diagnostics.Process]::Start($psi);" ^
  "$stdout = $proc.StandardOutput.ReadToEnd();" ^
  "$stderr = $proc.StandardError.ReadToEnd();" ^
  "$proc.WaitForExit();" ^
  "if ($stdout) { [Console]::Out.Write($stdout) }" ^
  "if ($stderr) { [Console]::Error.Write($stderr) }" ^
  "exit $proc.ExitCode"

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
    type "%OUTPUT_DIR%\metadata.json"
) else (
    echo WARNING: No trace output found in %OUTPUT_DIR%\
    echo Check BizHawk Lua console for errors.
)
