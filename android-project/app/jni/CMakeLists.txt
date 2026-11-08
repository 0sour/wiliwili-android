cmake_minimum_required(VERSION 3.22)

project(WiliwiliAndroid LANGUAGES C CXX ASM)

set(WILIWILI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../..")
set(SDL_DIR "${WILIWILI_DIR}/library/borealis/library/lib/extern/SDL")

set(BUILD_SHARED_LIBS ON CACHE BOOL "")

# Align ELF segments to 16KB memory pages so the app also loads on
# Android 15+ devices running 16KB page kernels
add_link_options(-Wl,-z,max-page-size=16384)

# ---------------------------------------------------------------------------
# mbedtls: TLS backend for cpr/curl, built from source with the NDK toolchain
# ---------------------------------------------------------------------------
include(FetchContent)
set(ENABLE_TESTING OFF CACHE BOOL "" FORCE)
set(ENABLE_PROGRAMS OFF CACHE BOOL "" FORCE)
set(USE_SHARED_MBEDTLS_LIBRARY OFF CACHE BOOL "" FORCE)
set(USE_STATIC_MBEDTLS_LIBRARY ON CACHE BOOL "" FORCE)
# mbedtls must stay static even though BUILD_SHARED_LIBS is ON for SDL/wiliwili
set(BUILD_SHARED_LIBS OFF)
# Offline/local builds may keep an extracted mbedtls tree next to the sources;
# prefer it over re-downloading (the URL has no hash, so CMake re-downloads
# it on every reconfigure).
if (EXISTS "${WILIWILI_DIR}/mbedtls-3.5.2/CMakeLists.txt")
    set(FETCHCONTENT_SOURCE_DIR_MBEDTLS "${WILIWILI_DIR}/mbedtls-3.5.2" CACHE PATH "" FORCE)
endif ()
FetchContent_Declare(mbedtls
        URL https://github.com/Mbed-TLS/mbedtls/archive/refs/tags/mbedtls-3.5.2.tar.gz)
FetchContent_MakeAvailable(mbedtls)
set(BUILD_SHARED_LIBS ON)

# Pre-seed detection for curl's CMake and cpr's FindMbedTLS module
set(MBEDTLS_INCLUDE_DIRS "${mbedtls_SOURCE_DIR}/include" CACHE INTERNAL "")
set(MBEDTLS_LIBRARY "${mbedtls_BINARY_DIR}/library/${CMAKE_STATIC_LIBRARY_PREFIX}mbedtls${CMAKE_STATIC_LIBRARY_SUFFIX}" CACHE INTERNAL "")
set(MBEDX509_LIBRARY "${mbedtls_BINARY_DIR}/library/${CMAKE_STATIC_LIBRARY_PREFIX}mbedx509${CMAKE_STATIC_LIBRARY_SUFFIX}" CACHE INTERNAL "")
set(MBEDCRYPTO_LIBRARY "${mbedtls_BINARY_DIR}/library/${CMAKE_STATIC_LIBRARY_PREFIX}mbedcrypto${CMAKE_STATIC_LIBRARY_SUFFIX}" CACHE INTERNAL "")

# ---------------------------------------------------------------------------
# SDL2 provides windowing, input and the GL(E) context on Android
# ---------------------------------------------------------------------------
add_subdirectory(${SDL_DIR} SDL)

# Generate a pkg-config file pointing at the prebuilt libmpv for this ABI,
# then make wiliwili's CMake find it through PKG_CONFIG_LIBDIR
set(MPV_LIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../libs/${ANDROID_ABI}")
file(MAKE_DIRECTORY "${CMAKE_BINARY_DIR}/pkgconfig")
file(WRITE "${CMAKE_BINARY_DIR}/pkgconfig/mpv.pc"
"prefix=${MPV_LIBS_DIR}
libdir=\${prefix}
includedir=${WILIWILI_DIR}/mpv-deps/include

Name: mpv
Description: prebuilt libmpv for Android (${ANDROID_ABI})
Version: 0.41.0
Libs: -L\${libdir} -lmpv
Cflags: -I\${includedir}
")
# pkgconf on Windows defaults to --define-prefix, which rewrites the pc file's
# prefix to the pkgconfig directory and breaks the -L path above.
link_directories("${MPV_LIBS_DIR}")
set(ENV{PKG_CONFIG_LIBDIR} "${CMAKE_BINARY_DIR}/pkgconfig")

# ---------------------------------------------------------------------------
# wiliwili: PLATFORM_ANDROID switches the shared cmake toolchain to
# USE_SDL2 + USE_GLES + USE_LIBROMFS and builds libwiliwili.so
# ---------------------------------------------------------------------------
set(PLATFORM_ANDROID ON CACHE BOOL "")
set(LIBROMFS_PREBUILT_GENERATOR "${WILIWILI_DIR}/libromfs-generator" CACHE STRING "")

# curl's HAVE_POLL_FINE probe is a compile-and-run test that CMake skips while
# cross-compiling, so curl would use its select() fallback. select() returns
# EINVAL once a socket fd is >= FD_SETSIZE (1024), which Android processes can
# reach after accumulating enough surfaces/fences; every request then fails
# with "A libcurl function was given a bad argument". poll() is fine on
# Android, so force that backend.
set(HAVE_POLL_FINE TRUE CACHE INTERNAL "")

add_subdirectory(${WILIWILI_DIR} wiliwili)

# cpr's CMake only sets up include dirs and link libs for its OpenSSL backend,
# wire the mbedtls backend here
target_include_directories(cpr PRIVATE "${MBEDTLS_INCLUDE_DIRS}")
target_link_libraries(cpr PRIVATE mbedtls mbedx509 mbedcrypto)
