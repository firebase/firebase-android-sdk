package com.google.firebase.dataconnect.codegen.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class YourPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.tasks.register("myCustomTask") {
      doLast { println("Hello from your custom Gradle plugin!") }
    }
  }
}
