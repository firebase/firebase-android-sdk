// Copyright 2019 Google LLC
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

package com.google.firebase.database.ktx

import androidx.annotation.Keep
import com.google.firebase.FirebaseApp
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Returns the [FirebaseDatabase] instance of the default [FirebaseApp]. */
val Firebase.database: FirebaseDatabase
    get() = FirebaseDatabase.getInstance()

/** Returns the [FirebaseDatabase] instance for the specified [url]. */
fun Firebase.database(url: String): FirebaseDatabase = FirebaseDatabase.getInstance(url)

/** Returns the [FirebaseDatabase] instance of the given [FirebaseApp]. */
fun Firebase.database(app: FirebaseApp): FirebaseDatabase = FirebaseDatabase.getInstance(app)

/** Returns the [FirebaseDatabase] instance of the given [FirebaseApp] and [url]. */
fun Firebase.database(app: FirebaseApp, url: String): FirebaseDatabase =
FirebaseDatabase.getInstance(app, url)

/**
 * Returns the content of the DataSnapshot converted to a POJO.
 *
 * Supports generics like List<> or Map<>. Use @JvmSuppressWildcards to force the compiler to
 * use the type `T`, and not `? extends T`.
 */
inline fun <reified T> DataSnapshot.getValue(): T? {
    return getValue(object : GenericTypeIndicator<T>() {})
}

/**
 * Returns the content of the MutableData converted to a POJO.
 *
 * Supports generics like List<> or Map<>. Use @JvmSuppressWildcards to force the compiler to
 * use the type `T`, and not `? extends T`.
 */
inline fun <reified T> MutableData.getValue(): T? {
    return getValue(object : GenericTypeIndicator<T>() {})
}

/**
 * Run a transaction on the data at this location.
 * Returns the new data at the location.
 *
 * @param fireLocalEvents Defaults to true. If set to false, events will only be fired for the
 *     final result state of the transaction, and not for any intermediate states
 * @param handler An object to handle running the transaction
 */
suspend fun DatabaseReference.runTransaction(
    fireLocalEvents: Boolean = true,
    handler: (MutableData) -> Transaction.Result
) = suspendCancellableCoroutine<DataSnapshot> { continuation ->
    runTransaction(object : Transaction.Handler {
        override fun doTransaction(currentData: MutableData): Transaction.Result {
            return handler(currentData)
        }

        override fun onComplete(
            error: DatabaseError?,
            committed: Boolean,
            currentData: DataSnapshot?
        ) {
            if (error != null) {
                continuation.resumeWithException(error.toException())
            } else {
                if (currentData != null) {
                    continuation.resume(currentData)
                }
            }
        }
    }, fireLocalEvents)
}

internal const val LIBRARY_NAME: String = "fire-db-ktx"

/** @suppress */
@Keep
class FirebaseDatabaseKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
