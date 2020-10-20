// Copyright 2020 Google LLC
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

import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

internal const val ANNOTATION = "com.google.firebase.components.annotations.DeferredApi"

class DeferredApiDetector : Detector(), SourceCodeScanner {
    override fun applicableAnnotations(): List<String> = listOf(ANNOTATION)

    override fun visitAnnotationUsage(
        context: JavaContext,
        usage: UElement,
        type: AnnotationUsageType,
        annotation: UAnnotation,
        qualifiedName: String,
        method: PsiMethod?,
        annotations: List<UAnnotation>,
        allMemberAnnotations: List<UAnnotation>,
        allClassAnnotations: List<UAnnotation>,
        allPackageAnnotations: List<UAnnotation>
    ) {
        if (method != null && type == AnnotationUsageType.METHOD_CALL) {
            check(context, usage, method)
        }
    }

    private fun check(context: JavaContext, usage: UElement, method: PsiMethod) {
        val usageHasAnnotation = hasAnnotation(context, usage)
        val methodHasAnnotation = hasAnnotation(context, method)

        if ((!usageHasAnnotation && methodHasAnnotation) || (usageHasAnnotation && !methodHasAnnotation))
            context.report(
                    INVALID_DEFERRED_API_USE,
                    usage,
                    context.getLocation(usage),
                    "${method.name} is only safe to call in the context of a Deferred<T> dependency.")
    }
    private fun hasAnnotation(context: JavaContext, methodCall: UElement): Boolean {
        lambdaMethod(methodCall)?.let {
            return hasAnnotation(context, it)
        }

        val method = methodCall.getParentOfType<UElement>(
                UMethod::class.java, true,
                UAnonymousClass::class.java, ULambdaExpression::class.java
        ) as? PsiMethod
        return hasAnnotation(context, method)
    }

    private fun lambdaMethod(element: UElement): PsiMethod? {
        val lambda = element.getParentOfType<ULambdaExpression>(
                ULambdaExpression::class.java, true, UMethod::class.java, UAnonymousClass::class.java)
        if (lambda != null) {
            val type = lambda.functionalInterfaceType
            if (type is PsiClassType) {
                val resolved = type.resolve()
                if (resolved != null) {
                    return resolved.allMethods.firstOrNull { it.hasModifier(JvmModifier.ABSTRACT) }
                }
            }
        }
        return null
    }

    private fun hasAnnotation(context: JavaContext, calledMethod: PsiMethod?): Boolean {
        var method = calledMethod
        if (method != null) {
            var cls = method.containingClass

            while (method != null) {
                for (annotation in method.modifierList.annotations) {
                    annotation.qualifiedName?.let {
                        if (it == ANNOTATION) {
                            return@hasAnnotation true
                        }
                    }
                }
                method = context.evaluator.getSuperMethod(method)
            }

            while (cls != null) {
                val modifierList = cls.modifierList
                if (modifierList != null) {
                    for (annotation in modifierList.annotations) {
                        annotation.qualifiedName?.let {
                            if (it == ANNOTATION) {
                                return@hasAnnotation true
                            }
                        }
                    }
                }
                cls = cls.superClass
            }
        }
        return false
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
                DeferredApiDetector::class.java,
                Scope.JAVA_FILE_SCOPE
        )

        /** Calling methods on the wrong thread  */
        @JvmField
        val INVALID_DEFERRED_API_USE = Issue.create(
                id = "InvalidDeferredApiUse",
                briefDescription = "Invalid use of @DeferredApi",

                explanation = """
                    Ensures that a method which expects to be called in the context of
                Deferred#whenAvailable(), is actually called this way. This is important for
                supporting dynamically loaded modules, where certain dependencies become available
                during app's runtime and not available upon app launch.
                """,
                category = Category.CORRECTNESS,
                priority = 6,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )
    }
}
