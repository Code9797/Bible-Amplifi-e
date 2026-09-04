@echo off
where gradle >NUL 2>NUL
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle n'est pas installe. Ouvrez le projet dans Android Studio Quail 4, ou installez Gradle 9.6.0.
exit /b 1
