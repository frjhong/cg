@echo off
setlocal
set "PROJECT_DIR=%~dp0.."
set "MODE=%~1"
if not defined MODE set "MODE=ai"
if not "%~2"=="" goto usage
if /I "%MODE%"=="help" goto help
if /I "%MODE%"=="--help" goto help
if /I "%MODE%"=="demo" goto demo
if /I not "%MODE%"=="ai" goto usage
if not defined AI_API_KEY (
  echo Set AI_API_KEY before starting AI mode, or use scripts\run.cmd demo. 1>&2
  exit /b 2
)
set "AI_FIXTURE="
goto java

:demo
set "AI_FIXTURE=%PROJECT_DIR%\src\test\resources\sample-plan.json"
if not exist "%AI_FIXTURE%" (
  echo Bundled sample-plan.json is missing. 1>&2
  exit /b 2
)

:java
set "JAVA_CMD=java"
if defined JAVA_HOME (
  set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
  if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME must point to a valid JDK 21 or newer. 1>&2
    exit /b 2
  )
) else (
  where java >nul 2>&1
  if errorlevel 1 (
    echo Install JDK 21 or newer and configure JAVA_HOME or PATH. 1>&2
    exit /b 2
  )
)
pushd "%PROJECT_DIR%" || exit /b 2
set "APP_JAR=target\weekly-report-1.0.0.jar"
if exist "%APP_JAR%" goto start
set "APP_JAR=artifacts\weekly-report-1.0.0.jar"
if exist "%APP_JAR%" goto start
echo No JAR found; building with Maven Wrapper ^(including tests^).
call mvnw.cmd -B package
if errorlevel 1 (
  popd
  exit /b 1
)
set "APP_JAR=target\weekly-report-1.0.0.jar"

:start
echo Starting weekly-report ^(%MODE%^). Stop with Ctrl+C.
"%JAVA_CMD%" -jar "%APP_JAR%"
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%

:usage
echo Usage: scripts\run.cmd [ai^|demo] 1>&2
exit /b 2

:help
echo Usage: scripts\run.cmd [ai^|demo]
echo   ai    Process reports using AI_API_KEY ^(default^).
echo   demo  Replay only the bundled 0916.docx and 0930.docx samples.
exit /b 0
