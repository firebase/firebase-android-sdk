package com.google.firebase.ai.annotation

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class SchemaDetails(val description: String, val title: String)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
public annotation class NumSchemaDetails(val minimum: Double, val maximum: Double)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
public annotation class ListSchemaDetails(val minItems: Int, val maxItems: Int, val clazz: KClass<*>)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
public annotation class StringSchemaDetails(val format: String)