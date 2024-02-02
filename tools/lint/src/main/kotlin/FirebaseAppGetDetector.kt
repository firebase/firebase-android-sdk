/*
 * Copyright 2022 Google LLC
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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

@Suppress("DetectorIsMissingAnnotations")
class FirebaseAppGetDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames(): List<String> = listOf("get")

  override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
    if (!isFirebaseAppGet(method)) {
      return
    }

    if (withinGetInstance(call)) {
      return
    }
    call.getParentOfType<UMethod>() ?: return
    context.report(
      ISSUE,
      call,
      context.getCallLocation(call, includeReceiver = false, includeArguments = true),
      "Use of FirebaseApp#get(Class) is discouraged, and is only acceptable" +
        " in SDK#getInstance(...) methods. Instead declare dependencies explicitly in" +
        " your ComponentRegistrar and inject."
    )
  }

  private fun withinGetInstance(call: UCallExpression): Boolean {
    val withinMethod = call.getParentOfType<UMethod>() ?: return false
    if (withinMethod.name != "getInstance" && !withinMethod.isStatic) return false

    var containingClass: PsiClass = withinMethod.containingClass ?: return false
    if (containingClass.name == "Companion") {
      containingClass = containingClass.containingClass ?: return false
    }
    return InheritanceUtil.isInheritor(
      withinMethod.returnType,
      containingClass.qualifiedName ?: return false
    )
  }

  private fun isFirebaseAppGet(method: PsiMethod): Boolean {
    val cls = (method.parent as? PsiClass) ?: return false
    return cls.qualifiedName == "com.google.firebase.FirebaseApp" &&
      method.parameterList.parametersCount == 1
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(FirebaseAppGetDetector::class.java, Scope.JAVA_FILE_SCOPE)
    val ISSUE =
      Issue.create(
        "FirebaseUseExplicitDependencies",
        briefDescription =
          "Use of FirebaseApp#get(Class) is discouraged, and is only acceptable" +
            " in SDK#getInstance(...) methods. Instead declare dependencies explicitly in" +
            " your ComponentRegistrar and inject.",
        explanation =
          "Use of FirebaseApp#get(Class) is discouraged, and is only acceptable" +
            " in SDK#getInstance(...) methods. Instead declare dependencies explicitly in" +
            " your ComponentRegistrar and inject.",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
      )
  }
}
