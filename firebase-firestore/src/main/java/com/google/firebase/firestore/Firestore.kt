/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore

import androidx.annotation.Keep
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.firestore.*
import com.google.firebase.firestore.util.Executors.BACKGROUND_EXECUTOR
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/** Returns the [FirebaseFirestore] instance of the default [FirebaseApp]. */
val Firebase.firestore: FirebaseFirestore
  get() = FirebaseFirestore.getInstance()

/** Returns the [FirebaseFirestore] instance of a given [FirebaseApp]. */
fun Firebase.firestore(app: FirebaseApp): FirebaseFirestore = FirebaseFirestore.getInstance(app)

/** Returns the [FirebaseFirestore] instance of a given [FirebaseApp] and database name. */
fun Firebase.firestore(app: FirebaseApp, database: String): FirebaseFirestore =
  FirebaseFirestore.getInstance(app, database)

/**
 * Returns the [FirebaseFirestore] instance of the default [FirebaseApp], given the database name.
 */
fun Firebase.firestore(database: String): FirebaseFirestore =
  FirebaseFirestore.getInstance(database)

/**
 * Returns the contents of the document converted to a POJO or null if the document doesn't exist.
 *
 * @param T The type of the object to create.
 * @return The contents of the document in an object of type T or null if the document doesn't
 * ```
 *     exist.
 * ```
 */
inline fun <reified T> DocumentSnapshot.toObject(): T? = toObject(T::class.java)

/**
 * Returns the contents of the document converted to a POJO or null if the document doesn't exist.
 *
 * @param T The type of the object to create.
 * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
 * ```
 *     been set to their final value.
 * @return
 * ```
 * The contents of the document in an object of type T or null if the document doesn't
 * ```
 *     exist.
 * ```
 */
inline fun <reified T> DocumentSnapshot.toObject(
  serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): T? = toObject(T::class.java, serverTimestampBehavior)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 *
 * @param field The path to the field.
 * @param T The type to convert the field value to.
 * @return The value at the given field or null.
 */
inline fun <reified T> DocumentSnapshot.getField(field: String): T? = get(field, T::class.java)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 *
 * @param field The path to the field.
 * @param T The type to convert the field value to.
 * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
 * ```
 *     been set to their final value.
 * @return
 * ```
 * The value at the given field or null.
 */
inline fun <reified T> DocumentSnapshot.getField(
  field: String,
  serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): T? = get(field, T::class.java, serverTimestampBehavior)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 *
 * @param fieldPath The path to the field.
 * @param T The type to convert the field value to.
 * @return The value at the given field or null.
 */
inline fun <reified T> DocumentSnapshot.getField(fieldPath: FieldPath): T? =
  get(fieldPath, T::class.java)

/**
 * Returns the value at the field, converted to a POJO, or null if the field or document doesn't
 * exist.
 *
 * @param fieldPath The path to the field.
 * @param T The type to convert the field value to.
 * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
 * ```
 *     been set to their final value.
 * @return
 * ```
 * The value at the given field or null.
 */
inline fun <reified T> DocumentSnapshot.getField(
  fieldPath: FieldPath,
  serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): T? = get(fieldPath, T::class.java, serverTimestampBehavior)

/**
 * Returns the contents of the document converted to a POJO.
 *
 * @param T The type of the object to create.
 * @return The contents of the document in an object of type T.
 */
inline fun <reified T : Any> QueryDocumentSnapshot.toObject(): T = toObject(T::class.java)

/**
 * Returns the contents of the document converted to a POJO.
 *
 * @param T The type of the object to create.
 * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
 * ```
 *     been set to their final value.
 * @return
 * ```
 * The contents of the document in an object of type T.
 */
inline fun <reified T : Any> QueryDocumentSnapshot.toObject(
  serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): T = toObject(T::class.java, serverTimestampBehavior)

/**
 * Returns the contents of the documents in the QuerySnapshot, converted to the provided class, as a
 * list.
 *
 * @param T The POJO type used to convert the documents in the list.
 */
inline fun <reified T : Any> QuerySnapshot.toObjects(): List<T> = toObjects(T::class.java)

