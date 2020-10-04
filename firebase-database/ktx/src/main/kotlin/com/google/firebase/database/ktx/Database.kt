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
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.MutableData
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.platforminfo.LibraryVersionComponent

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
 * Add a listener for changes in the data at this location. Each time time the data changes, your
 * listener will be called with an non-null snapshot of the data.
 */
inline fun DatabaseReference.addValueEventListener(
        crossinline onValue: (snapshot: DataSnapshot?, error: DatabaseError?) -> Unit
): ValueEventListener {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = onValue(snapshot, null)
        override fun onCancelled(error: DatabaseError) = onValue(null, error)
    }
    addValueEventListener(listener)
    return listener
}

/**
 * Add a listener for a single change in the data at this location. This listener will be
 * triggered once with the value of the data at the location.
 */
inline fun DatabaseReference.addListenerForSingleValueEvent(
        crossinline onceValue: (snapshot: DataSnapshot?, error: DatabaseError?) -> Unit
): ValueEventListener {
    val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) = onceValue(snapshot, null)
        override fun onCancelled(error: DatabaseError) = onceValue(null, error)
    }
    addListenerForSingleValueEvent(listener)
    return listener
}

internal const val LIBRARY_NAME: String = "fire-db-ktx"

/** @suppress */
@Keep
class FirebaseDatabaseKtxRegistrar : ComponentRegistrar {
    override fun getComponents(): List<Component<*>> =
            listOf(LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME))
}
