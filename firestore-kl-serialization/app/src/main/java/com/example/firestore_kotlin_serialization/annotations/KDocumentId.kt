package com.example.firestore_kotlin_serialization.annotations

import kotlinx.serialization.SerialInfo

@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY_GETTER,
//    AnnotationTarget.FIELD
)
annotation class KDocumentId
