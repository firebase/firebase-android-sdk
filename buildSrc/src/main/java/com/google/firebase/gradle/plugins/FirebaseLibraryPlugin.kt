// Copyright 2023 Google LLC
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

import com.android.build.gradle.LibraryExtension
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatExtension
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import java.io.File
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class FirebaseLibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.apply(ImmutableMap.of("plugin", "com.android.library"))
    project.apply(ImmutableMap.of("plugin", LicenseResolverPlugin::class.java))
    project.apply(ImmutableMap.of("plugin", "com.github.sherter.google-java-format"))
    project.extensions.getByType(GoogleJavaFormatExtension::class.java).toolVersion = "1.10.0"
    val firebaseLibrary =
      project.extensions.create(
        "firebaseLibrary",
        FirebaseLibraryExtension::class.java,
        project,
        LibraryType.ANDROID
      )
    val android = project.extensions.getByType<LibraryExtension>()
    android.compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    }

    // In the case of and android library signing config only affects instrumentation test APK.
    // We need it signed with default debug credentials in order for FTL to accept the APK.
    android.buildTypes { getByName("release").signingConfig = getByName("debug").signingConfig }

    android.defaultConfig {
      buildConfigField("String", "VERSION_NAME", "\"" + project.version + "\"")
    }

    // see https://github.com/robolectric/robolectric/issues/5456
    android.testOptions {
      unitTests.all {
        it.systemProperty("robolectric.dependency.repo.id", "central")
        it.systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
        it.systemProperty("javax.net.ssl.trustStoreType", "JKS")
      }
    }

    setupApiInformationAnalysis(project, android)
    android.testServer(FirebaseTestServer(project, firebaseLibrary.testLab, android))
    setupStaticAnalysis(project, firebaseLibrary)
    configurePublishing(project, firebaseLibrary, android)

    // reduce the likelihood of kotlin module files colliding.
    project.tasks.withType(KotlinCompile::class.java) {
      kotlinOptions.freeCompilerArgs = ImmutableList.of("-module-name", kotlinModuleName(project))
    }

    project.pluginManager.apply(DackkaPlugin::class.java)
    project.pluginManager.apply(GitSubmodulePlugin::class.java)
    project.tasks.getByName("preBuild").dependsOn("updateGitSubmodules")
  }

  private fun setupApiInformationAnalysis(project: Project, android: LibraryExtension) {
    val srcDirs = android.sourceSets.getByName("main").java.srcDirs

    val mainSourceSets = android.sourceSets.getByName("main")
    val getKotlinDirectories = mainSourceSets::class.java.getDeclaredMethod("getKotlinDirectories")
    val kotlinSrcDirs = getKotlinDirectories.invoke(mainSourceSets)

    val apiInfo = getApiInfo(project, kotlinSrcDirs as Set<File>)
    val generateApiTxt = getGenerateApiTxt(project, kotlinSrcDirs as Set<File>)
    val docStubs = getDocStubs(project, srcDirs)

    project.tasks.getByName("check").dependsOn(docStubs)
    android.libraryVariants.all {
      if (name == "release") {
        val jars =
          compileConfiguration.incoming
            .artifactView {
              attributes {
                attribute(Attribute.of("artifactType", String::class.java), "android-classes")
              }
            }
            .artifacts
            .artifactFiles
        apiInfo.configure { classPath = jars }
        generateApiTxt.configure { classPath = jars }
        docStubs.configure { classPath = jars }
      }
    }
  }

  private fun configurePublishing(
    project: Project,
    firebaseLibrary: FirebaseLibraryExtension,
    android: LibraryExtension
  ) {
    android.publishing { singleVariant("release") { withSourcesJar() } }
    project.tasks.withType(
      GenerateModuleMetadata::class.java,
    ) {
      isEnabled = false
    }

    project.afterEvaluate {
      project.apply(ImmutableMap.of("plugin", "maven-publish"))
      val publishing = project.extensions.getByType(PublishingExtension::class.java)
      publishing.repositories {
        maven {
          val s = project.rootProject.buildDir.toString() + "/m2repository"
          val file = File(s)
          url = file.toURI()
          name = "BuildDir"
        }
      }

      publishing.publications {
        create("mavenAar", MavenPublication::class.java) {
          from(project.components.findByName(firebaseLibrary.type.componentName))
          artifactId = firebaseLibrary.artifactId.get()
          groupId = firebaseLibrary.groupId.get()
          firebaseLibrary.applyPomCustomization(pom)
        }
      }
    }
  }
}
