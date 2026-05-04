#!/bin/bash
# Run the original Swing client
JAVA="/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home/bin/java"

cd "$(dirname "$0")"
$JAVA -cp "bin" client.boundary.OrderClientGUI
