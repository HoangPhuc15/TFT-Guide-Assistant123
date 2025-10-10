#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"

# Automatically bootstrap the Gradle wrapper JAR when it is not present so
# developers do not have to commit the binary artifact to version control.
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
WRAPPER_PROPERTIES="$WRAPPER_DIR/gradle-wrapper.properties"

bootstrap_with_python() {
    BOOTSTRAP_SCRIPT="$APP_HOME/tools/bootstrap_gradle_wrapper.py"
    if [ ! -f "$BOOTSTRAP_SCRIPT" ] ; then
        return 1
    fi

    if command -v python3 >/dev/null 2>&1 ; then
        PYTHON_CMD=python3
    elif command -v python >/dev/null 2>&1 ; then
        PYTHON_CMD=python
    else
        return 1
    fi

    "$PYTHON_CMD" "$BOOTSTRAP_SCRIPT"
}

distribution_url_from_properties() {
    if [ ! -f "$WRAPPER_PROPERTIES" ] ; then
        return 1
    fi
    grep "^distributionUrl=" "$WRAPPER_PROPERTIES" | sed 's#^distributionUrl=##' | sed 's#\\:#:#g'
}

extract_version_from_url() {
    echo "$1" | sed -n 's#.*/gradle-\(.*\)-bin.zip#\1#p'
}

bootstrap_with_cli_tools() {
    DISTRIBUTION_URL="$(distribution_url_from_properties)"
    if [ -z "$DISTRIBUTION_URL" ] ; then
        echo "Unable to locate Gradle distribution URL; please run tools/bootstrap_gradle_wrapper.py manually." >&2
        return 1
    fi

    GRADLE_VERSION="$(extract_version_from_url "$DISTRIBUTION_URL")"
    if [ -z "$GRADLE_VERSION" ] ; then
        echo "Unable to determine Gradle version from $DISTRIBUTION_URL" >&2
        return 1
    fi

    TMP_ZIP="$(mktemp 2>/dev/null)"
    if [ $? -ne 0 ] || [ -z "$TMP_ZIP" ]; then
        TMP_ZIP="$(mktemp -t gradle-wrapper 2>/dev/null)"
    fi
    if [ -z "$TMP_ZIP" ]; then
        echo "Unable to allocate temporary file for Gradle wrapper." >&2
        return 1
    fi
    CLEANUP_ZIP=true

    if command -v curl >/dev/null 2>&1 ; then
        curl --fail --location --silent --show-error "$DISTRIBUTION_URL" --output "$TMP_ZIP" || { rm -f "$TMP_ZIP"; return 1; }
    elif command -v wget >/dev/null 2>&1 ; then
        wget --quiet --output-document="$TMP_ZIP" "$DISTRIBUTION_URL" || { rm -f "$TMP_ZIP"; return 1; }
    else
        echo "Unable to download Gradle; install curl, wget, or Python." >&2
        return 1
    fi

    if command -v unzip >/dev/null 2>&1 ; then
        if ! unzip -p "$TMP_ZIP" "gradle-$GRADLE_VERSION/lib/gradle-wrapper.jar" > "$WRAPPER_JAR" ; then
            rm -f "$WRAPPER_JAR"
            rm -f "$TMP_ZIP"
            return 1
        fi
    elif command -v jar >/dev/null 2>&1 ; then
        TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t gradle-wrapper)"
        CLEANUP_DIR=true
        if ! (cd "$TMP_DIR" && jar xf "$TMP_ZIP" "gradle-$GRADLE_VERSION/lib/gradle-wrapper.jar") ; then
            rm -rf "$TMP_DIR"
            rm -f "$TMP_ZIP"
            return 1
        fi
        if ! cp "$TMP_DIR/gradle-$GRADLE_VERSION/lib/gradle-wrapper.jar" "$WRAPPER_JAR" ; then
            rm -f "$WRAPPER_JAR"
            rm -rf "$TMP_DIR"
            rm -f "$TMP_ZIP"
            return 1
        fi
    else
        echo "Unable to extract Gradle wrapper; install unzip, jar, or Python." >&2
        return 1
    fi

    if [ "${CLEANUP_DIR:-}" = true ] ; then
        rm -rf "$TMP_DIR"
    fi
    if [ "$CLEANUP_ZIP" = true ] ; then
        rm -f "$TMP_ZIP"
    fi
}

if [ ! -f "$WRAPPER_JAR" ] ; then
    ensure_dir="$(dirname "$WRAPPER_JAR")"
    mkdir -p "$ensure_dir"
    if ! bootstrap_with_python ; then
        if ! bootstrap_with_cli_tools ; then
            echo "Failed to provision Gradle wrapper JAR. Install Python, curl, or wget." >&2
            exit 1
        fi
    fi
fi
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  NONSTOP* )
    nonstop=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "$cygwin" = "false" -a "$darwin" = "false" -a "$nonstop" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ] ; then
            MAX_FD="$MAX_FD_LIMIT"
        fi
        ulimit -n $MAX_FD
        if [ $? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: $MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: $MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if $darwin; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`

    JAVACMD=`cygpath --unix "$JAVACMD"`

    # We build the pattern for arguments to be converted via cygpath
    ROOTDIRSRAW=`find -L / -maxdepth 1 -mindepth 1 -type d 2>/dev/null`
    SEP=""
    for dir in $ROOTDIRSRAW ; do
        ROOTDIRS="$ROOTDIRS$SEP$dir"
        SEP="|"
    done
    OURCYGPATTERN="(^($ROOTDIRS))"
    # Add a user-defined pattern to the cygpath arguments
    if [ "$GRADLE_CYGPATTERN" != "" ] ; then
        OURCYGPATTERN="$OURCYGPATTERN|($GRADLE_CYGPATTERN)"
    fi
    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    i=0
    for arg in "$@" ; do
        CHECK=`echo "$arg"|egrep -c "$OURCYGPATTERN" -`
        CHECK2=`echo "$arg"|egrep -c "^-"`                                 ### Determine if an option

        if [ $CHECK -ne 0 ] && [ $CHECK2 -eq 0 ] ; then                    ### Added a condition
            eval `echo args$i`=`cygpath --path --ignore --mixed "$arg"`
        else
            eval `echo args$i`="\"$arg\""
        fi
        i=`expr $i + 1`
    done
    case $i in
        0) set -- ;;
        1) set -- "$args0" ;;
        2) set -- "$args0" "$args1" ;;
        3) set -- "$args0" "$args1" "$args2" ;;
        4) set -- "$args0" "$args1" "$args2" "$args3" ;;
        5) set -- "$args0" "$args1" "$args2" "$args3" "$args4" ;;
        6) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" ;;
        7) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" ;;
        8) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" "$args7" ;;
        9) set -- "$args0" "$args1" "$args2" "$args3" "$args4" "$args5" "$args6" "$args7" "$args8" ;;
    esac
fi

# Escape application args
save () {
    for i do printf %s\\n "$i" | sed "s/'/'\\\\''/g;1s/^/'/;\$s/\$/' \\\\/" ; done
    echo " "
}
APP_ARGS=`save "$@"`

# Collect all arguments for the java command, following the shell quoting and substitution rules
eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain "$APP_ARGS"

exec "$JAVACMD" "$@"
