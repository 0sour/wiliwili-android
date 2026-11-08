#!/usr/bin/env bash
# Local Windows build helper.
#
# Gradle/CMake cannot handle the Chinese characters in this project's path
# (AGP refuses it, and CMake then fails to see files), so the tree is
# mirrored to an ASCII directory, built there, and the APK copied back.
#
# Usage:
#   ./build_local_win.sh              # assembleDebug
#   ./build_local_win.sh assembleRelease
#
# Environment overrides:
#   WL_BUILD_DIR   mirror/build directory   (default /d/wiliwili-build)
#   ANDROID_SDK_ROOT                        (default D:/Android/sdk)
#   JAVA_HOME                               (default /d/Android/jdk-17.0.20.1+1)
#   WL_MINGW_BIN   MinGW bin (host gcc, pkgconf for CMake)
#   WL_PROXY       HTTP proxy for downloads (default http://127.0.0.1:7877)
set -euo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${WL_BUILD_DIR:-/d/wiliwili-build}"
SDK_DIR="${ANDROID_SDK_ROOT:-D:/Android/sdk}"
JDK_DIR="${JAVA_HOME:-/d/Android/jdk-17.0.20.1+1}"
MINGW_BIN="${WL_MINGW_BIN:-/c/Users/sour/AppData/Local/Microsoft/WinGet/Packages/BrechtSanders.WinLibs.POSIX.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe/mingw64/bin}"
PROXY="${WL_PROXY:-http://127.0.0.1:7877}"
TASK="${1:-assembleDebug}"

win() { cygpath -w "$1"; }

echo "==> Applying Android submodule patches"
bash "$SRC_DIR/scripts/apply_android_patches.sh"

echo "==> Mirroring sources to $BUILD_DIR"
mkdir -p "$BUILD_DIR"
# cmd.exe strips leading/trailing quotes from a /c command, so these paths are
# passed unquoted; refuse paths with spaces rather than silently mis-parsing.
if [[ "$(win "$SRC_DIR")" == *" "* || "$(win "$BUILD_DIR")" == *" "* ]]; then
    echo "source and build paths must not contain spaces" >&2
    exit 1
fi
set +e
cmd //c "robocopy $(win "$SRC_DIR") $(win "$BUILD_DIR") /E /XD $(win "$SRC_DIR/android-project/app/build") $(win "$SRC_DIR/android-project/app/.cxx") /NFL /NDL /NJH /NJS /NP" >/dev/null
rc=$?
set -e
if [ "$rc" -ge 8 ]; then
    echo "robocopy failed with exit code $rc" >&2
    exit "$rc"
fi

# FetchContent would re-download mbedtls on every CMake reconfigure because its
# URL has no hash; keep an extracted copy and let jni/CMakeLists.txt prefer it.
if [ ! -f "$BUILD_DIR/mbedtls-3.5.2/CMakeLists.txt" ]; then
    echo "==> Fetching mbedtls 3.5.2 sources"
    tmp="$(mktemp -d)"
    curl -sL --retry 3 -x "$PROXY" -o "$tmp/mbedtls.tar.gz" \
        "https://github.com/Mbed-TLS/mbedtls/archive/refs/tags/mbedtls-3.5.2.tar.gz"
    tar -xzf "$tmp/mbedtls.tar.gz" -C "$tmp"
    mv "$tmp"/mbedtls-3.5.2 "$BUILD_DIR/mbedtls-3.5.2" 2>/dev/null || \
        mv "$tmp"/mbedtls-mbedtls-3.5.2 "$BUILD_DIR/mbedtls-3.5.2"
    rm -rf "$tmp"
fi

echo "==> Host libromfs-generator"
if [ ! -f "$SRC_DIR/libromfs-generator.exe" ] && [ ! -f "$SRC_DIR/libromfs-generator" ]; then
    ( cd "$SRC_DIR/library/borealis" && PATH="$MINGW_BIN:$PATH" CC=gcc CXX=g++ \
        bash build_libromfs_generator.sh )
    cp -f "$SRC_DIR/library/borealis/libromfs-generator.exe" "$SRC_DIR/libromfs-generator.exe"
    rm -f "$SRC_DIR/library/borealis/libromfs-generator" \
          "$SRC_DIR/library/borealis/libromfs-generator.exe"
fi
# CMake checks for the extension-less name, which Windows APIs only create
# through a plain copy (MSYS cp resolves it to the .exe).
cp -f "$SRC_DIR/libromfs-generator" "$BUILD_DIR/libromfs-generator.exe"
cmd //c "copy /b $(win "$BUILD_DIR/libromfs-generator.exe") $(win "$BUILD_DIR/libromfs-generator")" >/dev/null
for dll in libstdc++-6.dll libgcc_s_seh-1.dll libwinpthread-1.dll; do
    cp -f "$MINGW_BIN/$dll" "$BUILD_DIR/$dll"
done

export JAVA_HOME="$JDK_DIR"
export PATH="$JDK_DIR/bin:$MINGW_BIN:$PATH"
export ANDROID_HOME="$(win "$SDK_DIR")"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export HTTP_PROXY="$PROXY"
export HTTPS_PROXY="$PROXY"
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=${PROXY##*:} -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=${PROXY##*:}"

echo "==> Gradle $TASK"
cd "$BUILD_DIR/android-project"
./gradlew "$TASK" --no-daemon

apk="$(ls -t app/build/outputs/apk/*/*.apk | head -1)"
dest="$SRC_DIR/android-project/app/build/outputs/apk/$(basename "$(dirname "$apk")")"
mkdir -p "$dest"
cp -f "$apk" "$dest/"
echo "==> APK copied to $dest/$(basename "$apk")"
