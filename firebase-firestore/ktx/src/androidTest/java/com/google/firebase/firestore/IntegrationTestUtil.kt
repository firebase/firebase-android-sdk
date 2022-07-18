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

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.ThrowableSubject
import com.google.common.truth.Truth
import com.google.firebase.firestore.ktx.BuildConfig
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.firestoreSettings
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit
import org.junit.Assert

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
 * Note: the Android Emulator treats "10.0.2.2" as its host machine.
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

/**
 * Runs a [DocumentReference] method with temporary absence of any [FirebaseFirestore.mapEncoders].
 *
 * Note: IllegalArgumentException will be thrown if there is no Mapper registered to
 * [FirebaseFirestore] at runtime.
 */
fun DocumentReference.withoutCustomMappers(lambda: DocumentReference.() -> Unit) {
    val currentMapper = firestore.mapEncoders.toSet()
    if (currentMapper.isEmpty())
        throw IllegalArgumentException("No Registered Custom Mapper Obtained at runtime!")
    firestore.mapEncoders.clear()
    lambda()
    firestore.mapEncoders.addAll(currentMapper)
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

inline fun <reified T : Exception> testAssertThrows(
    crossinline runnable: () -> Any?
): ThrowableSubject {
    val exception: T = Assert.assertThrows(T::class.java) { runnable() }
    return Truth.assertThat(exception)
}
