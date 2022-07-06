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

package com.google.firebase.firestore.testutil

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.ktx.BuildConfig
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.firestore.ktx.serialization.decodeFromMap
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.mutation.FieldMask
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Whether the integration tests should run against a local Firestore emulator or the Production
 * environment
 */
private const val CONNECT_TO_EMULATOR = BuildConfig.USE_EMULATOR_FOR_TESTS

/** Default amount of time to wait for a given operation to complete, used by waitFor() helper. */
private const val OPERATION_WAIT_TIMEOUT_MS = 30000L

private const val AUTO_ID_LENGTH = 20

private val AUTO_ID_ALPHABET = ('A'..'Z') + ('a'..'z') + ('0'..'9')

private val settingsWithEmulator = firestoreSettings {}

private val settingsWithoutEmulator = firestoreSettings { setHost("firestore.googleapis.com") }

/**
 * Initializes a [FirebaseFirestore] instance that used for integration test.
 *
 * <p>Note: the Android Emulator treats "10.0.2.2" as its host machine.
 */
val testFirestore: FirebaseFirestore by lazy {
    Firebase.firestore.apply {
        firestoreSettings =
            if (CONNECT_TO_EMULATOR) {
                useEmulator("10.0.2.2", 8080)
                settingsWithEmulator
            } else {
                settingsWithoutEmulator
            }
    }
}

fun <T> waitFor(task: Task<T>, timeoutMS: Long): T {
    return Tasks.await(task, timeoutMS, TimeUnit.MILLISECONDS)
}

fun <T> waitFor(task: Task<T>): T {
    return waitFor(task, OPERATION_WAIT_TIMEOUT_MS)
}

/** Returns a unique id for each Firestore collection used for integration test. */
private fun autoId(): String {
    return (1..AUTO_ID_LENGTH).map { AUTO_ID_ALPHABET.random() }.joinToString("")
}

/**
 * Returns a unique [CollectionReference] identified by the combination of collection name and
 * [autoId] for integration test.
 */
fun testCollection(name: String): CollectionReference {
    return testFirestore.collection("${name}_${autoId()}")
}

/** Returns a [DocumentReference] identified by document name for integration test. */
fun testDocument(name: String): DocumentReference {
    return testCollection("test-collection").document(name)
}

/** Returns a [DocumentReference] for integration test. */
fun testDocument(): DocumentReference {
    return testCollection("test-collection").document()
}

/**
 * Overwrites the document referred to by this [DocumentReference]. If the document does not yet
 * exist, it will be created. If a document already exists, it will be overwritten.
 *
 * @param data The data to write to the document (the data must be a @Serializable Kotlin object).
 * @return A Task that will be resolved when the write finishes.
 */
inline fun <reified T> DocumentReference.setData(data: T): Task<Void> {
    val encodedMap = encodeToMap<T>(data)
    return this.set(encodedMap)
}

/**
 * Returns the contents of the document converted to a Serializable Custom Kotlin Object or null if
 * the document doesn't exist. This method always use the Kotlin Serialization plugin method,and
 * this is a helper function for integration test purpose (to compare Kotlin decode method is the
 * same as Java POJO method).
 *
 * @return The contents of the document in an object of type T or null if the document doesn't
 * exist.
 */
inline fun <reified T> DocumentSnapshot.getData(
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior =
        DocumentSnapshot.ServerTimestampBehavior.NONE
): T? {
    val mapToBeDecoded = this.getData(serverTimestampBehavior)
    return mapToBeDecoded?.let { decodeFromMap<T>(it, reference) }
}

/**
 * Overwrites the document referred to by this [DocumentReference]. If the document does not yet
 * exist, it will be created. If a document already exists, it will be overwritten. If you pass
 * [SetOptions], the provided data can be merged into an existing document.
 *
 * <p> This method always use the reflection based Java pojo method, and this is a helper function
 * for integration test purpose (to compare Kotlin encode method is the same as Java POJO method).
 *
 * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
 * document contents).
 * @param options An object to configure the set behavior, default is [SetOptions.OVERWRITE]
 * @return A Task that will be resolved when the write finishes.
 */
inline fun <reified T> DocumentReference.setPojoData(
    data: T,
    options: SetOptions? = OVERWRITE_SET_OPTIONS
): Task<Void> {
    return this::class
        .declaredMemberFunctions
        ?.firstOrNull { it.name == "setParsedData" }
        ?.apply { isAccessible = true }
        ?.call(this, data, options) as Task<Void>
}

val OVERWRITE_SET_OPTIONS: SetOptions?
    get() = testSetOptions(false, null)

/**
 * Returns a [SetOptions] from its constructor, this is a helper function for integration test purpose.
 */
private fun testSetOptions(merge: Boolean, field: FieldMask?): SetOptions? =
    SetOptions::class
        .constructors
        .firstOrNull { it.name == "<init>" }
        ?.apply { isAccessible = true }
        ?.call(merge, field)

/**
 * Returns the contents of the document converted to a Custom POJO Object or null if the document
 * doesn't exist. This method always use the reflection based Java pojo method, and this is a helper
 * function for integration test purpose (to compare Kotlin decode method is the same as Java POJO
 * method).
 *
 * @return The contents of the document in an object of type T or null if the document doesn't
 * exist.
 */
inline fun <reified T> DocumentSnapshot.getPojoData(
    serverTimestampBehavior: DocumentSnapshot.ServerTimestampBehavior =
        DocumentSnapshot.ServerTimestampBehavior.NONE
): T? {
    val data = getData(serverTimestampBehavior)
    return CustomClassMapper.convertToCustomClass(data, T::class.java, reference)
}

/**
 * Writes to the document referred to by the provided DocumentReference. If the document does not
 * yet exist, it will be created. The provided data will always override the existing document
 *
 * <p> This method always use the reflection based Java pojo method, and this is a helper function
 * for integration test purpose (to compare Kotlin transaction.set method is the same as Java POJO method)
 *
 * @param documentRef The [DocumentReference] to overwrite.
 * @param data The data to write to the document (e.g. a Map or a POJO containing the desired
 *     document contents).
 * @return This [Transaction] instance. Used for chaining method calls.
 */
inline fun <reified T> Transaction.set(
    documentRef: DocumentReference,
    data: T
): Transaction {
    val firestore =
        Transaction::class
            .memberProperties
            .firstOrNull { it.name == "firestore" }
            ?.apply { isAccessible = true }
            ?.get(this) as FirebaseFirestore
    val transaction =
        Transaction::class
            .memberProperties
            .firstOrNull { it.name == "transaction" }
            ?.apply { isAccessible = true }
            ?.get(this) as com.google.firebase.firestore.core.Transaction
    val firestoreUserDataReader =
        FirebaseFirestore::class
            .memberProperties
            .firstOrNull { it.name == "userDataReader" }
            ?.apply { isAccessible = true }
            ?.get(firestore) as UserDataReader
    val documentRefKey =
        DocumentReference::class
            .memberProperties
            .firstOrNull { it.name == "key" }
            ?.apply { isAccessible = true }
            ?.get(documentRef) as DocumentKey

    val parsed = firestoreUserDataReader.parseSetData(data)
    transaction[documentRefKey] = parsed
    return this
}
