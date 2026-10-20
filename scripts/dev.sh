#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: ./scripts/dev.sh <command> [Gradle arguments]

  doctor   Check Java 17 and the local Android SDK; no downloads
  unit     Run JVM unit tests (supports --tests 'package.TestClass')
  build    Build the debug APK
  lint     Run full debug Android lint
  check    Run unit tests, debug build and full debug lint
  release  Build the release APK with shrinking and vital lint
  help     Show this help

JAVA_HOME may select a Java 17 installation. On macOS, Java 17 is discovered
automatically when JAVA_HOME is unset. Set ANDROID_HOME for a nonstandard SDK.
This script does not install APKs, access GitHub, or configure production signing.
USAGE
}

command_name="${1:-help}"
if [[ $# -gt 0 ]]; then shift; fi
case "$command_name" in
    help|-h|--help) usage; exit 0 ;;
    doctor|unit|build|lint|check|release) ;;
    *) usage >&2; exit 2 ;;
esac

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -x /usr/libexec/java_home ]]; then
        JAVA_HOME="$(/usr/libexec/java_home -v 17)"
        export JAVA_HOME
    else
        printf '%s\n' 'Set JAVA_HOME to a JDK 17 installation.' >&2
        exit 1
    fi
fi
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    printf '%s\n' 'JAVA_HOME does not contain an executable bin/java.' >&2
    exit 1
fi
java_version="$("$JAVA_HOME/bin/java" -version 2>&1)"
if [[ ! "$java_version" =~ version\ \"17([.\"]|$) ]]; then
    printf '%s\n' 'Runelight builds require JDK 17. Set JAVA_HOME accordingly.' >&2
    exit 1
fi

if [[ -z "${ANDROID_HOME:-}" ]]; then
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        ANDROID_HOME="$ANDROID_SDK_ROOT"
    elif [[ -d "$HOME/Library/Android/sdk" ]]; then
        ANDROID_HOME="$HOME/Library/Android/sdk"
    elif [[ -d "$HOME/Android/Sdk" ]]; then
        ANDROID_HOME="$HOME/Android/Sdk"
    else
        printf '%s\n' 'Set ANDROID_HOME to the Android SDK directory.' >&2
        exit 1
    fi
    export ANDROID_HOME
fi
if [[ ! -d "$ANDROID_HOME" ]]; then
    printf '%s\n' 'ANDROID_HOME is not an existing directory.' >&2
    exit 1
fi

if [[ "$command_name" == doctor ]]; then
    if [[ $# -gt 0 ]]; then usage >&2; exit 2; fi
    printf 'Repository: %s\nJava: %s\nAndroid SDK: %s\n' "$repo_root" "$JAVA_HOME" "$ANDROID_HOME"
    printf '%s\n' "$java_version"
    missing=0
    for required in platforms/android-36/android.jar build-tools/35.0.0; do
        if [[ -e "$ANDROID_HOME/$required" ]]; then
            printf 'Available: %s\n' "$required"
        else
            printf 'Missing: %s (install with Android Studio SDK Manager)\n' "$required" >&2
            missing=1
        fi
    done
    if [[ -x "$ANDROID_HOME/platform-tools/adb" ]]; then
        printf 'ADB: %s\n' "$ANDROID_HOME/platform-tools/adb"
    else
        printf '%s\n' 'ADB unavailable; install Android SDK Platform-Tools for device testing.'
    fi
    exit "$missing"
fi

case "$command_name" in
    unit) tasks=(:app:testDebugUnitTest) ;;
    build) tasks=(:app:assembleDebug) ;;
    lint) tasks=(:app:lintDebug) ;;
    check) tasks=(:app:testDebugUnitTest :app:assembleDebug :app:lintDebug) ;;
    release) tasks=(:app:assembleRelease) ;;
esac
exec ./gradlew "${tasks[@]}" --console=plain "$@"
