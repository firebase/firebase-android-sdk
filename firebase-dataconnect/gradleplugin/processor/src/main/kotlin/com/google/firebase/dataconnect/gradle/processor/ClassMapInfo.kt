package com.google.firebase.dataconnect.gradle.processor

data class ClassMapInfo(
    val packageName: String,
    val directory: String,
    val fileName: String,
    val classParts: List<String>,
) {
    val className: String = classParts.joinToString(".")
}