/**
 * Returns the contents of the documents in the QuerySnapshot, converted to the provided class, as a
 * list.
 *
 * @param T The POJO type used to convert the documents in the list.
 * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
 * ```
 *     been set to their final value.
 * ```
 */
inline fun <reified T : Any> QuerySnapshot.toObjects(
  serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior
): List<T> = toObjects(T::class.java, serverTimestampBehavior)

/** Returns a [FirebaseFirestoreSettings] instance initialized using the [init] function. */
fun firestoreSettings(
  init: FirebaseFirestoreSettings.Builder.() -> Unit
): FirebaseFirestoreSettings {
  val builder = FirebaseFirestoreSettings.Builder()
  builder.init()
  return builder.build()
}

fun memoryCacheSettings(init: MemoryCacheSettings.Builder.() -> Unit): MemoryCacheSettings {
  val builder = MemoryCacheSettings.newBuilder()
  builder.init()
  return builder.build()
}

fun memoryEagerGcSettings(init: MemoryEagerGcSettings.Builder.() -> Unit): MemoryEagerGcSettings {
  val builder = MemoryEagerGcSettings.newBuilder()
  builder.init()
  return builder.build()
}

fun memoryLruGcSettings(init: MemoryLruGcSettings.Builder.() -> Unit): MemoryLruGcSettings {
  val builder = MemoryLruGcSettings.newBuilder()
  builder.init()
  return builder.build()
}

fun persistentCacheSettings(
  init: PersistentCacheSettings.Builder.() -> Unit
): PersistentCacheSettings {
  val builder = PersistentCacheSettings.newBuilder()
  builder.init()
  return builder.build()
}

/** @suppress */
@Keep
class FirebaseFirestoreKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}

/**
 * Starts listening to the document referenced by this `DocumentReference` with the given options
 * and emits its values via a [Flow].
 *
 * - When the returned flow starts being collected, an [EventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 *
 * @param metadataChanges controls metadata-only changes. Default: [MetadataChanges.EXCLUDE]
 */
fun DocumentReference.snapshots(
  metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE
): Flow<DocumentSnapshot> {
  return callbackFlow {
    val registration =
      addSnapshotListener(BACKGROUND_EXECUTOR, metadataChanges) { snapshot, exception ->
        if (exception != null) {
          cancel(message = "Error getting DocumentReference snapshot", cause = exception)
        } else if (snapshot != null) {
          trySendBlocking(snapshot)
        }
      }
    awaitClose { registration.remove() }
  }
}

/**
 * Starts listening to this query with the given options and emits its values via a [Flow].
 *
 * - When the returned flow starts being collected, an [EventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 *
 * @param metadataChanges controls metadata-only changes. Default: [MetadataChanges.EXCLUDE]
 */
fun Query.snapshots(
  metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE
): Flow<QuerySnapshot> {
  return callbackFlow {
    val registration =
      addSnapshotListener(BACKGROUND_EXECUTOR, metadataChanges) { snapshot, exception ->
        if (exception != null) {
          cancel(message = "Error getting Query snapshot", cause = exception)
        } else if (snapshot != null) {
          trySendBlocking(snapshot)
        }
      }
    awaitClose { registration.remove() }
  }
}

/**
 * Starts listening to this query with the given options and emits its values converted to a POJO
 * via a [Flow].
 *
 * - When the returned flow starts being collected, an [EventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 *
 * @param metadataChanges controls metadata-only changes. Default: [MetadataChanges.EXCLUDE]
 * @param T The type of the object to convert to.
 */
inline fun <reified T : Any> Query.dataObjects(
  metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE
): Flow<List<T>> = snapshots(metadataChanges).map { it.toObjects(T::class.java) }

/**
 * Starts listening to the document referenced by this `DocumentReference` with the given options
 * and emits its values converted to a POJO via a [Flow].
 *
 * - When the returned flow starts being collected, an [EventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 *
 * @param metadataChanges controls metadata-only changes. Default: [MetadataChanges.EXCLUDE]
 * @param T The type of the object to convert to.
 */
inline fun <reified T : Any> DocumentReference.dataObjects(
  metadataChanges: MetadataChanges = MetadataChanges.EXCLUDE
): Flow<T?> = snapshots(metadataChanges).map { it.toObject(T::class.java) }
