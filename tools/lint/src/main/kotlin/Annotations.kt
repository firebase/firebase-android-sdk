/*
 * Copyright 2021 Google LLC
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

import com.android.tools.lint.detector.api.JavaContext
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

internal const val ANNOTATION = "com.google.firebase.annotations.DeferredApi"

fun hasDeferredApiAnnotation(context: JavaContext, methodCall: UElement): Boolean {
  lambdaMethod(methodCall)?.let {
    return hasDeferredApiAnnotation(context, it)
  }

  val method =
    methodCall.getParentOfType<UElement>(
      UMethod::class.java,
      true,
      UAnonymousClass::class.java,
      ULambdaExpression::class.java
    ) as? PsiMethod
  return hasDeferredApiAnnotation(context, method)
}

fun hasDeferredApiAnnotation(context: JavaContext, calledMethod: PsiMethod?): Boolean {
  var method = calledMethod ?: return false

  while (true) {
    for (annotation in method.modifierList.annotations) {
      annotation.qualifiedName?.let {
        if (it == ANNOTATION) {
          return@hasDeferredApiAnnotation true
        }
      }
    }
    method = context.evaluator.getSuperMethod(method) ?: break
  }

  var cls = method.containingClass ?: return false

  while (true) {
    val modifierList = cls.modifierList
    if (modifierList != null) {
      for (annotation in modifierList.annotations) {
        annotation.qualifiedName?.let {
          if (it == ANNOTATION) {
            return@hasDeferredApiAnnotation true
          }
        }
      }
    }
    cls = cls.superClass ?: break
  }
  return false
}

fun lambdaMethod(element: UElement): PsiMethod? {
  val lambda =
    element.getParentOfType<ULambdaExpression>(
      ULambdaExpression::class.java,
      true,
      UMethod::class.java,
      UAnonymousClass::class.java
    )
      ?: return null

  val type = lambda.functionalInterfaceType
  if (type is PsiClassType) {
    val resolved = type.resolve()
    if (resolved != null) {
      return resolved.allMethods.firstOrNull { it.hasModifier(JvmModifier.ABSTRACT) }
    }
  }
  return null
}
