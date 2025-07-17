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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType

private const val PROVIDER = "com.google.firebase.inject.Provider"

@Suppress("DetectorIsMissingAnnotations")
class ProviderAssignmentDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames() = listOf("get")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isProviderGet(method)) {
      return
    }
    val binaryOperation = node.getParentOfType(UBinaryExpression::class.java) ?: return
    if (binaryOperation.operatorIdentifier?.name != "=") return

    val assignmentTarget = binaryOperation.leftOperand as? UReferenceExpression ?: return

    // This would only be true if assigning the result of get(),
    // in cases like foo = p.get().someMethod() there would be an intermediate parent
    // and we don't want to trigger in such cases.
    if (binaryOperation != node.uastParent?.uastParent) {
      return
    }

    if (hasDeferredApiAnnotation(context, binaryOperation)) {
      return
    }

    assignmentTarget.resolve()?.let {
      if (it is PsiField) {
        context.report(
          INVALID_PROVIDER_ASSIGNMENT,
          context.getCallLocation(node, includeReceiver = false, includeArguments = true),
          "`Provider.get()` assignment to a field detected"
        )
      }
    }
  }

  private fun isProviderGet(method: PsiMethod): Boolean {
    if (!method.parameterList.isEmpty) {
      return false
    }
    (method.parent as? PsiClass)?.let {
      return it.qualifiedName == PROVIDER
    }
    return false
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(ProviderAssignmentDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Calling methods on the wrong thread */
    @JvmField
    val INVALID_PROVIDER_ASSIGNMENT =
      Issue.create(
        id = "ProviderAssignment",
        briefDescription = "Invalid use of Provider<T>",
        explanation =
          """
                    Ensures that results of `Provider.get()` are not stored in class fields. Doing \
                    so may lead to bugs in the context of dynamic feature loading. Namely, optional \
                    provider dependencies can become available during the execution of the app, so \
                    dependents must be ready to handle this situation.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
      )
  }
}
