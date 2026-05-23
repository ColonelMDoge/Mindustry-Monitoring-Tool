@echo off
setlocal

REM =========================================================
REM Setup
REM =========================================================

cd /d "%~dp0"
set "APPDATA=%CD%\cData"

REM =========================================================
REM Run download first
REM =========================================================

call :download
if errorlevel 1 (
    echo Download step failed.
    pause
    exit /b
)

REM =========================================================
REM Launch tool
REM =========================================================

echo Starting MindustryMonitoringTool...
java -jar MindustryMonitoringTool.jar

REM Capture Java exit code
set EXITCODE=%ERRORLEVEL%

echo Tool exited with code %EXITCODE%

REM =========================================================
REM Handle host exit code
REM =========================================================

if "%EXITCODE%"=="10" (
    echo Triggering upload...
    call :upload
)

exit /b


REM =========================================================
REM DOWNLOAD FUNCTION
REM =========================================================

:download

git --version >nul 2>&1
if errorlevel 1 (
    echo Git is not installed.
    pause
    exit /b 1
)

if not exist ".git" (
    echo Not a git repository.
    pause
    exit /b 1
)

for /f %%i in ('git branch --show-current') do set BRANCH=%%i

git reset --hard

git pull origin %BRANCH%

if errorlevel 1 (
    echo Pull failed.
    pause
    exit /b 1
)

echo Download complete.
exit /b 0


REM =========================================================
REM UPLOAD FUNCTION
REM =========================================================

:upload

set "COMMIT_MSG=Auto upload"
git add cData
git add *.bat
git add .gitignore

git diff --cached --quiet
if %errorlevel%==0 (
    echo No changes to upload.
    exit /b 0
)

git commit -m "%COMMIT_MSG%"

if errorlevel 1 (
    echo Commit failed.
    pause
    exit /b 1
)

for /f %%i in ('git branch --show-current') do set BRANCH=%%i

git push origin %BRANCH%

if errorlevel 1 (
    echo Push failed.
    pause
    exit /b 1
)

echo Upload complete.
exit /b 0