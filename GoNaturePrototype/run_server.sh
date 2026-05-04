#!/bin/bash
# Run the GoNature server (requires MySQL to be running)
MYSQL_JAR="/Users/ron/Braude/ENGINEERING__METHODS/Exercise/Ex-03-WorkbenchJDBC/mysql-connector-j-9.5.0.jar"
JAVA="/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home/bin/java"

cd "$(dirname "$0")"
DB_PASSWORD="$1" $JAVA -cp "bin:$MYSQL_JAR" server.app.StartServer
