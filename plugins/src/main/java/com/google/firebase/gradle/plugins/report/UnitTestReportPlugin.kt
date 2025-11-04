package com.google.firebase.gradle.plugins.report

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class UnitTestReportPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.tasks.register<UnitTestReportTask>("generateTestReport") {
      outputFile.set(project.file("test-report.md"))
      commitCount.set(8 as Integer)
      apiToken.set(System.getenv("GH_TOKEN"))
    }
  }
}
