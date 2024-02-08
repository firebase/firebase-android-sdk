package com.google.firebase.dataconnect.plugin

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodegenPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val androidAppPlugin = project.plugins.findPlugin(AppPlugin::class.java)
    val androidLibPlugin = project.plugins.findPlugin(LibraryPlugin::class.java)
    val androidPlugins = listOf(androidAppPlugin, androidLibPlugin).filterNotNull()
    if (androidPlugins.size == 0) {
      throw CodegenPluginGradleException(
        "The plugin must be applied on an Android application or library project"
      )
    }

    project.tasks.register("myCustomTask") {
      doLast { androidPlugins.forEach { println("Found plugin: $it") } }
    }
  }
}

class CodegenPluginGradleException(message: String) : GradleException(message)
