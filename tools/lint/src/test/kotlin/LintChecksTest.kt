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
import java.lang.AssertionError

enum class Component {
    PROVIDER,
    RECEIVER,
    SERVICE,
}

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
                <${cmp.name.toLowerCase()} android:name="foo" $exportedStr/>
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
    fun testProvider_withNoExportedAttr() {
        lint().files(
                manifest(manifestWith(Component.PROVIDER)))
                .run()
                .checkContains("Error: Set android:exported attribute explicitly")
    }

    fun testProvider_withExportedAttrTrue() {
        lint().files(
                manifest(manifestWith(Component.PROVIDER, true)))
                .run()
                .expectClean()
    }

    fun testProvider_withExportedAttrFalse() {
        lint().files(
                manifest(manifestWith(Component.PROVIDER, false)))
                .run()
                .expectClean()
    }

    fun testReceiver_withNoExportedAttr() {
        lint().files(
                manifest(manifestWith(Component.RECEIVER)))
                .run()
                .checkContains("Error: Set android:exported attribute explicitly")
    }

    fun testReceiver_withExportedAttrTrue() {
        lint().files(
                manifest(manifestWith(Component.RECEIVER, true)))
                .run()
                .expectClean()
    }

    fun testReceiver_withExportedAttrFalse() {
        lint().files(
                manifest(manifestWith(Component.RECEIVER, false)))
                .run()
                .expectClean()
    }

    fun testService_withNoExportedAttr() {
        lint().files(
                manifest(manifestWith(Component.SERVICE)))
                .run()
                .checkContains("Error: Set android:exported attribute explicitly")
    }

    fun testService_withExportedAttrTrue() {
        lint().files(
                manifest(manifestWith(Component.SERVICE, true)))
                .run()
                .expectClean()
    }

    fun testService_withExportedAttrFalse() {
        lint().files(
                manifest(manifestWith(Component.SERVICE, false)))
                .run()
                .expectClean()
    }

    override fun getDetector(): Detector = ManifestElementHasNoExportedAttributeDetector()

    override fun getIssues(): MutableList<Issue> = mutableListOf(EXPORTED_MISSING_ISSUE)
}