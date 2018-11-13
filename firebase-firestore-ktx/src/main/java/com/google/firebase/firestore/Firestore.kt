// Copyright 2018 Google LLC
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

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp

/** Returns the [FirebaseFirestore] instance of the default [FirebaseApp]. */
inline val Firebase.firestore: FirebaseFirestore
    get() = FirebaseFirestore.getInstance()

/** Returns the [FirebaseFirestore] instance of a given [FirebaseApp]. */
inline val FirebaseApp.firestore: FirebaseFirestore
    get() = FirebaseFirestore.getInstance(this)

/** Returns the contents of the document converted to a POJO or null if the document doesn't exist. */
inline fun <reified T> DocumentSnapshot.toObject(): T? = toObject(T::class.java)

/** Returns the contents of the document converted to a POJO or null if the document doesn't exist. */
inline fun <reified T> DocumentSnapshot.toObject(severTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior): T? =
    toObject(T::class.java, severTimestampBehavior)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 */
inline fun <reified T> DocumentSnapshot.getDocument(field: String): T? = get(field, T::class.java)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 */
inline fun <reified T> DocumentSnapshot.getDocument(
    field: String,
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): T? = get(field, T::class.java, serverTimestampBehavior)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 */
inline fun <reified T> DocumentSnapshot.getDocument(field: FieldPath): T? = get(field, T::class.java)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 */
inline fun <reified T> DocumentSnapshot.getDocument(
    field: FieldPath,
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): T? = get(field, T::class.java, serverTimestampBehavior)

/** Returns the contents of the document converted to a POJO. */
inline fun <reified T> QueryDocumentSnapshot.toObject(): T = toObject(T::class.java)

/** Returns the contents of the document converted to a POJO. */
inline fun <reified T> QueryDocumentSnapshot.toObject(severTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior): T =
    toObject(T::class.java, severTimestampBehavior)

/**
 * Returns the contents of the documents in the QuerySnapshot, converted to the provided class, as
 * a list.
 */
inline fun <reified T> QuerySnapshot.toObjects(): List<T> = toObjects(T::class.java)

/**
 * Returns the contents of the documents in the QuerySnapshot, converted to the provided class, as
 * a list.
 */
inline fun <reified T> QuerySnapshot.toObjects(severTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior): List<T> =
    toObjects(T::class.java, severTimestampBehavior)
