@echo off
REM Script để tự động tăng version cho TXA Hub App (Windows)
REM Usage: increment_version.bat [bugfix|feature|major]

setlocal enabledelayedexpansion

set VERSION_FILE=..\version.properties
set TYPE=%1
if "%TYPE%"=="" set TYPE=bugfix

if not exist "%VERSION_FILE%" (
    echo Error: version.properties not found!
    exit /b 1
)

REM Đọc version hiện tại
for /f "tokens=2 delims==" %%a in ('findstr "^VERSION_NAME=" "%VERSION_FILE%"') do set CURRENT_VERSION=%%a
for /f "tokens=2 delims==" %%a in ('findstr "^VERSION_CODE=" "%VERSION_FILE%"') do set CURRENT_CODE=%%a

echo Current version: %CURRENT_VERSION% (code: %CURRENT_CODE%)

REM Parse version (simplified - cần PowerShell hoặc script phức tạp hơn cho Windows)
REM Tạm thời dùng cách đơn giản: tăng version code và version name thủ công

if "%TYPE%"=="bugfix" (
    set /a NEW_CODE=%CURRENT_CODE% + 1
    echo New version code: !NEW_CODE!
    echo Please update VERSION_NAME manually in version.properties for bugfix
) else if "%TYPE%"=="feature" (
    set /a NEW_CODE=%CURRENT_CODE% + 10
    echo New version code: !NEW_CODE!
    echo Please update VERSION_NAME manually in version.properties for feature
) else if "%TYPE%"=="major" (
    set /a NEW_CODE=%CURRENT_CODE% + 100
    echo New version code: !NEW_CODE!
    echo Please update VERSION_NAME manually in version.properties for major
) else (
    echo Error: Invalid type. Use: bugfix, feature, or major
    exit /b 1
)

REM Cập nhật version code
powershell -Command "(Get-Content '%VERSION_FILE%') -replace '^VERSION_CODE=.*', 'VERSION_CODE=!NEW_CODE!' | Set-Content '%VERSION_FILE%'"

echo Version code updated to !NEW_CODE!
echo Please manually update VERSION_NAME in version.properties

