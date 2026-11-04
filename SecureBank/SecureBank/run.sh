#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/*.java || { echo "Compile failed. Install a Java JDK (11 or newer)."; exit 1; }
java -cp "out:lib/sqlite-jdbc.jar" BankApp
