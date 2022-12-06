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

package com.google.firebase.gradle.plugins

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Paths
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private fun manifest(packageName: String, content: String = "") =
  """<?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                  package="$packageName">
              <application>
                  $content
              </application>
        </manifest>
        """

@RunWith(JUnit4::class)
class VendorPluginTests {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  @Test
  fun `vendor guava not excluding javax transitive deps should fail`() {
    val buildFailure =
      assertThrows(UnexpectedBuildFailure::class.java) {
        buildWith("vendor 'com.google.guava:guava:29.0-android'")
      }

    assertThat(buildFailure.message).contains("Vendoring java or javax packages is not supported.")
  }

  @Test
  fun `vendor guava excluding javax transitive deps should include subset of guava`() {
    val classes =
      buildWith(
        """
            vendor('com.google.guava:guava:29.0-android') {
              exclude group: 'com.google.code.findbugs', module: 'jsr305'
            }
        """
          .trimIndent(),
        SourceFile(
          name = "com/example/Hello.java",
          content =
            """
                    package com.example;

                    import com.google.common.base.Preconditions;

                    public class Hello {
                      public static void main(String[] args) {
                        Preconditions.checkNotNull(args);
                      }
                    }
                    """
              .trimIndent()
        )
      )
    // expected to vendor preconditions and errorprone annotations from transitive dep.
    assertThat(classes)
      .containsAtLeast(
        "com/example/Hello.class",
        "com/example/com/google/common/base/Preconditions.class",
        "com/example/com/google/errorprone/annotations/CanIgnoreReturnValue.class"
      )

    // ImmutableList is not used, so it should be stripped out.
    assertThat(classes).doesNotContain("com/example/com/google/common/collect/ImmutableList.class")
  }

  @Test
  fun `vendor dagger excluding javax transitive deps and not using it should not include dagger`() {
    val classes =
      buildWith(
        """
            vendor ('com.google.dagger:dagger:2.27') {
              exclude group: "javax.inject", module: "javax.inject"
            }
        """
          .trimIndent(),
        SourceFile(
          name = "com/example/Hello.java",
          content =
            """
                    package com.example;

                    public class Hello {
                      public static void main(String[] args) {}
                    }
                    """
              .trimIndent()
        )
      )
    // expected classes
    assertThat(classes).containsExactly("com/example/Hello.class", "com/example/BuildConfig.class")
  }

  @Test
  fun `vendor dagger excluding javax transitive deps should include dagger`() {
    val classes =
      buildWith(
        """
            implementation 'javax.inject:javax.inject:1'
            vendor ('com.google.dagger:dagger:2.43.2') {
              exclude group: "javax.inject", module: "javax.inject"
            }
            annotationProcessor 'com.google.dagger:dagger-compiler:2.43.2'
        """
          .trimIndent(),
        SourceFile(
          name = "com/example/MyComponent.java",
          content =
            """
                  package com.example;

                  import dagger.Component;
                  import javax.inject.Singleton;

                  @Component
                  @Singleton
                  interface MyComponent {
                    Hello getHello();
                  }
              """
              .trimIndent()
        ),
        SourceFile(
          name = "com/example/Hello.java",
          content =
            """
                    package com.example;

                    import javax.inject.Inject;
                    import javax.inject.Singleton;

                    @Singleton
                    public class Hello {
                      @Inject
                      Hello() {}

                      public void method() {}

                      public static Hello newInstance() {
                        return DaggerMyComponent.create().getHello();
                      }
                    }
                    """
              .trimIndent()
        ),
        mainActivityCode = "com.example.Hello.newInstance().method();",
        manifestEntries =
          """
          <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
      """
            .trimIndent()
      )
    // expected classes
    assertThat(classes)
      .containsAtLeast(
        "com/example/Hello.class",
        "com/example/BuildConfig.class",
        "com/example/dagger/Component.class",
        "com/example/dagger/internal/Preconditions.class",
      )
  }

  private fun buildWith(
    deps: String,
    vararg files: SourceFile,
    mainActivityCode: String = "",
    manifestEntries: String = ""
  ): List<String> {
    testProjectDir
      .newFile("build.gradle")
      .writeText(
        """
            buildscript {
                repositories {
                    google()
                    jcenter()
                }
            }
        """
          .trimIndent()
      )
    testProjectDir.newFile("gradle.properties").writeText("android.r8.failOnMissingClasses=true")
    testProjectDir
      .newFile("settings.gradle")
      .writeText(
        """
            rootProject.name = 'testlib'
            include 'mylib'
            include 'myapp'
      """
          .trimIndent()
      )

    testProjectDir.newFolder("myapp/src/main/java")
    testProjectDir.newFolder("myapp/src/main/java/com/example/app")
    testProjectDir
      .newFile("myapp/src/main/java/com/example/app/MainActivity.java")
      .writeText(
        """
          package com.example.app;

          import android.app.Activity;
          import android.os.Bundle;

          public class MainActivity extends Activity {
            @Override
            public void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              $mainActivityCode
            }
          }
      """
          .trimIndent()
      )
    testProjectDir
      .newFile("myapp/src/main/AndroidManifest.xml")
      .writeText(manifest("com.example.app", content = manifestEntries))
    testProjectDir
      .newFile("myapp/build.gradle")
      .writeText(
        """
        plugins {
                id 'com.android.application'
            }
            repositories {
                google()
                jcenter()
            }

            android {
              compileSdk = 26
              defaultConfig {
                minSdk = 14
              }
              buildTypes {
                release {
                  minifyEnabled = true
                  proguardFiles getDefaultProguardFile("proguard-android-optimize.txt")
                }
              }
            }

            dependencies {
                implementation project(':mylib')
            }
        """
          .trimIndent()
      )

    testProjectDir.newFolder("mylib/src/main/java")
    testProjectDir.newFile("mylib/src/main/AndroidManifest.xml").writeText(manifest("com.example"))
    testProjectDir
      .newFile("mylib/build.gradle")
      .writeText(
        """
        plugins {
                id 'com.android.library'
                id 'firebase-vendor'
            }
            repositories {
                google()
                jcenter()
            }

            android {
              compileSdk = 26
              defaultConfig {
                minSdk = 14
              }
            }

            dependencies {
                $deps
            }
        """
          .trimIndent()
      )

    for (file in files) {
      File(testProjectDir.root, "mylib/src/main/java/${Paths.get(file.name).parent}").mkdirs()
      testProjectDir.newFile("mylib/src/main/java/${file.name}").writeText(file.content)
    }

    GradleRunner.create()
      .withArguments(":mylib:assemble", ":myapp:assemble")
      .withProjectDir(testProjectDir.root)
      .withPluginClasspath()
      .build()

    val aarFile = File(testProjectDir.root, "mylib/build/outputs/aar/mylib-release.aar")
    assertThat(aarFile.exists()).isTrue()

    val zipFile = ZipFile(aarFile)
    val classesJar = zipFile.entries().asSequence().filter { it.name == "classes.jar" }.first()
    return ZipInputStream(zipFile.getInputStream(classesJar)).use {
      val entries = mutableListOf<String>()
      var currentEntry = it.nextEntry
      while (currentEntry != null) {
        if (!currentEntry.isDirectory) {
          entries.add(currentEntry.name)
        }
        currentEntry = it.nextEntry
      }
      entries
    }
  }
}

data class SourceFile(val name: String, val content: String)
