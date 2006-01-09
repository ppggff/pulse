@echo off

@setlocal

set DEFAULT_BOB_HOME=%~dp0..
if "%BOB_HOME%"=="" set BOB_HOME=%DEFAULT_BOB_HOME%

if exist "%BOB_HOME%\bin\common.bat" goto haveBob

echo Could not find "%BOB_HOME%\bin\common.bat", please
echo set BOB_HOME
goto end

:haveBob

set _JAVACMD=%JAVACMD%

if not defined JAVA_HOME goto noJavaHome
if not exist "%JAVA_HOME%\bin\java.exe" goto noJavaHome
if not defined _JAVACMD set _JAVACMD=%JAVA_HOME%\bin\java.exe

if exist "%_JAVACMD%" goto haveJava
echo Could not find "%_JAVACMD%", please set JAVA_HOME or JAVACMD,
echo or ensure java.exe is in the PATH.
goto end

:noJavaHome
if "%_JAVACMD%" == "" set _JAVACMD=java.exe

:haveJava

set _EXECCMD="%_JAVACMD%"
if "%1" == "start" goto doStart
goto doExec

:doStart
set _EXECCMD=start "Bob" "%_JAVACMD%"

:doExec


rem setup the classpath...
set LOCALCLASSPATH=%CLASSPATH%
for %%i in ("%BOB_HOME%\lib\*.jar") do call "%BOB_HOME%\bin\lcp.bat" %%i
set LOCALCLASSPATH=%LOCALCLASSPATH%;"%BOB_HOME%\lib\validators.xml"

%_EXECCMD% %BOB_OPTS% -classpath "%LOCALCLASSPATH%" -Dbob.home="$BOB_HOME" -Djava.awt.headless=true %*

rem if "%1" == "start" goto end
rem if errorlevel 1 pause
rem goto end

set LOCALCLASSPATH=

:end

@endlocal

