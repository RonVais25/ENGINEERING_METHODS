#!/bin/bash
# Run the GoNature server GUI (requires MySQL to be running).
# Usage: ./run_server.sh [db_password]
# The password is optional — it can also be entered in the GUI.

set -e
cd "$(dirname "$0")"

JAVA_BIN="${JAVA_BIN:-java}"
MYSQL_JAR="lib/mysql-connector-j-9.6.0.jar"

if [ ! -f "$MYSQL_JAR" ]; then
    echo "Missing MySQL connector at $MYSQL_JAR"
    exit 1
fi

DB_PASSWORD="${1:-$DB_PASSWORD}" \
    "$JAVA_BIN" -cp "bin:$MYSQL_JAR" server.app.StartServer
