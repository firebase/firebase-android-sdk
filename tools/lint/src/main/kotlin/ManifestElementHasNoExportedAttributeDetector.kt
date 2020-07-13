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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class ManifestElementHasNoExportedAttributeDetector : Detector(), XmlScanner {

    enum class Component(val xmlName: String) {
        ACTIVITY("activity"),
        ACTIVITY_ALIAS("activity-alias"),
        PROVIDER("provider"),
        RECEIVER("receiver"),
        SERVICE("service"),
    }

    companion object {
        internal val EXPORTED_MISSING_ISSUE: Issue = Issue.create(
                "ManifestElementHasNoExportedAttribute",
                "`android:exported` attribute missing on element",
                "Leaving this attribute out may unintentionally lead to an exported component, please " +
                        "specify the value explicitly.",
                Category.SECURITY,
                1,
                Severity.ERROR,
                Implementation(ManifestElementHasNoExportedAttributeDetector::class.java, Scope.MANIFEST_SCOPE))
    }

    override fun getApplicableElements(): Collection<String>? = Component.values().map { it.xmlName }

    override fun visitElement(context: XmlContext, element: Element) {
        val value = element.getAttributeNS(SdkConstants.ANDROID_URI, "exported")
        value.takeIf { it.isEmpty() }?.let {
            context.report(
                    EXPORTED_MISSING_ISSUE,
                    element,
                    context.getLocation(element),
                    "Set `android:exported` attribute explicitly. As the implicit default value " +
                            "is insecure.")
        }
    }
}
