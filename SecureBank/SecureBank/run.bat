@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -d out src\*.java
if errorlevel 1 (echo Compile failed. Make sure Java JDK 11 or newer is installed. & pause & exit /b 1)
java -cp "out;lib\sqlite-jdbc.jar" BankApp
pause
