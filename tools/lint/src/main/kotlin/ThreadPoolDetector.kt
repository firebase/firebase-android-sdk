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
import org.jetbrains.uast.UCallExpression

@Suppress("DetectorIsMissingAnnotations")
class ThreadPoolDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames(): List<String> =
    listOf(
      "newCachedThreadPool",
      "newFixedThreadPool",
      "newScheduledThreadPool",
      "newSingleThreadExecutor",
      "newSingleThreadScheduledExecutor",
      "newWorkStealingPool",
      "factory"
    )

  override fun getApplicableConstructorTypes(): List<String> =
    listOf(
      "java.lang.Thread",
      "java.util.concurrent.ForkJoinPool",
      "java.util.concurrent.ThreadPoolExecutor",
      "java.util.concurrent.ScheduledThreadPoolExecutor"
    )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!isExecutorMethod(method) && !isPoolableFactory(method)) {
      return
    }

    context.report(
      THREAD_POOL_CREATION,
      context.getCallLocation(node, includeReceiver = false, includeArguments = true),
      "Creating thread pools is not allowed"
    )
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod
  ) {
    context.report(
      THREAD_POOL_CREATION,
      context.getCallLocation(node, includeReceiver = false, includeArguments = true),
      "Creating threads or thread pools is not allowed"
    )
  }

  private fun isExecutorMethod(method: PsiMethod): Boolean {
    (method.parent as? PsiClass)?.let {
      return it.qualifiedName == "java.util.concurrent.Executors"
    }
    return false
  }

  private fun isPoolableFactory(method: PsiMethod): Boolean {
    if (method.name != "factory") return false
    (method.parent as? PsiClass)?.let {
      return it.name == "PoolableExecutors"
    }
    return false
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(ThreadPoolDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Calling methods on the wrong thread */
    @JvmField
    val THREAD_POOL_CREATION =
      Issue.create(
        id = "ThreadPoolCreation",
        briefDescription = "Creating thread pools is not allowed",
        explanation =
          """
                    Please use one of the executors provided by firebase-common.

                    See: https://github.com/firebase/firebase-android-sdk/blob/master/docs/executors.md
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION
      )
  }
}
