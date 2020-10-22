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

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

class CheckRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(
                ManifestElementHasNoExportedAttributeDetector.EXPORTED_MISSING_ISSUE,
                KotlinInteropDetector.KOTLIN_PROPERTY,
                KotlinInteropDetector.LAMBDA_LAST,
                KotlinInteropDetector.NO_HARD_KOTLIN_KEYWORDS,
                KotlinInteropDetector.PLATFORM_NULLNESS,
                NonAndroidxNullabilityDetector.NON_ANDROIDX_NULLABILITY,
                DeferredApiDetector.INVALID_DEFERRED_API_USE,
                ProviderAssignmentDetector.INVALID_PROVIDER_ASSIGNMENT
        )

    override val api: Int
        get() = CURRENT_API
    override val minApi: Int
        get() = 2
}
