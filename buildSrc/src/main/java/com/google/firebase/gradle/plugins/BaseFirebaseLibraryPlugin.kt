package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.plugins.ci.Coverage
import java.io.File
import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

abstract class BaseFirebaseLibraryPlugin : Plugin<Project> {

  protected fun kotlinModuleName(project: Project): String {
    val fullyQualifiedProjectPath = project.path.replace(":".toRegex(), "-")
    return project.rootProject.name + fullyQualifiedProjectPath
  }

  protected fun setupStaticAnalysis(project: Project, library: FirebaseLibraryExtension) {
    project.afterEvaluate {
      configurations.all {
        if ("lintChecks" == name) {
          for (checkProject in library.staticAnalysis.androidLintCheckProjects) {
            project.dependencies.add("lintChecks", project.project(checkProject!!))
          }
        }
      }
    }
    project.tasks.register("firebaseLint") { dependsOn("lint") }
    Coverage.apply(library)
  }

  protected fun getApiInfo(project: Project, srcDirs: Set<File>): TaskProvider<ApiInformationTask> {
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
      project.tasks.register<ApiInformationTask>("apiInformation") {
        sources.value(project.provider { srcDirs })
        apiTxtFile.set(apiTxt)
        baselineFile.set(project.file("baseline.txt"))
        this.outputFile.set(outputFile)
        this.outputApiFile.set(outputApiFile)
        updateBaseline.set(project.hasProperty("updateBaseline"))
      }
    return apiInfo
  }

  protected fun getGenerateApiTxt(project: Project, srcDirs: Set<File>) =
    project.tasks.register<GenerateApiTxtTask>("generateApiTxtFile") {
      sources.value(project.provider { srcDirs })
      apiTxtFile.set(project.file("api.txt"))
      baselineFile.set(project.file("baseline.txt"))
      updateBaseline.set(project.hasProperty("updateBaseline"))
    }

  protected fun getDocStubs(project: Project, srcDirs: Set<File>) =
    project.tasks.register<GenerateStubsTask>("docStubs") {
      sources.value(project.provider { srcDirs })
    }

  protected fun configurePublishing(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
    project.afterEvaluate {
      project.apply<MavenPublishPlugin>()
      project.extensions.configure<PublishingExtension> {
        repositories.maven {
          val s = project.rootProject.buildDir.toString() + "/m2repository"
          url = File(s).toURI()
          name = "BuildDir"
        }
        publications.create<MavenPublication>("mavenAar") {
          from(project.components.findByName(firebaseLibrary.type.componentName))
          artifactId = firebaseLibrary.artifactId.get()
          groupId = firebaseLibrary.groupId.get()
          firebaseLibrary.applyPomCustomization(pom)
        }
      }
    }
  }
}
