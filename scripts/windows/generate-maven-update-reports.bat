@echo off
mvn enforcer:enforce@enforce
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
