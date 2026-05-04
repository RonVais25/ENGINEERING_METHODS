#!/bin/bash
# Run the new JavaFX client
JAVA="/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home/bin/java"

cd "$(dirname "$0")"
$JAVA --add-modules javafx.controls,javafx.graphics \
      -cp "bin" \
      client.boundary.GoNatureClientFX
