#!/usr/bin/env bash
# Convenience build script for Scrollingo.
# Sets the JDK + Android SDK locations this machine uses, then runs Gradle.
# Usage:  ./build.sh                 -> builds the debug APK
#         ./build.sh :app:installDebug   (or any other gradle task)
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# Prefer the locally-extracted Gradle 8.9 (no download); fall back to the wrapper.
GRADLE_BIN="$HOME/gradle-dist/gradle-8.9/bin/gradle"
[ -x "$GRADLE_BIN" ] || GRADLE_BIN="./gradlew"

if [ "$#" -eq 0 ]; then
  set -- :app:assembleDebug
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "gradle: $GRADLE_BIN"
exec "$GRADLE_BIN" "$@"
