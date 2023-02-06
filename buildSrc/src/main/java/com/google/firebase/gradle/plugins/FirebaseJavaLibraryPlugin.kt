package com.google.firebase.gradle.plugins

import com.github.sherter.googlejavaformatgradleplugin.GoogleJavaFormatExtension
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import java.io.File
import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class FirebaseJavaLibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.apply(ImmutableMap.of("plugin", "java-library"))
    project.apply(ImmutableMap.of("plugin", "com.github.sherter.google-java-format"))
    project.extensions.getByType(GoogleJavaFormatExtension::class.java).toolVersion = "1.10.0"
    val firebaseLibrary =
      project.extensions.create(
        "firebaseLibrary",
        FirebaseLibraryExtension::class.java,
        project,
        LibraryType.JAVA
      )

    // reduce the likelihood of kotlin module files colliding.
    project.tasks.withType(KotlinCompile::class.java) {
      kotlinOptions.freeCompilerArgs = ImmutableList.of("-module-name", kotlinModuleName(project))
    }
    setupStaticAnalysis(project, firebaseLibrary)
    setupApiInformationAnalysis(project)
    configurePublishing(project, firebaseLibrary)
    project.tasks.register("kotlindoc")
  }

  private fun setupApiInformationAnalysis(project: Project) {
    val mainSourceSet =
      project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets.getByName("main")
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
    project.afterEvaluate {
      val classpath =
        configurations
          .getByName("runtimeClasspath")
          .incoming
          .artifactView {
            attributes { attribute(Attribute.of("artifactType", String::class.java), "jar") }
          }
          .artifacts
          .artifactFiles
      apiInfo.configure { classPath = classpath }
      generateApiTxt.configure { classPath = classpath }
      docStubs.configure { classPath = classpath }
    }
  }
  private fun configurePublishing(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
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
        project.afterEvaluate {
          artifactId = firebaseLibrary.artifactId.get()
          groupId = firebaseLibrary.groupId.get()
          firebaseLibrary.applyPomCustomization(pom)
        }
      }
    }
  }
}
