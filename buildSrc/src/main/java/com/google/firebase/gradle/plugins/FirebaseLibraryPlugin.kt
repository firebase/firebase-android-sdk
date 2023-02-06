package com.google.firebase.gradle.plugins

import com.android.build.gradle.LibraryExtension
import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatExtension
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Paths

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
    val mainSourceSet = android.sourceSets.getByName("main")
    val outputFile =
      project.rootProject.file(
        Paths.get(
          project.rootProject.buildDir.path,
          "apiinfo",
          project.path.substring(1).replace(":", "_")
        )
      )
    val outputApiFile = File(outputFile.absolutePath + "_api.txt")
    val apiTxt =
      if (project.file("api.txt").exists()) project.file("api.txt")
      else project.file(project.rootDir.toString() + "/empty-api.txt")
    val apiInfo =
      project.tasks.register("apiInformation", ApiInformationTask::class.java) {
        sources.value(project.provider { mainSourceSet.java.srcDirs })
        apiTxtFile.set(apiTxt)
        baselineFile.set(project.file("baseline.txt"))
        this.outputFile.set(outputFile)
        this.outputApiFile.set(outputApiFile)
        updateBaseline.set(project.hasProperty("updateBaseline"))
      }

    val generateApiTxt =
      project.tasks.register("generateApiTxtFile", GenerateApiTxtTask::class.java) {
        sources.value(project.provider { mainSourceSet.java.srcDirs })
        apiTxtFile.set(project.file("api.txt"))
        baselineFile.set(project.file("baseline.txt"))
        updateBaseline.set(project.hasProperty("updateBaseline"))
      }

    val docStubs =
      project.tasks.register("docStubs", GenerateStubsTask::class.java) {
        sources.value(project.provider { mainSourceSet.java.srcDirs })
      }

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
