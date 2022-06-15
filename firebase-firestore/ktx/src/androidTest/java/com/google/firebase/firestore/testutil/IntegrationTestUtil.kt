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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ktx.BuildConfig
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

/**
 * Whether the integration tests should run against a local Firestore emulator or the Production
 * environment
 */
private const val CONNECT_TO_EMULATOR = BuildConfig.USE_EMULATOR_FOR_TESTS

/** Default amount of time to wait for a given operation to complete, used by waitFor() helper. */
private const val OPERATION_WAIT_TIMEOUT_MS = 30000L

private const val AUTO_ID_LENGTH = 20

private val AUTO_ID_ALPHABET = ('A'..'Z') + ('a'..'z') + ('0'..'9')

private var settingWithEmulator =
    FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build()

private var settingWithoutEmulator =
    FirebaseFirestoreSettings.Builder()
        .setHost("firestore.googleapis.com")
        .setPersistenceEnabled(true)
        .build()

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
                settingWithEmulator
            } else {
                settingWithoutEmulator
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
