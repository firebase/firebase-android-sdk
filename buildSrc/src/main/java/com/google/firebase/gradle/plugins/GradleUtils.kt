package com.google.firebase.gradle.plugins

import java.io.File
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy

fun Copy.fromDirectory(directory: Provider<File>) =
    from(directory) {
        into(directory.map { it.name })
    }
