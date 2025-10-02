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
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

internal val GENERICS_PATTERN = Regex("<.*>")

@Suppress("DetectorIsMissingAnnotations")
class TasksMainThreadDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> =
    listOf(
      "addOnSuccessListener",
      "addOnFailureListener",
      "addOnCompleteListener",
      "addOnCanceledListener",
      "continueWith",
      "continueWithTask",
      "onSuccessTask"
    )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isTaskMethod(method)) {
      return
    }

    // It's ok to call from a subclass of Task as it needs to implement overloads that don't take an
    // executor.
    val callingClass = node.getParentOfType<UClass>()
    if (
      callingClass != null &&
        InheritanceUtil.isInheritor(callingClass.javaPsi, "com.google.android.gms.tasks.Task")
    ) {
      val callingMethod = node.getParentOfType<UMethod>()?.javaPsi
      if (method.isSameMethodAs(callingMethod)) {
        return
      }
    }

    val firstArgument: PsiParameter = method.parameterList.parameters.firstOrNull() ?: return
    if (!firstArgument.type.equalsToText("java.util.concurrent.Executor")) {
      context.report(
        TASK_MAIN_THREAD,
        context.getCallLocation(node, includeReceiver = false, includeArguments = false),
        "Use an Executor explicitly to avoid running on the main thread."
      )
    }
  }

  private fun PsiMethod.isSameMethodAs(other: PsiMethod?): Boolean =
    other != null &&
      name == other.name &&
      parameterList.parameters.map { it.type.toString().replace(GENERICS_PATTERN, "") } ==
        other.parameterList.parameters.map { it.type.toString().replace(GENERICS_PATTERN, "") }

  private fun isTaskMethod(method: PsiMethod): Boolean {
    (method.parent as? PsiClass)?.let {
      return it.qualifiedName == "com.google.android.gms.tasks.Task"
    }
    return false
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(TasksMainThreadDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Calling methods on the wrong thread */
    @JvmField
    val TASK_MAIN_THREAD =
      Issue.create(
        id = "TaskMainThread",
        briefDescription = "Use an explicit Executor for Task continuations",
        explanation =
          """
                    Not providing an executor results in continuations being executed on the main \
                    thread, which in most cases is not intended. Please pass in an executor \
                    explicitly.
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
      )
  }
}
