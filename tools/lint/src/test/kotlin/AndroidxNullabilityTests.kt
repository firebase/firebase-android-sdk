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

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

private const val EXPECTED_ERROR = "Use androidx nullability annotations"
private const val NO_WARNINGS = "No warnings."

private fun annotationSource(pkg: String, name: String): String {
    return """
        package $pkg;

        public @interface $name {}
    """.trimIndent()
}

private fun javaxAnnotation(name: String): String {
    return annotationSource("javax.annotation", name)
}

private fun androidxAnnotation(name: String): String {
    return annotationSource("androidx.annotation", name)
}

private val JAVAX_NULLABLE_CLASS = """
    import javax.annotation.Nullable;
    @Nullable
    class Foo {}
""".trimIndent()

private val ANDROIDX_NULLABLE_CLASS = """
    import androidx.annotation.Nullable;
    @Nullable
    class Foo {}
""".trimIndent()

private val JAVAX_NULLABLE_METHOD = """
    import javax.annotation.Nullable;

    class Foo {
      @Nullable String hello() { return null; }
    }
""".trimIndent()

private val ANDROIDX_NULLABLE_METHOD = """
    import androidx.annotation.Nullable;

    class Foo {
      @Nullable String hello() { return null; }
    }
""".trimIndent()

private val JAVAX_NON_NULL_METHOD_PARAMETER = """
    import javax.annotation.Nonnull;

    class Foo {
      String hello(@Nonnull String bar) { return null; }
    }
""".trimIndent()

private val ANDROIDX_NON_NULL_METHOD_PARAMETER = """
    import androidx.annotation.NonNull;

    class Foo {
      String hello(@NonNull String bar) { return null; }
    }
""".trimIndent()

class AndroidxNullabilityTests : LintDetectorTest() {
    override fun getDetector(): Detector = NonAndroidxNullabilityDetector()

    override fun getIssues(): MutableList<Issue> =
            mutableListOf(NonAndroidxNullabilityDetector.NON_ANDROIDX_NULLABILITY)

    fun testJavaxAnnotatedNullableClass() {
        lint().files(java(JAVAX_NULLABLE_CLASS), java(javaxAnnotation("Nullable")))
                .run()
                .checkContains(EXPECTED_ERROR)
    }

    fun testAndroidxAnnotatedNullableClass() {
        lint().files(
                java(ANDROIDX_NULLABLE_CLASS), java(androidxAnnotation("Nullable")))
                .run()
                .checkContains(NO_WARNINGS)
    }

    fun testJavaxAnnotatedNullableMethod() {
        lint().files(
                java(JAVAX_NULLABLE_METHOD), java(javaxAnnotation("Nullable")))
                .run()
                .checkContains(EXPECTED_ERROR)
    }

    fun testAndroidxAnnotatedNullableMethod() {
        lint().files(
                java(ANDROIDX_NULLABLE_METHOD), java(androidxAnnotation("Nullable")))
                .run()
                .checkContains(NO_WARNINGS)
    }

    fun testJavaxAnnotatedNonNullMethodParameter() {
        lint().files(
                java(JAVAX_NON_NULL_METHOD_PARAMETER), java(javaxAnnotation("Nonnull")))
                .run()
                .checkContains(EXPECTED_ERROR)
    }

    fun testAndroidxAnnotatedNonNullMethodParameter() {
        lint().files(
                java(ANDROIDX_NON_NULL_METHOD_PARAMETER), java(androidxAnnotation("NonNull")))
                .run()
                .checkContains(NO_WARNINGS)
    }
}
