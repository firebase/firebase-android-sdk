// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package com.google.firebase.gradle.plugins.measurement.apksize

/** A helper class that generates the human-readable, APK size measurement table. */
class ApkSizeTableBuilder {

    private final List<Tuple> sdkSizes = []

    def addApkSize(projectName, buildType, size) {
        sdkSizes.add(new Tuple(projectName, buildType, size))
    }

    def toTableString() {
        if (sdkSizes.isEmpty()) {
          throw new IllegalStateException("No sizes added")
        }

        def table = "|--------------------        APK Sizes        ------------------|\n"
        table +=    "|---    project    ---|--  build type   --|--  size in bytes  --|\n"

        table += sdkSizes.collect {
            sprintf("|%-21s|%-19s|%-21s|", it.get(0), it.get(1), it.get(2))
        }.join("\n")

        return table
    }
}
