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

/**
 * Annotation used to mark a @Serializable object's property to be automatically populated with the
 * document's ID when the object is created from a Cloud Firestore document.
 *
 * <p>When using a @Serializable object to write to a document, the property annotated by
 * {@code @KDocumentId} is ignored, which allows writing the @Serializable object back to any
 * document, even if it's not the origin of the @Serializable object.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class KDocumentId()
