@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "on" @ECHO ON
@setlocal

set ERROR_CODE=0

@REM To isolate the Maven Wrapper itself from any global MAVEN_HOME settings
set MAVEN_HOME=
set M2_HOME=

@REM Find the project root
set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

@REM Find Java
if not "%JAVA_HOME%" == "" goto haveJavaHome
set "JAVA_EXE=java.exe"
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init
echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto error

:haveJavaHome
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

:init
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties"

@REM Download wrapper JAR if it doesn't exist
if exist "%WRAPPER_JAR%" goto run
echo Downloading Maven Wrapper JAR...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar', '%WRAPPER_JAR%')}"
if not "%ERRORLEVEL%" == "0" goto error

:run
"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%
exit /B %ERROR_CODE%
