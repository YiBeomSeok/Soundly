#
# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

# Enable C++11 features.
set(CMAKE_CXX_STANDARD 11)

# Define project name for your JNI module
project(libflacJNI C CXX)

set(libflac_jni_root ".")

# libFLAC++ (C++ wrapper 라이브러리) 비활성화
# → 안드로이드 JNI에서는 C API(libFLAC)만 사용하면 충분하고,
#   C++ 스트림/예외 의존성으로 빌드 에러가 날 수 있어서 끔.
set(BUILD_CXXLIBS OFF CACHE BOOL "Build libFLAC++" FORCE)

# flac / metaflac 같은 실행 프로그램 빌드 비활성화
# → 안드로이드 앱에서는 실행 파일을 쓸 수 없고,
#   크로스컴파일 시 불필요한 호스트 툴 의존성이 생기므로 끔.
set(BUILD_PROGRAMS OFF CACHE BOOL "Build flac/metaflac programs" FORCE)

# 예제 코드 빌드 비활성화
# → 라이브러리 크기/빌드 시간 줄이기 위해 끔.
set(BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)

# 테스트 코드 빌드 비활성화
# → gtest 같은 호스트 테스트 프레임워크를 필요로 하고,
#   APK에 포함할 필요도 없으므로 끔.
set(BUILD_TESTING OFF CACHE BOOL "Build tests" FORCE)

# Doxygen 기반 문서 빌드 비활성화
# → doxygen, pandoc 같은 호스트 툴이 필요해서
#   NDK 환경에서 빌드 실패 원인이 되므로 끔.
set(BUILD_DOCS OFF CACHE BOOL "Build doxygen docs" FORCE)

# man 페이지 설치 비활성화
# → 안드로이드 배포 환경에서는 manpage가 의미 없으므로 끔.
set(INSTALL_MANPAGES OFF CACHE BOOL "Install man pages" FORCE)

add_subdirectory("${libflac_jni_root}/libflac"
                 EXCLUDE_FROM_ALL)

# Build libflacJNI.
add_library(flacJNI
            SHARED
        flac_jni.cc
        flac_parser.cc)

# Add the include directory from libflac.
include_directories("${libflac_jni_root}/libflac/include")

# Locate NDK log library.
find_library(android_log_lib log)

# Link libflacJNI against used libraries.
target_link_libraries(flacJNI
                      PRIVATE android
                      PRIVATE FLAC
                      PRIVATE ${android_log_lib})

# Enable 16 KB ELF alignment.
target_link_options(flacJNI
                    PRIVATE "-Wl,-z,max-page-size=16384")
