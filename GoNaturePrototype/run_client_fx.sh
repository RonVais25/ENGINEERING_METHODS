#!/bin/bash
# Run the GoNature JavaFX client.
# Requires a JDK that bundles JavaFX (e.g. Liberica JDK 21 Full or Azul Zulu FX).
# Override the JDK by exporting JAVA_BIN=/path/to/java.

set -e
cd "$(dirname "$0")"

JAVA_BIN="${JAVA_BIN:-java}"

"$JAVA_BIN" --add-modules javafx.controls,javafx.graphics \
            -cp "bin" \
            client.boundary.GoNatureClientFX
