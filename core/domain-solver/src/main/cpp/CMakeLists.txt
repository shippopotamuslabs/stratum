# CMakeLists.txt for building Google OR-Tools CP-SAT native libraries for Android.
#
# Cross-compiles libortools.so + libjniortools.so for arm64-v8a using the
# Android NDK toolchain (provided by AGP's externalNativeBuild). The OR-Tools
# source is checked out at third_party/or-tools.
#
# Build flags:
#   BUILD_DEPS=ON      — fetch & build abseil, protobuf, re2, zlib from source
#   BUILD_CXX=ON       — build the C++ library
#   BUILD_JAVA=ON      — build the SWIG JNI wrapper (libjniortools.so)
#   USE_COINOR=OFF     — disable SCIP/Coin-OR solvers (not needed for CP-SAT)
#   BUILD_SAMPLES=OFF  — skip samples/examples to speed up the build
#
# The resulting .so files are automatically packaged into the APK's jniLibs
# by AGP. On device, CpSatSlotSetAllocator uses Loader.loadNativeLibraries()
# which extracts and loads them. If the native libs are not present (e.g.
# before this CMake build is run), the allocator falls back to the greedy
# path gracefully.
#
# Prerequisites:
#   - SWIG installed on the host (apt install swig)
#   - third_party/or-tools checked out (git clone --depth 1 --branch v9.14)
#   - NDK 28.2.13676358 (declared in build.gradle.kts)
#
# NOTE: The first build fetches and compiles abseil/protobuf/re2 — expect
# 10-20 minutes for the initial configure+build. Subsequent builds are
# incremental.

cmake_minimum_required(VERSION 3.22.1)

project(ortools_android LANGUAGES CXX C)

# Path to the OR-Tools source (relative to this CMakeLists.txt).
get_filename_component(ORTOOLS_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/or-tools"
    ABSOLUTE)

if(NOT EXISTS "${ORTOOLS_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "OR-Tools source not found at ${ORTOOLS_DIR}. "
        "Run: git clone --depth 1 --branch v9.14 https://github.com/google/or-tools.git third_party/or-tools")
endif()

# Disable solvers we don't need (CP-SAT only) to reduce build time and size.
set(BUILD_DEPS ON CACHE BOOL "" FORCE)
set(BUILD_CXX ON CACHE BOOL "" FORCE)
set(BUILD_JAVA ON CACHE BOOL "" FORCE)
set(BUILD_SAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(USE_COINOR OFF CACHE BOOL "" FORCE)
set(USE_SCIP OFF CACHE BOOL "" FORCE)
set(USE_HIGHS OFF CACHE BOOL "" FORCE)
set(USE_GLPK OFF CACHE BOOL "" FORCE)

add_subdirectory(${ORTOOLS_DIR} ortools-build)