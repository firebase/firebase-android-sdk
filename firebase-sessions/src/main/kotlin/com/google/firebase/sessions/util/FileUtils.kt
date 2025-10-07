package com.google.firebase.sessions.util

import java.io.File

internal fun File.validateParentOrThrow(): File = parentFile?.let {
    if (!it.isDirectory) {
        throw IllegalStateException("Expected ${it.path} to be a directory, but found a file")
    }
    this
} ?: this