package com.google.firebase.gradle.plugins

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

class FireEscapeArtifactPlugin : Plugin<Project> {
  var project: Project? = null

  override fun apply(target: Project) {
    project = target

    project!!.afterEvaluate {
      val firebaseLibrary = extensions.findByType(FirebaseLibraryExtension::class.java)
      val fireEscapeTask =
        tasks.create("mavenAarFireEscapeArtifact", Zip::class.java) { classifier = "fireescape" }
      plugins.withType(MavenPublishPlugin::class.java) {
        extensions.configure(PublishingExtension::class.java) {
          publications.withType(MavenPublication::class.java) {
            if ("mavenAar" == name) {
              configurePublication(this, fireEscapeTask, firebaseLibrary!!.type)
            }
          }
        }
      }
    }
  }

  private fun configurePublication(
    publication: MavenPublication,
    artifactTask: Zip,
    libraryType: LibraryType
  ) {
    publication.artifact(artifactTask)
    artifactTask.from(apiTxtFileTask())
    if (libraryType == LibraryType.ANDROID) {
      artifactTask.from(proguardMappingFileTask())
    }
    publication.artifact(javadocTask())
  }

  private fun proguardMappingFileTask(): Task? {
    return project?.tasks?.create("fireEscapeProguardMapping") {
      val task = this
      project.tasks.all {
        if (name == "assembleRelease") {
          task.dependsOn(this)
        }
      }
      outputs.file(File(project.buildDir, "outputs/mapping/release/mapping.txt"))
    }
  }

  private fun apiTxtFileTask(): Task? {
    return project?.tasks?.create("fireEscapeApiText") {
      dependsOn(JAVADOC_TASK_NAME)
      outputs.file(File(project.buildDir, "tmp/javadoc/api.txt"))
    }
  }

  private fun javadocTask(): Task? {
    return project?.tasks?.create("fireescapeJavadocJar", Jar::class.java) {
      dependsOn(JAVADOC_TASK_NAME)
      from(File(project.buildDir, "/docs/javadoc/reference"))
      include("**/*")
      archiveName = "fireescape-javadoc.jar"
      classifier = "javadoc"
    }
  }
}
