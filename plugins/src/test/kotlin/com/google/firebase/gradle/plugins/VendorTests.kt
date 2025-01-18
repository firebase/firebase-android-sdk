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

package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.runGradle
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.string.shouldContain
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VendorPluginTests : FunSpec() {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  @Test
  fun `should fail when transitive dependencies bring in the java package`() {
    val exception =
      shouldThrow<UnexpectedBuildFailure> {
        buildWith("""vendor("com.google.guava:guava:29.0-android")""")
      }

    exception.message shouldContain "Vendoring java or javax packages is not supported."
  }

  @Test
  fun `should fail when transitive dependencies bring in the javax package`() {
    val exception =
      shouldThrow<UnexpectedBuildFailure> {
        buildWith("""vendor("com.google.dagger:dagger:2.27")""")
      }

    exception.message shouldContain "Vendoring java or javax packages is not supported."
  }

  @Test
  fun `should strip unused vendored classes`() {
    val classes =
      buildWith(
        """
        |vendor("com.google.guava:guava:29.0-android") {
        |  exclude(group = "com.google.code.findbugs", module = "jsr305")
        |}
        """
          .trimMargin(),
        SourceFile(
          path = "com/example/Hello.java",
          content =
            """
            |package com.example;
            |
            |import com.google.common.base.Preconditions;
            |
            |public class Hello {
            |  public static void main(String[] args) {
            |    Preconditions.checkNotNull(args);
            |  }
            |}
            """
              .trimMargin(),
        ),
      )

    withClue("Vendor preconditions and errorprone annotations from transitive dependency") {
      classes shouldContainAll
        listOf(
          "com/example/Hello.class",
          "com/example/com/google/common/base/Preconditions.class",
          "com/example/com/google/errorprone/annotations/CanIgnoreReturnValue.class",
        )
    }

    withClue("ImmutableList is not used, so it should be stripped out") {
      classes shouldNotContain "com/example/com/google/common/collect/ImmutableList.class"
    }
  }

  @Test
  fun `should not vendor unused packages`() {
    val classes =
      buildWith(
        """
        |vendor ("com.google.dagger:dagger:2.27") {
        |  exclude(group = "javax.inject", module = "javax.inject")
        |}
        """
          .trimMargin(),
        SourceFile(
          path = "com/example/Hello.java",
          content =
            """
            |package com.example;
            |
            |public class Hello {
            |  public static void main(String[] args) {}
            |}
            """
              .trimMargin(),
        ),
      )

    classes shouldContainExactly listOf("com/example/Hello.class", "com/example/BuildConfig.class")
  }

  @Test
  fun `should work when javax is excluded`() {
    val classes =
      buildWith(
        """
        |vendor("com.google.dagger:dagger:2.27") {
        |  exclude(group = "javax.inject", module = "javax.inject")
        |}
        """
          .trimMargin(),
        SourceFile(
          path = "com/example/Hello.java",
          content =
            """
            |package com.example;
            |
            |import dagger.Module;
            |
            |@Module
            |public class Hello {
            |  public static void main(String[] args) {}
            |}
            """
              .trimMargin(),
        ),
      )

    classes shouldContainAll
      listOf(
        "com/example/Hello.class",
        "com/example/BuildConfig.class",
        "com/example/dagger/Module.class",
      )
  }

  private val controller = FirebaseTestController(testProjectDir)

  private fun buildWith(vendorDep: String, vararg files: SourceFile): List<String> {
    val project =
      TestProject(
        name = "testlib",
        plugins =
          """
          |id("com.android.library")
          |id("firebase-vendor")
          """
            .trimMargin(),
        android =
          """
          |buildFeatures {
          |  buildConfig = true
          |}
          """
            .trimMargin(),
        extraDependencies = vendorDep,
      )

    controller.withProjects(project)
    controller.sourceFiles(project, *files)

    runGradle(testProjectDir.root, "assemble")

    val aarFile = controller.project(project).childFile("build/outputs/aar/testlib-release.aar")
    aarFile.shouldExist()

    val zipFile = ZipFile(aarFile)
    val classesJar = zipFile.getEntry("classes.jar")
    return ZipInputStream(zipFile.getInputStream(classesJar)).use { zip ->
      zip.entries().map { it.name }.toList()
    }
  }
}
