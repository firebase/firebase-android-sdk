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
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.MutableData
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/** Returns the [FirebaseDatabase] instance of the default [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.database` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.database",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.database"]
  )
)
val Firebase.database: FirebaseDatabase
  get() = FirebaseDatabase.getInstance()

/** Returns the [FirebaseDatabase] instance for the specified [url]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.database(url)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.database(url)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.database"]
  )
)
fun Firebase.database(url: String): FirebaseDatabase = FirebaseDatabase.getInstance(url)

/** Returns the [FirebaseDatabase] instance of the given [FirebaseApp]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.database(app)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.database(app)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.database"]
  )
)
fun Firebase.database(app: FirebaseApp): FirebaseDatabase = FirebaseDatabase.getInstance(app)

/** Returns the [FirebaseDatabase] instance of the given [FirebaseApp] and [url]. */
@Deprecated(
  "Use `com.google.firebase.Firebase.database(app, url)` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "com.google.firebase.Firebase.database(app, url)",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.database"]
  )
)
fun Firebase.database(app: FirebaseApp, url: String): FirebaseDatabase =
  FirebaseDatabase.getInstance(app, url)

/**
 * Returns the content of the DataSnapshot converted to a POJO.
 *
 * Supports generics like List<> or Map<>. Use @JvmSuppressWildcards to force the compiler to use
 * the type `T`, and not `? extends T`.
 */
@Deprecated(
  "Use `com.google.firebase.database.DataSnapshot.getValue` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "DataSnapshot.getValue",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.DataSnapshot.getValue"]
  )
)
inline fun <reified T> DataSnapshot.getValue(): T? {
  return getValue(object : GenericTypeIndicator<T>() {})
}

/**
 * Returns the content of the MutableData converted to a POJO.
 *
 * Supports generics like List<> or Map<>. Use @JvmSuppressWildcards to force the compiler to use
 * the type `T`, and not `? extends T`.
 */
@Deprecated(
  "Use `com.google.firebase.database.MutableData.getValue` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "MutableData.getValue",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.MutableData.getValue"]
  )
)
inline fun <reified T> MutableData.getValue(): T? {
  return getValue(object : GenericTypeIndicator<T>() {})
}

/**
 * Starts listening to this query and emits its values via a [Flow].
 *
 * - When the returned flow starts being collected, a [ValueEventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 */
@Deprecated(
  "Use `com.google.firebase.database.Query.snapshots` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "Query.snapshots",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.Query.snapshots"]
  )
)
val Query.snapshots
  get() =
    callbackFlow<DataSnapshot> {
      val listener =
        addValueEventListener(
          object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
              repo.scheduleNow { trySendBlocking(snapshot) }
            }

            override fun onCancelled(error: DatabaseError) {
              cancel(message = "Error getting Query snapshot", cause = error.toException())
            }
          }
        )
      awaitClose { removeEventListener(listener) }
    }

/**
 * Starts listening to this query's child events and emits its values via a [Flow].
 *
 * - When the returned flow starts being collected, a [ChildEventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 */
@Deprecated(
  "Use `com.google.firebase.database.Query.childEvents` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "Query.childEvents",
    imports = ["com.google.firebase.Firebase", "com.google.firebase.database.Query.childEvents"]
  )
)
val Query.childEvents
  get() =
    callbackFlow<ChildEvent> {
      val listener =
        addChildEventListener(
          object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
              repo.scheduleNow { trySendBlocking(ChildEvent.Added(snapshot, previousChildName)) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
              repo.scheduleNow { trySendBlocking(ChildEvent.Changed(snapshot, previousChildName)) }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
              repo.scheduleNow { trySendBlocking(ChildEvent.Removed(snapshot)) }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
              repo.scheduleNow { trySendBlocking(ChildEvent.Moved(snapshot, previousChildName)) }
            }

            override fun onCancelled(error: DatabaseError) {
              cancel(message = "Error getting Query childEvent", cause = error.toException())
            }
          }
        )
      awaitClose { removeEventListener(listener) }
    }

/**
 * Starts listening to this query and emits its values converted to a POJO via a [Flow].
 *
 * - When the returned flow starts being collected, a [ValueEventListener] will be attached.
 * - When the flow completes, the listener will be removed.
 */
@Deprecated(
  "Use `com.google.firebase.database.FirebaseDatabaseKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "FirebaseDatabaseKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.database.FirebaseDatabaseKtxRegistrar"]
  )
)
inline fun <reified T : Any> Query.values(): Flow<T?> {
  return snapshots.map { it.getValue(T::class.java) }
}

/** @suppress */
@Deprecated(
  "Use `com.google.firebase.database.FirebaseDatabaseKtxRegistrar` from the main module instead. The Kotlin extensions (KTX) APIs have been added to their respective main modules, and the Kotlin extension (KTX) APIs in `com.google.firebase.firebase-database-ktx` are now deprecated. As early as April 2024, we'll no longer release KTX modules. For details, see the <a href=\"https://firebase.google.com/docs/android/ktx-apis-to-main-modules\">FAQ about this initiative.</a>",
  ReplaceWith(
    expression = "FirebaseDatabaseKtxRegistrar",
    imports =
      ["com.google.firebase.Firebase", "com.google.firebase.database.FirebaseDatabaseKtxRegistrar"]
  )
)
@Keep
class FirebaseDatabaseKtxRegistrar : ComponentRegistrar {
  override fun getComponents(): List<Component<*>> = listOf()
}
