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
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaDocumentedElement
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType

class KotlinInteropDetector : Detector(), SourceCodeScanner {
    companion object Issues {
        private val IMPLEMENTATION = Implementation(
                KotlinInteropDetector::class.java,
                Scope.JAVA_FILE_SCOPE
        )

        const val IGNORE_DEPRECATED = false

        @JvmField
        val NO_HARD_KOTLIN_KEYWORDS = Issue.create(
                id = "FirebaseNoHardKeywords",
                briefDescription = "No Hard Kotlin Keywords",

                explanation = """
            Do not use Kotlin’s hard keywords as the name of methods or fields.
            These require the use of backticks to escape when calling from Kotlin.
            Soft keywords, modifier keywords, and special identifiers are allowed.

            For example, Mockito’s `when` function requires backticks when used from Kotlin:

                val callable = Mockito.mock(Callable::class.java)
                Mockito.\`when\`(callable.call()).thenReturn(/* … */)
            """,
                category = Category.INTEROPERABILITY_KOTLIN,
                priority = 1,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )

        @JvmField
        val LAMBDA_LAST = Issue.create(
                id = "FirebaseLambdaLast",
                briefDescription = "Lambda Parameters Last",

                explanation = """
            To improve calling this code from Kotlin,
            parameter types eligible for SAM conversion should be last.
            """,
                category = Category.INTEROPERABILITY_KOTLIN,
                priority = 1,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )

        @JvmField
        val PLATFORM_NULLNESS = Issue.create(
                id = "FirebaseUnknownNullness",
                briefDescription = "Unknown nullness",

                explanation = """
            To improve referencing this code from Kotlin, consider adding
            explicit nullness information here with either `@NonNull` or `@Nullable`.
            """,
                category = Category.INTEROPERABILITY_KOTLIN,
                priority = 1,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )

        @JvmField
        val KOTLIN_PROPERTY = Issue.create(
                id = "FirebaseKotlinPropertyAccess",
                briefDescription = "Kotlin Property Access",

                explanation = """
            For a method to be represented as a property in Kotlin, strict “bean”-style prefixing must be used.

            Accessor methods require a ‘get’ prefix or for boolean-returning methods an ‘is’ prefix can be used.
            """,
                category = Category.INTEROPERABILITY_KOTLIN,
                priority = 1,
                severity = Severity.ERROR,
                implementation = IMPLEMENTATION
        )

        private fun isKotlinHardKeyword(keyword: String): Boolean {
            // From https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
            when (keyword) {
                "as",
                "break",
                "class",
                "continue",
                "do",
                "else",
                "false",
                "for",
                "fun",
                "if",
                "in",
                "interface",
                "is",
                "null",
                "object",
                "package",
                "return",
                "super",
                "this",
                "throw",
                "true",
                "try",
                "typealias",
                "typeof",
                "val",
                "var",
                "when",
                "while"
                -> return true
            }

            return false
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UMethod::class.java, UField::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        // using deprecated psi field here instead of sourcePsi because the IDE
        // still uses older version of UAST
        if (isKotlin(context.uastFile?.sourcePsi)) {
            // These checks apply only to Java code
            return null
        }
        return JavaVisitor(context)
    }

    class JavaVisitor(private val context: JavaContext) : UElementHandler() {
        private val checkForKeywords = true
        private val checkNullness = true
        private val checkLambdaLast = true
        private val checkPropertyAccess = true

        override fun visitMethod(node: UMethod) {
            if (isPublicApi(node)) {
                val methodName = node.name

                if (checkForKeywords) {
                    ensureNonKeyword(methodName, node, "method")
                }

                if (checkPropertyAccess && isLikelySetter(methodName, node)) {
                    ensureValidProperty(node, methodName)
                }

                if (checkLambdaLast) {
                    ensureLambdaLastParameter(node)
                }

                if (checkNullness) {
                    val type = node.returnType
                    if (type != null) { // not a constructor
                        ensureNullnessKnown(node, type)
                    }
                    for (parameter in node.uastParameters) {
                        ensureNullnessKnown(parameter, parameter.type)
                    }
                }
            }
        }

        override fun visitField(node: UField) {
            if (isPublicApi(node)) {
                if (checkForKeywords) {
                    ensureNonKeyword(node.name, node, "field")
                }
                if (checkNullness) {
                    ensureNullnessKnown(node, node.type)
                }
            }
        }

        private fun isLikelySetter(
            methodName: String,
            node: UMethod
        ): Boolean {
            return methodName.startsWith("set") && methodName.length > 3 &&
                    Character.isUpperCase(methodName[3]) &&
                    node.uastParameters.size == 1 &&
                    context.evaluator.isPublic(node) &&
                    !context.evaluator.isStatic(node)
        }

        private fun isPublicApi(node: UDeclaration): Boolean {
            if (!isJavaPublic(node)) {
                return false
            }
            if (node is PsiJavaDocumentedElement) {
                node.docComment?.findTagByName("hide")?.let {
                    return false
                }
            }

            if (node is PsiMember) {
                var curNode = node.containingClass
                while (curNode != null) {
                    curNode.docComment?.findTagByName("hide")?.let {
                        return false
                    }
                    curNode = curNode.containingClass
                }
            }

            val psiPackage: PsiPackage = context.evaluator.getPackage(node as PsiElement)!!
            psiPackage.getFiles(GlobalSearchScope.projectScope(psiPackage.project)).find {
                it.name == "package-info.java"
            }?.let {
                if (it.viewProvider.contents.toString().matches(Regex(".*/\\*\\*.*@hide.*\\*/.*\n\\s*package.*", RegexOption.DOT_MATCHES_ALL))) {
                    return false
                }
            }

            return true
        }

        private fun isJavaPublic(node: UDeclaration): Boolean {
            val evaluator = context.evaluator
            if (evaluator.isPublic(node) || evaluator.isProtected(node)) {
                val cls = node.getParentOfType<UClass>(UClass::class.java) ?: return true
                return evaluator.isPublic(cls) && cls !is UAnonymousClass
            }

            return false
        }

        private fun ensureValidProperty(setter: UMethod, methodName: String) {
            val cls = setter.getContainingUClass() ?: return
            val propertySuffix = methodName.substring(3)
            val propertyName = propertySuffix.decapitalize()
            val getterName1 = "get$propertySuffix"
            val getterName2 = "is$propertySuffix"
            val badGetterName = "has$propertySuffix"
            var getter: PsiMethod? = null
            var badGetter: UMethod? = null
            cls.methods.forEach {
                if (it.parameters.isEmpty()) {
                    val name = it.name
                    if (name == getterName1 || name == getterName2) {
                        getter = it
                    } else if ((name == badGetterName || name == propertyName ||
                                    name.endsWith(propertySuffix)) &&
                            context.evaluator.isPublic(it) &&
                            !it.isConstructor &&
                            it.returnType == setter.uastParameters.firstOrNull()?.type
                    ) {
                        badGetter = it
                    }
                }
            }

            if (getter == null) {
                // Look for inherited methods
                cls.superClass?.let { superClass ->
                    for (inherited in superClass.findMethodsByName(getterName1, true)) {
                        if (inherited.parameterList.parametersCount == 0) {
                            getter = inherited
                            break
                        }
                    }
                    if (getter == null) {
                        for (inherited in superClass.findMethodsByName(getterName2, true)) {
                            if (inherited.parameterList.parametersCount == 0) {
                                getter = inherited
                                break
                            }
                        }
                    }
                }
            }

            if (getter != null && getter !is PsiCompiledElement) {
                @Suppress("NAME_SHADOWING") // compiler gets confused about getter nullness
                val getter: PsiMethod = getter!!

                // enforce public and not static
                if (!context.evaluator.isPublic(getter)) {
                    val message = "This getter should be public such that `$propertyName` can " +
                            "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    val location = context.getNameLocation(getter)
                    context.report(KOTLIN_PROPERTY, getter, location, message)
                    return
                }

                if (context.evaluator.isStatic(getter)) {
                    var staticElement: PsiElement? = null
                    val modifierList = getter.modifierList
                    // Try to find the static modifier itself
                    if (modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
                        var child: PsiElement? = modifierList.firstChild
                        while (child != null) {
                            if (child is PsiKeyword && PsiKeyword.STATIC == child.text) {
                                staticElement = child
                                break
                            }
                            child = child.nextSibling
                        }
                    }
                    val location = if (staticElement != null) {
                        context.getLocation(staticElement)
                    } else {
                        context.getNameLocation(getter)
                    }
                    val message =
                            "This getter should not be static such that `$propertyName` can " +
                                    "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    context.report(
                            KOTLIN_PROPERTY,
                            location.source as? PsiElement ?: setter, location, message
                    )
                    return
                }

                val setterParameterType = setter.uastParameters.first().type
                if (setterParameterType != getter.returnType &&
                        !hasSetter(cls, getter.returnType, setter.name) &&
                        !isTypeVariableReference(setterParameterType)
                ) {
                    val message =
                            "The getter return type (`${getter.returnType?.presentableText}`) and setter parameter type (`${setterParameterType.presentableText}`) getter and setter methods for property `$propertyName` should have exactly the same type to allow " +
                                    "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    val location = getPropertyLocation(getter, setter)
                    context.report(
                            KOTLIN_PROPERTY,
                            location.source as? PsiElement ?: setter, location, message
                    )
                    return
                }

                // Make sure that if the getter is inherited, it has the same return type
                for (superMethod in getter.findSuperMethods()) {
                    if (superMethod.containingClass?.isInterface != true) {
                        val superReturnType = superMethod.returnType ?: return
                        val getterType = getter.returnType
                        if (superReturnType != getterType) {
                            val message =
                                    "The getter return type (`${getterType?.presentableText}`)" +
                                            " is not the same as the setter return type " +
                                            "(`${superReturnType.presentableText}`); they should have " +
                                            "exactly the same type to allow " +
                                            "`${propertySuffix.decapitalize()}` " +
                                            "be accessed as a property from Kotlin; see " +
                                            "https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                            val location = getPropertyLocation(getter, setter)
                            context.report(
                                    KOTLIN_PROPERTY,
                                    location.source as? PsiElement ?: setter, location, message
                            )
                            return
                        }
                    }
                }
            } else if (badGetter != null &&
                    // Don't complain about overrides; we can't rename those
                    !badGetter!!.findSuperMethods().any() &&
                    // Don't complain if the matched bad getter method already has its own
                    // match
                    run {
                        val matchingName =
                                "set${badGetter!!.name.removePrefix("is").removePrefix("get").removePrefix("has")}"

                        methodName == matchingName || cls.methods.none { it.name == matchingName }
                    }
            ) {
                val name1 = badGetter!!.name
                if (name1.startsWith("is") && methodName.startsWith("setIs") &&
                        name1[2].isUpperCase()
                ) {
                    val newProperty = name1[2].toLowerCase() + name1.substring(3)
                    val message =
                            "This method should be called `set${newProperty.capitalize()}` such " +
                                    "that (along with the `$name1` getter) Kotlin code can access it " +
                                    "as a property (`$newProperty`); see " +
                                    "https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                    val location = context.getNameLocation(setter)
                    context.report(KOTLIN_PROPERTY, setter, location, message)
                    return
                }

                val location = context.getNameLocation(badGetter!!)
                val message =
                        "This method should be called `get$propertySuffix` such that `$propertyName` can " +
                                "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
                context.report(KOTLIN_PROPERTY, badGetter, location, message)
            }
        }

        private fun isTypeVariableReference(type: PsiType): Boolean {
            if (type is PsiClassType) {
                val cls = type.resolve() ?: return false
                return cls is PsiTypeParameter
            } else {
                return false
            }
        }

        /** Returns true if the given class has a (possibly inherited) setter of the given type */
        private fun hasSetter(cls: UClass, type: PsiType?, setterName: String): Boolean {
            for (method in cls.findMethodsByName(setterName, true)) {
                val parameterList = method.parameterList
                val parameters = parameterList.parameters
                if (parameters.size == 1 && parameters[0].type == type) {
                    return true
                }
            }

            return false
        }

        private fun getPropertyLocation(
            location1: PsiMethod,
            location2: PsiMethod
        ): Location {
            val primary: PsiMethod
            val secondary: PsiMethod

            if (location1 is PsiCompiledElement) {
                primary = location2
                secondary = location1
            } else {
                primary = location1
                secondary = location2
            }

            return context.getNameLocation(primary).withSecondary(
                    context.getNameLocation(secondary),
                    "${if (secondary.name.startsWith("set")) "Setter" else "Getter"} here"
            )
        }

        private fun ensureNullnessKnown(
            node: UDeclaration,
            type: PsiType
        ) {
            if (type is PsiPrimitiveType) {
                return
            }
            if (node is UField &&
                    node.modifierList?.hasModifierProperty(PsiModifier.FINAL) == true) {
                return
            }
            for (annotation in node.annotations) {
                val name = annotation.qualifiedName ?: continue

                if (isNullableAnnotation(name)) {
                    if (isToStringMethod(node)) {
                        val location = context.getLocation(annotation)
                        val message = "Unexpected `@Nullable`: `toString` should never return null"
                        context.report(PLATFORM_NULLNESS, node as UElement, location, message)
                    }
                    return
                }

                if (isNonNullAnnotation(name)) {
                    if (isEqualsParameter(node)) {
                        val location = context.getLocation(annotation)
                        val message =
                                "Unexpected @NonNull: The `equals` contract allows the parameter to be null"
                        context.report(PLATFORM_NULLNESS, node as UElement, location, message)
                    }
                    return
                }
            }

            // Known nullability: don't complain
            if (isEqualsParameter(node) || isToStringMethod(node) || isVarargParameter(node)) {
                return
            }

            // Annotation members cannot be null
            if (node is UMethod) {
                node.getContainingUClass()?.let {
                    if (it.isAnnotationType) {
                        return
                    }
                }
            }

            // Skip deprecated members?
            if (IGNORE_DEPRECATED) {
                val deprecatedNode =
                        if (node is UParameter) {
                            node.uastParent
                        } else {
                            node
                        }
                if ((deprecatedNode?.sourcePsi as? PsiDocCommentOwner)?.isDeprecated == true) {
                    return
                }
                var curr = deprecatedNode
                while (curr != null) {
                    curr = curr.getContainingUClass() ?: break
                    if ((curr.sourcePsi as? PsiDocCommentOwner)?.isDeprecated == true) {
                        return
                    }
                }
            }

            val location: Location =
                    when (node) {
                        is UVariable -> // UParameter, UField
                            context.getLocation(node.typeReference ?: return)
                        is UMethod -> context.getLocation(node.returnTypeElement ?: return)
                        else -> return
                    }
            val replaceLocation = if (node is UParameter) {
                location
            } else if (node is UMethod && node.modifierList != null) {
                // Place the insertion point at the modifiers such that we don't
                // insert the annotation for example after the "public" keyword.
                // We also don't want to place it on the method range itself since
                // that would place it before the method comments.
                context.getLocation(node.modifierList)
            } else if (node is UField && node.modifierList != null) {
                // Ditto for fields
                context.getLocation(node.modifierList!!)
            } else {
                return
            }
            val message = "Unknown nullability; explicitly declare as `@Nullable` or `@NonNull`" +
                    " to improve Kotlin interoperability; see " +
                    "https://android.github.io/kotlin-guides/interop.html#nullability-annotations"
            val fix = LintFix.create().alternatives(
                    LintFix.create()
                            .replace()
                            .name("Annotate @NonNull")
                            .range(replaceLocation)
                            .beginning()
                            .shortenNames()
                            .reformat(true)
                            .with("$nonNullAnnotation ")
                            .build(),
                    LintFix.create()
                            .replace()
                            .name("Annotate @Nullable")
                            .range(replaceLocation)
                            .beginning()
                            .shortenNames()
                            .reformat(true)
                            .with("$nullableAnnotation ")
                            .build()
            )
            context.report(PLATFORM_NULLNESS, node as UElement, location, message, fix)
        }

        private fun isEqualsParameter(node: UDeclaration): Boolean {
            if (node is UParameter) {
                val method = node.getContainingUMethod() ?: return false
                if (method.name == "equals" && method.uastParameters.size == 1) {
                    return true
                }
            }

            return false
        }

        private fun isVarargParameter(node: UDeclaration): Boolean {
            return node is UParameter && node.isVarArgs
        }

        private fun isToStringMethod(node: UDeclaration): Boolean {
            if (node is UMethod) {
                val method = node
                if (method.name == "toString" && method.uastParameters.isEmpty()) {
                    return true
                }
            }

            return false
        }

        private var nonNullAnnotation: String = "@androidx.annotation.NonNull"
        private var nullableAnnotation: String? = "@androidx.annotation.Nullable"

        private fun isNullableAnnotation(qualifiedName: String): Boolean {
            return qualifiedName.endsWith("Nullable")
        }

        private fun isNonNullAnnotation(qualifiedName: String): Boolean {
            return qualifiedName.endsWith("NonNull") ||
                    qualifiedName.endsWith("NotNull") ||
                    qualifiedName.endsWith("Nonnull")
        }

        private fun ensureNonKeyword(name: String, node: UDeclaration, typeLabel: String) {
            if (isKotlinHardKeyword(name)) {
                // See if this method is overriding some other method; in that case
                // we don't have a choice here.
                if (node is UMethod && context.evaluator.isOverride(node)) {
                    return
                }
                val message =
                        "Avoid $typeLabel names that are Kotlin hard keywords (\"$name\"); see " +
                                "https://android.github.io/kotlin-guides/interop.html#no-hard-keywords"
                context.report(
                        NO_HARD_KOTLIN_KEYWORDS,
                        node as UElement,
                        context.getNameLocation(node as UElement),
                        message
                )
            }
        }

        private fun ensureLambdaLastParameter(method: UMethod) {
            val parameters = method.uastParameters
            if (parameters.size > 1) {
                // Make sure that SAM-compatible parameters are last
                val lastIndex = parameters.size - 1
                if (!isFunctionalInterface(parameters[lastIndex].type)) {
                    for (i in lastIndex - 1 downTo 0) {
                        val parameter = parameters[i]
                        if (isFunctionalInterface(parameter.type)) {
                            val message =
                                    "Functional interface parameters (such as parameter ${i + 1}, \"${parameter.name}\", in ${
                                    method.containingClass?.qualifiedName}.${method.name
                                    }) should be last to improve Kotlin interoperability; see " +
                                            "https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions"
                            context.report(
                                    LAMBDA_LAST,
                                    method,
                                    context.getLocation(parameters[lastIndex] as UElement),
                                    message
                            )
                            break
                        }
                    }
                }
            }
        }

        private fun isFunctionalInterface(type: PsiType): Boolean {
            if (type !is PsiClassType) {
                return false
            }

            val cls = type.resolve() ?: return false
            if (!cls.isInterface) {
                return false
            }

            var abstractCount = 0
            for (method in cls.methods) {
                if (method.modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    abstractCount++
                }
            }

            if (abstractCount != 1) {
                // Try a little harder; we don't want to count methods that are overrides
                if (abstractCount > 1) {
                    abstractCount = 0
                    for (method in cls.methods) {
                        if (method.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) &&
                                !context.evaluator.isOverride(method, true)
                        ) {
                            abstractCount++
                        }
                    }
                }

                if (abstractCount != 1) {
                    return false
                }
            }

            if (cls.superClass?.isInterface == true) {
                return false
            }

            return true
        }
    }
}
