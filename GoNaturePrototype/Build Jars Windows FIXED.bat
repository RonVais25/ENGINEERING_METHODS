@echo off
setlocal enabledelayedexpansion
REM Build GoNatureServer.jar and GoNatureClient.jar on Windows.
cd /d "%~dp0"

set "JAVA_HOME_FALLBACK=C:\Program Files\BellSoft\LibericaJDK-21-Full"
if not defined JAVA_HOME if exist "%JAVA_HOME_FALLBACK%\bin\javac.exe" set "JAVA_HOME=%JAVA_HOME_FALLBACK%"

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  set "JAVAC_EXE=%JAVA_HOME%\bin\javac.exe"
  set "JAR_EXE=%JAVA_HOME%\bin\jar.exe"
) else (
  set "JAVA_EXE=java"
  set "JAVAC_EXE=javac"
  set "JAR_EXE=jar"
)

set "MYSQL_JAR=lib\mysql-connector-j-9.6.0.jar"

echo Cleaning build dirs...
if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build
mkdir dist

echo Creating sources list...
dir /s /b src\*.java > build\sources.txt

if exist "%JAVAFX_HOME%\lib\javafx.controls.jar" (
  echo Compiling with external JavaFX: %JAVAFX_HOME%\lib
  "%JAVAC_EXE%" --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls,javafx.graphics,javafx.fxml -cp "%MYSQL_JAR%" -d build @build\sources.txt
) else (
  echo Compiling with JavaFX bundled in the JDK, if available.
  "%JAVAC_EXE%" --add-modules javafx.controls,javafx.graphics,javafx.fxml -cp "%MYSQL_JAR%" -d build @build\sources.txt
)
if errorlevel 1 goto error

> build\server-manifest.mf echo Manifest-Version: 1.0
>> build\server-manifest.mf echo Main-Class: server.app.StartServer
>> build\server-manifest.mf echo Class-Path: ../lib/mysql-connector-j-9.6.0.jar lib/mysql-connector-j-9.6.0.jar
>> build\server-manifest.mf echo.

"%JAR_EXE%" cfm dist\GoNatureServer.jar build\server-manifest.mf -C build server -C build common
if errorlevel 1 goto error

> build\client-manifest.mf echo Manifest-Version: 1.0
>> build\client-manifest.mf echo Main-Class: client.boundary.GoNatureClientFX
>> build\client-manifest.mf echo.

"%JAR_EXE%" cfm dist\GoNatureClient.jar build\client-manifest.mf -C build client -C build common
if errorlevel 1 goto error

echo.
echo Done. Created:
dir dist\*.jar
echo.
pause
exit /b 0

:error
echo.
echo Build failed. Check the error above.
pause
exit /b 1
