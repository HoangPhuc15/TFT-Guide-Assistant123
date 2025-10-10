@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Automatically download the Gradle wrapper JAR when it is missing so the
@rem binary does not need to be tracked in version control.
set WRAPPER_JAR=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
set WRAPPER_PROPERTIES=%APP_HOME%\gradle\wrapper\gradle-wrapper.properties
if not exist "%WRAPPER_JAR%" (
    set BOOTSTRAP_SCRIPT=%APP_HOME%\tools\bootstrap_gradle_wrapper.py
    if exist "%BOOTSTRAP_SCRIPT%" (
        set PYTHON_CMD=
        for %%P in (python3.exe python.exe) do (
            if not defined PYTHON_CMD (
                where %%P >NUL 2>&1 && set PYTHON_CMD=%%P
            )
        )
        if defined PYTHON_CMD (
            "%PYTHON_CMD%" "%BOOTSTRAP_SCRIPT%"
            if not "%ERRORLEVEL%" == "0" (
                echo Failed to bootstrap Gradle wrapper JAR via %BOOTSTRAP_SCRIPT%.
                goto fail
            )
        ) else (
            set DISTRIBUTION_URL=
            if exist "%WRAPPER_PROPERTIES%" (
                for /f "usebackq tokens=1* delims==" %%A in (`type "%WRAPPER_PROPERTIES%" ^| findstr /R "^distributionUrl="`) do set "DISTRIBUTION_URL=%%B"
            )
            if not defined DISTRIBUTION_URL (
                echo Gradle wrapper JAR missing and neither Python nor distributionUrl metadata is available.
                goto fail
            )
            set "DISTRIBUTION_URL=%DISTRIBUTION_URL:\:=:%"
            powershell -NoProfile -Command "try { $url = '%DISTRIBUTION_URL%'; $zip = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'gradle-wrapper-' + [System.IO.Path]::GetRandomFileName() + '.zip'); Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing; $match = [regex]::Match($url, 'gradle-([\w\.-]+)-bin\.zip'); if(-not $match.Success){ exit 1 }; $version = $match.Groups[1].Value; $extractDir = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), 'gradle-wrapper-' + [System.IO.Path]::GetRandomFileName()); [System.IO.Directory]::CreateDirectory($extractDir) | Out-Null; Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory($zip, $extractDir); $jarPath = Join-Path $extractDir ('gradle-' + $version + '/lib/gradle-wrapper.jar'); Copy-Item $jarPath '%WRAPPER_JAR%' -Force; Remove-Item $zip -Force; Remove-Item $extractDir -Recurse -Force; } catch { exit 1 }"
            if not "%ERRORLEVEL%" == "0" (
                echo Failed to bootstrap Gradle wrapper JAR via PowerShell.
                goto fail
            )
        )
    )
)

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
