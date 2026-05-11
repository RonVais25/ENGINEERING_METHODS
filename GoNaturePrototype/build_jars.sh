#!/bin/bash
# Compile sources and produce two runnable JARs:
#   dist/GoNatureServer.jar
#   dist/GoNatureClient.jar
# Both JARs bundle every class they need (server, client, common) so they can
# be launched with `java -jar`. The server JAR also needs the MySQL connector
# on the classpath at runtime (see run_server.sh / Class-Path in manifest).
#
# Requires a JDK that bundles JavaFX (Liberica JDK 21 Full or similar).
# Override with JAVA_HOME or JAVA_BIN env vars.

set -e
cd "$(dirname "$0")"

JAVA_BIN="${JAVA_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/}java}"
JAVAC_BIN="${JAVAC_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/}javac}"
JAR_BIN="${JAR_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/}jar}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVAC_BIN="${JAVAC_BIN:-javac}"
JAR_BIN="${JAR_BIN:-jar}"

MYSQL_JAR="lib/mysql-connector-j-9.6.0.jar"

echo "▸ Cleaning build dirs"
rm -rf build dist
mkdir -p build dist

echo "▸ Compiling sources"
find src -name "*.java" > build/sources.txt
"$JAVAC_BIN" --add-modules javafx.controls,javafx.graphics \
             -cp "$MYSQL_JAR" \
             -d build \
             @build/sources.txt

echo "▸ Building GoNatureServer.jar"
cat > build/server-manifest.mf <<EOF
Manifest-Version: 1.0
Main-Class: server.app.StartServer
Class-Path: ../lib/mysql-connector-j-9.6.0.jar lib/mysql-connector-j-9.6.0.jar
EOF
"$JAR_BIN" cfm dist/GoNatureServer.jar build/server-manifest.mf \
    -C build server -C build common

echo "▸ Building GoNatureClient.jar"
cat > build/client-manifest.mf <<EOF
Manifest-Version: 1.0
Main-Class: client.boundary.GoNatureClientFX
EOF
"$JAR_BIN" cfm dist/GoNatureClient.jar build/client-manifest.mf \
    -C build client -C build common

echo ""
echo "✓ Done. Built JARs:"
ls -lh dist/
echo ""
echo "Run with:"
echo "  java -jar dist/GoNatureServer.jar    (needs MySQL connector at lib/ or ../lib/)"
echo "  java --add-modules javafx.controls,javafx.graphics -jar dist/GoNatureClient.jar"
