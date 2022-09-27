package com.google.firebase.gradle.plugins

import java.io.File
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy

fun Copy.fromDirectory(directory: Provider<File>) =
    from(directory) {
        into(directory.map { it.name })
    }

fun Project.fileFromBuildDir(path: String) = file("$buildDir/$path")

fun Project.childFile(provider: Provider<File>, childPath: String) = provider.map {
    file("${it.path}/$childPath")
}
