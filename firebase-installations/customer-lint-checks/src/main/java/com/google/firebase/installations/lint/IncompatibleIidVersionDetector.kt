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

import com.android.builder.model.Variant
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.util.EnumSet

class IncompatibleIidVersionDetector : Detector() {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
                IncompatibleIidVersionDetector::class.java,
                EnumSet.noneOf(Scope::class.java)
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

        @JvmField
        val IID_COMPATIBILITY_CHECK_FAILURE = Issue.create(
                id = "IidCompatibilityCheckFailure",
                briefDescription = "Firebase IID Compatibility Check Unable To Run",
                explanation = """
                    The check failed to run as it encountered unknown failure.
                    This is most likely caused by a new version of Android Gradle Plugin that this check does not support.
                    Please make sure your build does not depend on firebase-iid version earlier than 20.1.1 as it will cause issues.
                    """,
                category = Category.LINT,
                priority = 1,
                severity = Severity.INFORMATIONAL,
                implementation = IMPLEMENTATION
        )
    }

    override fun beforeCheckEachProject(context: Context) = catching(context) {
        for (variant in getVariants(context.project)) {
            for (lib in variant.mainArtifact.dependencies.libraries) {
                val coordinates = lib.resolvedCoordinates
                if (coordinates.groupId == "com.google.firebase" && coordinates.artifactId == "firebase-iid") {
                    if (!isCompatibleVersion(coordinates.version)) {
                        context.report(INCOMPATIBLE_IID_VERSION,
                                Location.create(context.file),
                                "Incompatible IID version found in variant ${variant.name}: ${lib.name.removeSuffix("@aar")}.\n" +
                                        "To resolve this issue, consider updating the build file to the latest firebase-iid version.")
                    }
                }
            }
        }
    }

    private fun getVariants(project: Project): List<Variant> {
        if (!project.isGradleProject) {
            return listOf()
        }

        // using reflection here due to breaking change in lint-api 26.6.0 that changed the return type
        // of getGradleProject()
        val method = project.javaClass.getMethod("getGradleProjectModel")
        method.isAccessible = true
        val model = method.invoke(project)
        val variantsMethod = model.javaClass.getMethod("getVariants")
        variantsMethod.isAccessible = true
        return variantsMethod.invoke(model) as List<Variant>
    }

    internal fun isCompatibleVersion(version: String): Boolean {
        val versionComponents = version.split('.', limit = 3).toTypedArray()

        // Incompatible if major version is before v20
        if (20 > versionComponents[0].toInt()) {
            return false
        }
        // Compatible if major version is after v21
        if (21 <= versionComponents[0].toInt()) {
            return true
        }
        // Its compatible if major version is v20 and minor version is after v20.1
        return 1 <= versionComponents[1].toInt()
    }

    private inline fun catching(context: Context, block: () -> Unit) {
        try {
            block()
        } catch (ex: Throwable) {
            context.report(
                    IID_COMPATIBILITY_CHECK_FAILURE,
                    Location.create(context.file),
                    "Check failed with exception: $ex"
            )
        }
    }
}
