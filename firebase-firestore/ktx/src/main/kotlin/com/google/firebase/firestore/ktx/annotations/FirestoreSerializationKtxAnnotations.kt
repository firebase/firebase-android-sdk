// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.ktx.annotations

import kotlinx.serialization.SerialInfo

// TODO: This file to be removed after the "Java Kotlin Environment" support PR is merged.
/**
 * Annotation used to mark a @Serializable object's property to be automatically populated with the
 * document's ID when the object is created from a Cloud Firestore document. This annotation is the
 * Kotlin equivalent to Firestore's Java [com.google.firebase.firestore.DocumentId] annotation.
 *
 * <p>When using a @Serializable object to write to a document, the property annotated by
 * KDocumentId is ignored, which allows writing the @Serializable object back to any document, even
 * if it's not the origin of the @Serializable object.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class KDocumentId()

/**
 * Annotation used to mark a timestamp field to be populated with a server timestamp. This
 * annotation is the Kotlin equivalent to Firestore's Java
 * [com.google.firebase.firestore.ServerTimestamp] annotation. If a
 * @Serializable object being written contains null for a @KServerTimestamp-annotated field, it will
 * be replaced with a server-generated timestamp.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class KServerTimestamp()

/**
 * Annotation used to mark a property so that if this property doesn't map to any class fields
 * during serializing process, an exception will be thrown. A @Serializable object annotated with
 * this annotation can be understood as the equivalent to JSON serialization's `Json {
 * ignoreUnknownKeys = false }` configuration. This annotation is the Kotlin equivalent to
 * Firestore's Java [com.google.firebase.firestore.ThrowOnExtraProperties] annotation.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class KThrowOnExtraProperties()

/**
 * Annotation used to mark a property so that if this property doesn't map to any class fields
 * during serializing process, this property will be ignored. A @Serializable object annotated with
 * this annotation can be understood as the equivalent to JSON serialization's `Json {
 * ignoreUnknownKeys = true }` configuration. This is the default behavior even if the @Serializable
 * custom object is defined without this annotation. This annotation is the Kotlin equivalent to
 * Firestore's Java [com.google.firebase.firestore.IgnoreExtraProperties] annotation.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class KIgnoreExtraProperties()
