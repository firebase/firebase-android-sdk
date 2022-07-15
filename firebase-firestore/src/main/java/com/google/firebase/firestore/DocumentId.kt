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
package com.google.firebase.firestore

import kotlinx.serialization.SerialInfo
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Annotation used to mark a POJO property to be automatically populated with the document's ID when
 * the POJO is created from a Cloud Firestore document (for example, via [DocumentSnapshot.toObject]).
 *
 *
 * Any of the following will throw a runtime exception:
 *  * This annotation is applied to a property of a type other than String or [DocumentReference].
 *  * This annotation is applied to a property that is not writable (for example, a Java Bean
 * getter without a backing field).
 *  * This annotation is applied to a property with a name that conflicts with a read document
 * field. For example, if a POJO has a field `firstName` annotated by `@[DocumentId]`, and
 * there is a property from the document named `firstName` as well, an exception is thrown
 * when you try to read the document into the POJO via [DocumentSnapshot.toObject] or [DocumentReference.get].
 *
 * When using a POJO to write to a document (via [DocumentReference.set] or @[WriteBatch.set]), the property annotated by `@[DocumentId]` is
 * ignored, which allows writing the POJO back to any document, even if it's not the origin of the
 * POJO.
 */
@SerialInfo
@Retention(RetentionPolicy.RUNTIME)
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD
)
annotation class DocumentId
