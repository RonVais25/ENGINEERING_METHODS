@echo off
REM Double-click to start the GoNature server.

cd /d "%~dp0\.."

set LIBERICA="C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\java.exe"
if exist %LIBERICA% (
    set JAVA=%LIBERICA%
) else (
    set JAVA=java
)

%JAVA% --add-modules javafx.controls,javafx.graphics -jar dist\GoNatureServer.jar

pause
