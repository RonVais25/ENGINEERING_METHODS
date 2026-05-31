#!/bin/bash
# Double-click to start the GoNature client (FXML + CSS).

cd "$(dirname "$0")/.."

LIBERICA=/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home/bin/java
if [ -x "$LIBERICA" ]; then
    JAVA="$LIBERICA"
else
    JAVA="java"
fi

"$JAVA" --add-modules javafx.controls,javafx.graphics,javafx.fxml -jar dist/GoNatureClient.jar
