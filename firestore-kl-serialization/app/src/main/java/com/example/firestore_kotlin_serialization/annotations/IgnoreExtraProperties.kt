package com.example.firestore_kotlin_serialization.annotations

import kotlinx.serialization.SerialInfo

@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class IgnoreExtraProperties()
