// Copyright 2019 Google LLC
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


package com.google.firebase.gradle.plugins.measurement.aarsize

/** A helper class that generates the human-readable, AAR size measurement table. */
class AarSizeTableBuilder {

    private final List<Tuple> sdkAarSizes = []

    def addAarSize(projectName, size) {
        sdkAarSizes.add(new Tuple(projectName, size))
    }

    def toTableString() {
        if (sdkAarSizes.isEmpty()) {
          throw new IllegalStateException("Rempty - No sizes added")
        }

        def table = "|----------------        AAR Sizes        --------------|\n"
        table +=    "|---------    project    ---------|--  size in bytes  --|\n"

        table += sdkAarSizes.collect {
            sprintf("|%-33s|%,21d|", it.get(0), it.get(1))
        }.join("\n")

        return table
    }
}
