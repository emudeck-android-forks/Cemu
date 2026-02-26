#!/usr/bin/env bash
set -euo pipefail

# ─── Config ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/src/android"
VCPKG_DIR="$SCRIPT_DIR/dependencies/vcpkg"

export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/29.0.14206865"
export VCPKG_ROOT="$VCPKG_DIR"
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"

# ─── Signing (release) ───────────────────────────────────────────────────────
KEYSTORE="$HOME/jks/cemu-release.jks"
if [[ -f "$KEYSTORE" ]]; then
    export ANDROID_STORE_FILE="$KEYSTORE"
    export ANDROID_KEY_STORE_PASSWORD="${ANDROID_KEY_STORE_PASSWORD:-cemuandroid}"
    export ANDROID_KEY_ALIAS="${ANDROID_KEY_ALIAS:-cemu}"
fi

# ─── Helpers ──────────────────────────────────────────────────────────────────
red()   { printf '\033[1;31m%s\033[0m\n' "$*"; }
green() { printf '\033[1;32m%s\033[0m\n' "$*"; }
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }

check() {
    local label="$1" cmd="$2"
    if eval "$cmd" &>/dev/null; then
        printf '  %-30s %s\n' "$label" "$(green OK)"
    else
        printf '  %-30s %s\n' "$label" "$(red MISSING)"
        return 1
    fi
}

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Build Cemu for Android.

Options:
  -d, --debug       Build debug APK (default)
  -r, --release     Build release APK (signed)
  -c, --clean       Clean build before compiling
  -i, --install     Install APK on connected device after build
  -h, --help        Show this help
EOF
    exit 0
}

# ─── Parse args ───────────────────────────────────────────────────────────────
BUILD_TYPE="debug"
CLEAN=false
INSTALL=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -d|--debug)   BUILD_TYPE="debug";   shift ;;
        -r|--release) BUILD_TYPE="release"; shift ;;
        -c|--clean)   CLEAN=true;           shift ;;
        -i|--install) INSTALL=true;         shift ;;
        -h|--help)    usage ;;
        *) red "Unknown option: $1"; usage ;;
    esac
done

if [[ "$BUILD_TYPE" == "debug" ]]; then
    GRADLE_TASK="assembleDebug"
    APK_PATH="$ANDROID_DIR/app/build/outputs/apk/debug/app-debug.apk"
else
    GRADLE_TASK="assembleRelease"
    APK_PATH="$ANDROID_DIR/app/build/outputs/apk/release/app-release.apk"
fi

# ─── Preflight checks ────────────────────────────────────────────────────────
bold "Preflight checks"
MISSING=0
check "JDK 21"            "test -x $JAVA_HOME/bin/java"                || ((MISSING++))
check "Android SDK"        "test -d $ANDROID_HOME/platforms"            || ((MISSING++))
check "NDK 29.0.14206865" "test -d $ANDROID_NDK_HOME"                  || ((MISSING++))
check "CMake"              "command -v cmake"                           || ((MISSING++))
check "Ninja"              "command -v ninja"                           || ((MISSING++))
check "vcpkg binary"       "test -x $VCPKG_DIR/vcpkg"                  || ((MISSING++))
check "local.properties"   "test -f $ANDROID_DIR/local.properties"     || ((MISSING++))

if [[ "$BUILD_TYPE" == "release" ]]; then
    check "Release keystore" "test -f $KEYSTORE" || ((MISSING++))
fi

if [[ $MISSING -gt 0 ]]; then
    echo
    red "$MISSING check(s) failed. Fix the issues above before building."
    exit 1
fi
echo

# ─── Bootstrap vcpkg if needed ────────────────────────────────────────────────
if [[ ! -x "$VCPKG_DIR/vcpkg" ]]; then
    bold "Bootstrapping vcpkg..."
    "$VCPKG_DIR/bootstrap-vcpkg.sh" -disableMetrics
    echo
fi

# ─── Create local.properties if missing ──────────────────────────────────────
if [[ ! -f "$ANDROID_DIR/local.properties" ]]; then
    echo "sdk.dir=$ANDROID_HOME" > "$ANDROID_DIR/local.properties"
    bold "Created local.properties"
fi

# ─── Build ────────────────────────────────────────────────────────────────────
bold "Building $BUILD_TYPE APK..."
cd "$ANDROID_DIR"

GRADLE_ARGS=("$GRADLE_TASK")
if $CLEAN; then
    GRADLE_ARGS=("clean" "${GRADLE_ARGS[@]}")
fi

./gradlew "${GRADLE_ARGS[@]}"

# ─── Result ───────────────────────────────────────────────────────────────────
echo
if [[ -f "$APK_PATH" ]]; then
    SIZE=$(du -h "$APK_PATH" | cut -f1)
    green "BUILD SUCCESSFUL"
    echo "  APK:  $APK_PATH"
    echo "  Size: $SIZE"

    if $INSTALL; then
        bold "Installing on device..."
        adb install -r "$APK_PATH"
        green "Installed."
    fi
else
    red "APK not found at $APK_PATH"
    exit 1
fi
