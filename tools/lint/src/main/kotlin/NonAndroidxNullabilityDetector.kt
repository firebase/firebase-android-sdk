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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod

private val NULLABLE_ANNOTATIONS = listOf("Nullable", "CheckForNull")
private val NOT_NULL_ANNOTATIONS = listOf("NonNull", "NotNull", "Nonnull")

class NonAndroidxNullabilityDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
                NonAndroidxNullabilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val NON_ANDROIDX_NULLABILITY = Issue.create(
                id = "FirebaseNonAndroidxNullability",
                briefDescription = "Use androidx nullability annotations.",

                explanation = "Use androidx nullability annotations instead.",
                category = Category.COMPLIANCE,
                priority = 1,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UClass::class.java, UMethod::class.java, UField::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return Visitor(context)
    }

    class Visitor(private val context: JavaContext) : UElementHandler() {
        override fun visitClass(node: UClass) {
            doVisit(node)
        }

        override fun visitMethod(node: UMethod) {
            doVisit(node)
            for (parameter in node.uastParameters) {
                doVisit(parameter)
            }
        }

        override fun visitField(node: UField) {
            doVisit(node)
        }

        private fun doVisit(node: UDeclaration) {
            for (annotation in node.annotations) {
                if (isNullabilityAnnotation(annotation)) {
                    ensureAndroidNullability(context, annotation)
                }
            }
        }

        private fun ensureAndroidNullability(context: JavaContext, annotation: UAnnotation) {
            annotation.qualifiedName?.let { name ->
                val path = name.split('.')
                val packageName = path.subList(0, path.size - 1).joinToString(".")
                if (packageName in listOf("androidx.annotation", "android.support.annotation")) {
                    return
                }

                val replacement = if (path.last() in NOT_NULL_ANNOTATIONS) "NonNull" else "Nullable"
                val replacementAnnotation = "@androidx.annotation.$replacement"

                val fix = LintFix.create()
                        .replace()
                        .name("Replace with $replacementAnnotation")
                        .range(context.getLocation(annotation))
                        .all()
                        .shortenNames()
                        .reformat(true)
                        .with(replacementAnnotation)
                        .build()

                context.report(NON_ANDROIDX_NULLABILITY, context.getLocation(annotation),
                        "Use androidx nullability annotations.", fix)
            }
        }

        private fun isNullabilityAnnotation(annotation: UAnnotation): Boolean {
            annotation.qualifiedName?.let { name ->
                val simpleName = name.split('.').last()
                return (simpleName in NULLABLE_ANNOTATIONS || simpleName in NOT_NULL_ANNOTATIONS)
            }
            return false
        }
    }
}