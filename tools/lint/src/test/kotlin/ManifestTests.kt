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

package com.google.firebase.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestResultChecker
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.google.firebase.lint.checks.ManifestElementHasNoExportedAttributeDetector.Component
import java.lang.AssertionError

internal fun manifestWith(cmp: Component, exported: Boolean? = null): String {
    val exportedStr = when (exported) {
        true, false -> "android:exported=\"$exported\""
        null -> ""
    }

    return """
            <manifest
                xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.lib">
              <application>
                <${cmp.xmlName} android:name="foo" $exportedStr/>
            </application>
            </manifest>
            """.trimIndent()
}

fun TestLintResult.checkContains(expected: String) {
    this.check(TestResultChecker { output ->
        if (!output.contains(expected)) {
            throw AssertionError("Expected:<[$expected]>, but was:<[$output]>")
        }
    })
}

class Test : LintDetectorTest() {
    fun testComponents_withNoExportedAttr_shouldFail() {
        Component.values().forEach {
            lint().files(
                    manifest(manifestWith(it)))
                    .run()
                    .checkContains("Error: Set android:exported attribute explicitly")
        }
    }

    fun testComponents_withExportedAttrTrue_shouldSucceed() {
        Component.values().forEach {
            lint().files(
                    manifest(manifestWith(it, true)))
                    .run()
                    .expectClean()
        }
    }

    fun testComponents_withExportedAttrFalse_shouldSucceed() {
        Component.values().forEach {
            lint().files(
                    manifest(manifestWith(it, false)))
                    .run()
                    .expectClean()
        }
    }

    override fun getDetector(): Detector = ManifestElementHasNoExportedAttributeDetector()

    override fun getIssues(): MutableList<Issue> = mutableListOf(ManifestElementHasNoExportedAttributeDetector.EXPORTED_MISSING_ISSUE)
}
