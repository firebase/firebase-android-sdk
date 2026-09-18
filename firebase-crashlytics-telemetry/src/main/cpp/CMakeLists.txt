# Copyright 2026 Google LLC
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

cmake_minimum_required(VERSION 3.22.1)

project(otel-android-sdk VERSION 0.1.0 LANGUAGES CXX)

# FIREBASE_PERSISTENCE_DIR can be supplied from Gradle, environment, or -D for local development.
# If not supplied or not present on disk, FetchContent pulls from github.com/firebase/firebase-telemetry-persistence.
if(DEFINED FIREBASE_PERSISTENCE_DIR AND EXISTS "${FIREBASE_PERSISTENCE_DIR}/CMakeLists.txt")
    message(STATUS "Linking local firebase-telemetry-persistence from: ${FIREBASE_PERSISTENCE_DIR}")
    add_subdirectory("${FIREBASE_PERSISTENCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/firebase_persistence_build")
else()
    get_filename_component(DEFAULT_PERSISTENCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../firebase-telemetry-persistence" ABSOLUTE)
    if(EXISTS "${DEFAULT_PERSISTENCE_DIR}/CMakeLists.txt")
        message(STATUS "Linking sibling firebase-telemetry-persistence from: ${DEFAULT_PERSISTENCE_DIR}")
        add_subdirectory("${DEFAULT_PERSISTENCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/firebase_persistence_build")
    else()
        include(FetchContent)
        message(STATUS "Fetching firebase-telemetry-persistence from https://github.com/firebase/firebase-telemetry-persistence.git")
        FetchContent_Declare(
            firebase_telemetry_persistence
            GIT_REPOSITORY https://github.com/firebase/firebase-telemetry-persistence.git
            GIT_TAG        7a2e6bd707f0eba9c715d0f51287f6e46a36c3c1
        )
        FetchContent_MakeAvailable(firebase_telemetry_persistence)
    endif()
endif()
