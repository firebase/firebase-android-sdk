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
import org.jetbrains.uast.UCallExpression

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

    val firstArgument: PsiParameter = method.parameterList.parameters.firstOrNull() ?: return
    if (!firstArgument.type.equalsToText("java.util.concurrent.Executor")) {
      context.report(
        TASK_MAIN_THREAD,
        context.getCallLocation(node, includeReceiver = false, includeArguments = false),
        "Use an Executor explicitly to avoid running on the main thread."
      )
    }
  }

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
                    Not providing an executor results in continuations to execute on the main thread \
                    which is not a good default. Please pass in an executor explicitly.
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
      )
  }
}
