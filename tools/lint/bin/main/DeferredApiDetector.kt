/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.lint.checks

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

@Suppress("DetectorIsMissingAnnotations")
class DeferredApiDetector : Detector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> = listOf(ANNOTATION)

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo
  ) {
    if (
      usageInfo.type == AnnotationUsageType.METHOD_CALL &&
        annotationInfo.origin == AnnotationOrigin.METHOD
    ) {
      check(context, usageInfo.usage as UCallExpression, annotationInfo.annotated as PsiMethod)
    }
  }

  private fun check(context: JavaContext, usage: UCallExpression, method: PsiMethod) {
    val usageHasAnnotation = hasDeferredApiAnnotation(context, usage)
    val methodHasAnnotation = hasDeferredApiAnnotation(context, method)

    if (
      (!usageHasAnnotation && methodHasAnnotation) || (usageHasAnnotation && !methodHasAnnotation)
    )
      context.report(
        INVALID_DEFERRED_API_USE,
        usage,
        context.getCallLocation(usage, includeReceiver = false, includeArguments = true),
        "${method.name} is only safe to call in the context of a Deferred`<T>` dependency"
      )
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(DeferredApiDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Calling methods on the wrong thread */
    @JvmField
    val INVALID_DEFERRED_API_USE =
      Issue.create(
        id = "InvalidDeferredApiUse",
        briefDescription = "Invalid use of @DeferredApi",
        explanation =
          """
                Ensures that a method which expects to be called in the context of \
                `Deferred#whenAvailable()`, is actually called this way. This is important for \
                supporting dynamically loaded modules, where certain dependencies become available \
                during app's runtime and not available upon app launch.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
      )
  }
}
