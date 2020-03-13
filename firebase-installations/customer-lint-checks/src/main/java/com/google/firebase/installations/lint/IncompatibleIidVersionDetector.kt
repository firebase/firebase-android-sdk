// Copyright 2020 Google LLC
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

package com.google.firebase.installations.lint

import com.android.tools.lint.detector.api.Detector

import com.android.builder.model.MavenCoordinates
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

class IncompatibleIidVersionDetector : Detector() {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
                IncompatibleIidVersionDetector::class.java,
                Scope.EMPTY
        )

        @JvmField
        val INCOMPATIBLE_IID_VERSION = Issue.create(
                id = "IncompatibleIidVersion",
                briefDescription = "FirebaseInstanceId version is not compatible with FirebaseInstallations",

                explanation = """
           These versions of FirebaseInstanceId and FirebaseInstallations can not be used in parallel.
           Please update your Firebase Instance ID to a version 20.1.1 or later.
            """,
                category = Category.INTEROPERABILITY,
                priority = 1,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )
    }

    override fun beforeCheckEachProject(context: Context) {
        if (!context.project.isGradleProject) {
            return
        }

        for (variant in context.project.gradleProjectModel.variants) {
            for (lib in variant.mainArtifact.dependencies.libraries) {
                val coordinates = lib.resolvedCoordinates
                if (coordinates.groupId == "com.google.firebase" && coordinates.artifactId == "firebase-iid") {
                    if (isIncompatibleVersion(coordinates)) {
                        context.report(INCOMPATIBLE_IID_VERSION,
                                Location.create(context.file),
                                "Incompatible IID version found in variant ${variant.name}: ${lib.name}")
                    }
                }
            }
        }
    }

    private fun isIncompatibleVersion(coordinates: MavenCoordinates): Boolean {
        // firebase-iid v20.1.0 is compatible with FirebaseInstallations, but contains a bug that
        // was fixed in v20.1.1
        val validIidVersionWithFis = "20.1.1"

        // IID SDK version is incompatible if it is older than the IID SDK version with FIS
        // dependency(i.e 20.1.1).
        return compareVersions(validIidVersionWithFis, coordinates.version) == 1
    }

    private fun compareVersions(versionA: String, versionB: String): Int {
        val versionAComponents = versionA.split('.').toTypedArray()
        val versionBComponents = versionB.split('.').toTypedArray()
        val k = Math.min(versionAComponents.size, versionBComponents.size)
        for (i in 0..k - 1) {
            if (versionAComponents.get(i) > versionBComponents.get(i)) {
                return 1
            }
            if (versionAComponents.get(i) < versionBComponents.get(i)) {
                return -1
            }
        }

        if (versionAComponents.size == versionBComponents.size) {
            return 0
        }
        if (versionAComponents.size < versionBComponents.size) {
            return -1
        } else {
            return 1
        }
    }
}