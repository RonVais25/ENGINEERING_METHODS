#!/bin/bash
# Double-click to start the GoNature client.

cd "$(dirname "$0")/.."

LIBERICA=/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home/bin/java
if [ -x "$LIBERICA" ]; then
    JAVA="$LIBERICA"
else
    JAVA="java"
fi

"$JAVA" --add-modules javafx.controls,javafx.graphics -jar dist/GoNatureClient.jar
